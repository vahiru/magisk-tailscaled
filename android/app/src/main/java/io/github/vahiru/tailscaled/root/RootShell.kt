package io.github.vahiru.tailscaled.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.IOException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

/** Single-quote a string for /system/bin/sh. */
fun shQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

/**
 * One long-lived `su` shell shared by all short commands. Reusing it avoids a root
 * manager prompt / toast for every status poll. Commands are serialized; each one is
 * framed by a random marker that carries its exit code.
 */
object RootShell {
    private const val EOF = "\u0000EOF"

    private class Session(val process: Process) {
        val queue = LinkedBlockingQueue<String>()
        val writer: BufferedWriter = process.outputStream.bufferedWriter()

        init {
            thread(isDaemon = true, name = "su-reader") {
                try {
                    process.inputStream.bufferedReader().forEachLine { queue.put(it) }
                } catch (_: IOException) {
                }
                queue.put(EOF)
            }
        }

        fun close() {
            runCatching { writer.close() }
            process.destroy()
        }
    }

    private val mutex = Mutex()
    private var session: Session? = null

    private fun open(): Session? = try {
        Session(ProcessBuilder("su").redirectErrorStream(true).start())
    } catch (_: IOException) {
        null
    }

    suspend fun exec(cmd: String, timeoutMs: Long = 20_000): ShellResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val s = session ?: open()?.also { session = it }
                ?: return@withLock ShellResult(-1, "su not found: root is required")
            val marker = "__TSM_${UUID.randomUUID()}__"
            try {
                // stdin of the command is /dev/null so it can never swallow our next command.
                s.writer.write("( $cmd\n) </dev/null 2>&1; __rc=\$?; echo; echo \"$marker \$__rc\"\n")
                s.writer.flush()
            } catch (e: IOException) {
                s.close()
                session = null
                return@withLock ShellResult(-1, "root shell closed: ${e.message}")
            }

            val out = ArrayList<String>()
            val deadline = System.currentTimeMillis() + timeoutMs
            var code: Int
            while (true) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) {
                    s.close()
                    session = null
                    return@withLock ShellResult(-2, (out + "[timed out]").joinToString("\n"))
                }
                val line = s.queue.poll(remaining, TimeUnit.MILLISECONDS) ?: continue
                if (line == EOF) {
                    session = null
                    val msg = out.joinToString("\n").ifBlank { "root access denied" }
                    return@withLock ShellResult(-1, msg)
                }
                if (line.startsWith(marker)) {
                    code = line.substringAfter(' ').trim().toIntOrNull() ?: -1
                    break
                }
                out += line
            }
            if (out.lastOrNull()?.isEmpty() == true) out.removeAt(out.lastIndex)
            ShellResult(code, out.joinToString("\n"))
        }
    }

    suspend fun isRoot(): Boolean = exec("id -u", timeoutMs = 60_000).let { it.ok && it.out.trim() == "0" }
}

/**
 * A separate root process for long-running commands whose output must be streamed
 * (interactive login, module installation). The script prints `__PID__ <pid>` first so
 * [cancel] can kill the real worker even though it runs under the root daemon.
 */
class RootProcess(script: String) {
    private val process: Process = ProcessBuilder("su").redirectErrorStream(true).start()
    @Volatile
    private var workerPid: Int? = null

    init {
        process.outputStream.bufferedWriter().use {
            it.write("echo \"__PID__ \$\$\"\n")
            it.write(script)
            it.write("\n")
        }
    }

    /** Blocks; calls [onLine] for every output line and returns the exit code. */
    fun run(onLine: (String) -> Unit): Int {
        process.inputStream.bufferedReader().forEachLine { line ->
            if (line.startsWith("__PID__ ")) {
                workerPid = line.removePrefix("__PID__ ").trim().toIntOrNull()
            } else {
                onLine(line)
            }
        }
        return process.waitFor()
    }

    suspend fun cancel() {
        workerPid?.let { RootShell.exec("kill -TERM -$it 2>/dev/null; kill -TERM $it 2>/dev/null") }
        process.destroy()
    }
}
