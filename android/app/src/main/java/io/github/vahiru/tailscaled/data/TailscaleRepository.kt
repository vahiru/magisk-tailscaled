package io.github.vahiru.tailscaled.data

import android.content.Context
import io.github.vahiru.tailscaled.root.RootProcess
import io.github.vahiru.tailscaled.root.RootShell
import io.github.vahiru.tailscaled.root.ShellResult
import io.github.vahiru.tailscaled.root.shQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Talks to the Magisk Tailscaled module and the tailscaled daemon through `su`. */
class TailscaleRepository(private val context: Context) {

    companion object {
        const val TS_DIR = "/data/adb/tailscale"
        const val TS = "$TS_DIR/bin/tailscale"
        const val SERVICE = "$TS_DIR/scripts/tailscaled.service"
        const val COMMON = "$TS_DIR/scripts/common.sh"
        const val CONF = "$TS_DIR/settings.conf"
        const val MOD_DIR = "/data/adb/modules/magisk-tailscaled"
        const val MOD_UPDATE_DIR = "/data/adb/modules_update/magisk-tailscaled"
        const val DAEMON_LOG = "$TS_DIR/run/tailscaled.log"
        const val SERVICE_LOG = "$TS_DIR/run/service.log"
    }

    suspend fun isRoot(): Boolean = RootShell.isRoot()

    /** Module + daemon state in a single round trip. */
    suspend fun probe(): ModuleInfo {
        val script = """
            echo "INSTALLED=$([ -x $TS_DIR/bin/tailscaled ] && echo 1 || echo 0)"
            echo "MODDIR=$([ -d $MOD_DIR ] && echo 1 || echo 0)"
            echo "DISABLED=$([ -f $MOD_DIR/disable ] && echo 1 || echo 0)"
            echo "UPDATE=$([ -d $MOD_UPDATE_DIR ] && echo 1 || echo 0)"
            P=$MOD_DIR/module.prop; [ -f $MOD_UPDATE_DIR/module.prop ] && P=$MOD_UPDATE_DIR/module.prop
            echo "VERSION=$(sed -n 's/^version=//p' ${'$'}P 2>/dev/null)"
            echo "VERSIONCODE=$(sed -n 's/^versionCode=//p' ${'$'}P 2>/dev/null)"
            echo "AUTOSTART=$(sed -n 's/^TS_AUTOSTART=//p' $CONF 2>/dev/null | tr -d '"')"
            if [ -f /data/adb/ap/bin/busybox ] || [ -x /data/adb/apd ]; then echo MGR=APatch
            elif [ -x /data/adb/ksud ] || [ -d /data/adb/ksu ]; then echo MGR=KernelSU
            elif [ -d /data/adb/magisk ]; then echo MGR=Magisk; fi
            [ -f $COMMON ] && echo "PID=$(sh -c '. $COMMON >/dev/null 2>&1; daemon_pid' 2>/dev/null)"
            true
        """.trimIndent()
        val kv = parseKv(RootShell.exec(script).out)
        return ModuleInfo(
            installed = kv["INSTALLED"] == "1",
            moduleDir = kv["MODDIR"] == "1",
            disabled = kv["DISABLED"] == "1",
            pendingUpdate = kv["UPDATE"] == "1",
            version = kv["VERSION"].orEmpty(),
            versionCode = kv["VERSIONCODE"]?.toIntOrNull() ?: 0,
            autostart = kv["AUTOSTART"] != "0",
            rootManager = when (kv["MGR"]) {
                "APatch" -> RootManager.APatch
                "KernelSU" -> RootManager.KernelSU
                "Magisk" -> RootManager.Magisk
                else -> RootManager.Unknown
            },
            daemonPid = kv["PID"]?.toIntOrNull(),
        )
    }

    suspend fun status(): TsStatus? {
        val r = RootShell.exec("$TS status --json 2>/dev/null", timeoutMs = 10_000)
        if (!r.ok) return null
        return runCatching { TsStatus.parse(r.out) }.getOrNull()
    }

    suspend fun prefs(): TsPrefs? {
        val r = RootShell.exec("$TS debug prefs 2>/dev/null", timeoutMs = 10_000)
        if (!r.ok) return null
        return runCatching { TsPrefs.parse(r.out) }.getOrNull()
    }

