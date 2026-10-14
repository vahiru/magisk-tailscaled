package io.github.vahiru.tailscaled.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.vahiru.tailscaled.BuildConfig
import io.github.vahiru.tailscaled.R
import io.github.vahiru.tailscaled.data.ModuleInfo
import io.github.vahiru.tailscaled.data.TailscaleRepository
import io.github.vahiru.tailscaled.data.TsPrefs
import io.github.vahiru.tailscaled.data.TsStatus
import io.github.vahiru.tailscaled.data.isPrivateDnsActive
import io.github.vahiru.tailscaled.root.RootProcess
import io.github.vahiru.tailscaled.root.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class RootState { Checking, Granted, Denied }

data class UiState(
    val root: RootState = RootState.Checking,
    val module: ModuleInfo = ModuleInfo(),
    val status: TsStatus? = null,
    val prefs: TsPrefs? = null,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val privateDns: Boolean = false,
) {
    val bundledModuleNewer: Boolean
        get() = !module.installed || BuildConfig.MODULE_VERSION_CODE > module.versionCode
}

data class LoginState(
    val running: Boolean = false,
    val authUrl: String = "",
    val output: List<String> = emptyList(),
    val success: Boolean = false,
    val error: String = "",
)

data class TaskLog(
    val title: String,
    val lines: List<String> = emptyList(),
    val running: Boolean = true,
    val exitCode: Int? = null,
)

sealed interface UiMessage {
    data class Res(@StringRes val id: Int, val arg: String? = null) : UiMessage
    data class Text(val text: String) : UiMessage
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TailscaleRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _login = MutableStateFlow(LoginState())
    val login: StateFlow<LoginState> = _login.asStateFlow()

    private val _task = MutableStateFlow<TaskLog?>(null)
    val task: StateFlow<TaskLog?> = _task.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val refreshLock = Mutex()
    private var pollJob: Job? = null
    private var loginProcess: RootProcess? = null

    /** Poll only while the UI is visible. */
    fun setForeground(foreground: Boolean) {
        pollJob?.cancel()
        if (!foreground) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(if (_state.value.root == RootState.Denied) 10_000 else 3_000)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() = refreshLock.withLock {
        if (_state.value.root != RootState.Granted) {
            val root = repo.isRoot()
            _state.update { it.copy(root = if (root) RootState.Granted else RootState.Denied, loaded = !root) }
            if (!root) return@withLock
        }
        val module = repo.probe()
        val status = if (module.daemonRunning) repo.status() else null
        val prefs = if (status != null) repo.prefs() else null
        val privateDns = isPrivateDnsActive(getApplication())
        _state.update { it.copy(module = module, status = status, prefs = prefs, privateDns = privateDns, loaded = true) }
    }

    private fun action(block: suspend () -> ShellResult?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val r = block()
                if (r != null && !r.ok) {
                    _messages.send(UiMessage.Text(r.out.lines().lastOrNull { it.isNotBlank() } ?: "error ${r.code}"))
                }
            } finally {
                refresh()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun retryRoot() {
        _state.update { it.copy(root = RootState.Checking) }
        refreshNow()
    }

    /** Main switch: brings the daemon up if needed, then connects / disconnects. */
    fun setConnected(on: Boolean) = action {
        if (on) {
            if (!_state.value.module.daemonRunning) {
                val r = repo.startDaemon()
                if (!r.ok) return@action r
            }
            repo.up()
        } else {
            repo.down()
        }
    }

    fun setDaemon(on: Boolean) = action { if (on) repo.startDaemon() else repo.stopDaemon() }
    fun restartDaemon() = action { repo.restartDaemon() }
    fun setAutostart(on: Boolean) = action { repo.setAutostart(on) }
    fun setVpnCoexist(on: Boolean) = action { repo.setVpnCoexist(on) }

    fun setPref(flag: String, on: Boolean) = action { repo.set(flag, on.toString()) }
    fun setHostname(name: String) = action { repo.set("hostname", name) }
    fun useExitNode(ip: String?) = action {
        repo.useExitNode(ip).also {
            if (it.ok) _messages.send(if (ip == null) UiMessage.Res(R.string.msg_exit_node_cleared) else UiMessage.Res(R.string.msg_exit_node_set, ip))
        }
    }

    fun logout() = action { repo.logout() }

    fun ping(ip: String) {
        viewModelScope.launch {
            _task.value = TaskLog(title = "ping $ip")
            val r = repo.ping(ip)
            _task.value = TaskLog("ping $ip", r.out.lines(), running = false, exitCode = r.code)
        }
    }

    fun showLog(daemon: Boolean) {
        viewModelScope.launch {
            val title = if (daemon) "tailscaled.log" else "service.log"
            _task.value = TaskLog(title)
            _task.value = TaskLog(title, repo.readLog(daemon).lines(), running = false)
        }
    }

    fun dismissTask() {
        _task.value = null
    }

    fun installModule() {
        viewModelScope.launch {
            val title = "module ${BuildConfig.MODULE_VERSION}"
            _task.value = TaskLog(title, listOf("- Preparing module zip"))
            val zip = repo.stageBundledModule()
            if (zip == null) {
                _task.value = TaskLog(title, listOf("! Unable to copy the bundled module"), running = false, exitCode = 1)
                return@launch
            }
            val proc = withContext(Dispatchers.IO) { repo.installModuleProcess(zip) }
            val lines = ArrayList<String>()
            val code = withContext(Dispatchers.IO) {
                proc.run { line ->
                    lines += line
                    _task.value = TaskLog(title, lines.toList())
                }
            }
            _task.value = TaskLog(title, lines.toList(), running = false, exitCode = code)
            refresh()
        }
    }

    fun reboot() = action { repo.reboot() }

    fun startLogin(controlUrl: String, hostname: String, authKey: String?) {
        if (_login.value.running) return
        _login.value = LoginState(running = true)
        viewModelScope.launch {
            if (!_state.value.module.daemonRunning) {
                val r = repo.startDaemon()
                if (!r.ok) {
                    _login.value = LoginState(error = r.out.lines().lastOrNull().orEmpty())
                    return@launch
                }
            }
            val keyFile = authKey?.takeIf { it.isNotBlank() }?.let { repo.writeAuthKey(it) }
            if (authKey != null && authKey.isNotBlank() && keyFile == null) {
                _login.value = LoginState(error = "unable to write auth key")
                return@launch
            }
            val proc = withContext(Dispatchers.IO) { repo.loginProcess(controlUrl, hostname, keyFile) }
            loginProcess = proc
            val urlRegex = Regex("""https?://\S+""")
            val output = ArrayList<String>()
            val code = withContext(Dispatchers.IO) {
                proc.run { line ->
                    output += line
                    val url = urlRegex.find(line)?.value
                    _login.update { s -> s.copy(output = output.toList(), authUrl = url ?: s.authUrl) }
                }
            }
            loginProcess = null
            keyFile?.let { repo.removeAuthKey(it) }
            refresh()
            _login.update {
                it.copy(
                    running = false,
                    success = code == 0,
                    error = if (code == 0) "" else output.lastOrNull { l -> l.isNotBlank() } ?: "exit $code",
                )
            }
            if (code == 0) _messages.send(UiMessage.Res(R.string.msg_logged_in))
        }
    }

    fun cancelLogin() {
        val p = loginProcess ?: return
        viewModelScope.launch { p.cancel() }
    }

    fun resetLogin() {
        if (!_login.value.running) _login.value = LoginState()
    }
}