    suspend fun startDaemon() = RootShell.exec("$SERVICE start", timeoutMs = 30_000)
    suspend fun stopDaemon() = RootShell.exec("$SERVICE stop", timeoutMs = 30_000)
    suspend fun restartDaemon() = RootShell.exec("$SERVICE restart", timeoutMs = 45_000)

    suspend fun up() = RootShell.exec("$TS up --timeout=20s", timeoutMs = 30_000)
    suspend fun down() = RootShell.exec("$TS down")
    suspend fun logout() = RootShell.exec("$TS logout", timeoutMs = 30_000)

    /** `tailscale set --<flag>=<value>`; value is quoted. */
    suspend fun set(flag: String, value: String) = RootShell.exec("$TS set --$flag=${shQuote(value)}")

    suspend fun useExitNode(ip: String?) = set("exit-node", ip.orEmpty())

    suspend fun ping(ip: String) =
        RootShell.exec("$TS ping --c=3 --timeout=3s ${shQuote(ip)}", timeoutMs = 20_000)

    suspend fun setAutostart(on: Boolean): ShellResult {
        val v = if (on) 1 else 0
        return RootShell.exec(
            "if grep -q '^TS_AUTOSTART=' $CONF 2>/dev/null; then sed -i 's/^TS_AUTOSTART=.*/TS_AUTOSTART=$v/' $CONF; " +
                "else echo 'TS_AUTOSTART=$v' >> $CONF; fi",
        )
    }

    suspend fun readLog(daemon: Boolean, lines: Int = 400): String =
        RootShell.exec("tail -n $lines ${if (daemon) DAEMON_LOG else SERVICE_LOG} 2>&1").out

    /**
     * Interactive login. Emits output lines (the auth URL shows up in them) and returns the
     * exit code once the node is authenticated, the timeout hits or [RootProcess.cancel] runs.
     */
    fun loginProcess(controlUrl: String, hostname: String, authKeyFile: String?): RootProcess {
        val args = buildList {
            add("--timeout=15m")
            if (controlUrl.isNotBlank()) add("--login-server=${shQuote(controlUrl.trim())}")
            if (hostname.isNotBlank()) add("--hostname=${shQuote(hostname.trim())}")
            if (authKeyFile != null) add("--auth-key=file:$authKeyFile")
        }.joinToString(" ")
        return RootProcess("exec $TS login $args")
    }

    suspend fun removeAuthKey(path: String) = RootShell.exec("rm -f ${shQuote(path)}")

    /** Writes the auth key to a root-only file so it never shows up in a process list. */
    suspend fun writeAuthKey(key: String): String? {
        val path = "$TS_DIR/run/.authkey"
        val r = RootShell.exec(
            "umask 077; mkdir -p $TS_DIR/run; printf '%s' ${shQuote(key.trim())} > $path && echo ok",
        )
        return if (r.ok && r.out.trim() == "ok") path else null
    }

    /** Copies the bundled module zip somewhere the root manager can read it. */
    suspend fun stageBundledModule(): String? = withContext(Dispatchers.IO) {
        val local = File(context.cacheDir, "module.zip")
        runCatching {
            context.assets.open("module.zip").use { input -> local.outputStream().use { input.copyTo(it) } }
        }.getOrElse { return@withContext null }
        val target = "/data/local/tmp/magisk-tailscaled.zip"
        val r = RootShell.exec("cp -f ${shQuote(local.absolutePath)} $target && chmod 0644 $target && echo ok")
        local.delete()
        if (r.ok && r.out.trim().endsWith("ok")) target else null
    }

    fun installModuleProcess(zip: String): RootProcess = RootProcess(
        """
        Z=${shQuote(zip)}
        if [ -x /data/adb/ksud ]; then exec /data/adb/ksud module install "${'$'}Z"
        elif [ -x /data/adb/apd ]; then exec /data/adb/apd module install "${'$'}Z"
        elif command -v magisk >/dev/null 2>&1; then exec magisk --install-module "${'$'}Z"
        else echo "No supported root manager (Magisk / KernelSU / APatch) found"; exit 1; fi
        """.trimIndent(),
    )

    suspend fun reboot() = RootShell.exec("svc power reboot || reboot")
}
