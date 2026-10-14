package io.github.vahiru.tailscaled.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vahiru.tailscaled.R
import io.github.vahiru.tailscaled.ui.screens.HomeScreen
import io.github.vahiru.tailscaled.ui.screens.LoginScreen
import io.github.vahiru.tailscaled.ui.screens.PeersScreen
import io.github.vahiru.tailscaled.ui.screens.SettingsActions
import io.github.vahiru.tailscaled.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

private enum class Tab(val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home(R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Devices(R.string.tab_devices, Icons.Outlined.Devices, Icons.Filled.Devices),
    Settings(R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val login by vm.login.collectAsStateWithLifecycle()
    val task by vm.task.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showLogin by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it.resolve(ctx)) }
    }

    val closeLogin = {
        if (login.running) vm.cancelLogin()
        showLogin = false
        vm.resetLogin()
    }
    BackHandler(enabled = showLogin) { closeLogin() }
    BackHandler(enabled = !showLogin && tab != Tab.Home) { tab = Tab.Home }

    if (showLogin) {
        LoginScreen(
            login = login,
            prefs = state.prefs,
            snackbar = snackbar,
            onStart = vm::startLogin,
            onCancel = vm::cancelLogin,
            onClose = closeLogin,
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (tab == Tab.Home) R.string.app_name else tab.label)) },
                    actions = {
                        IconButton(onClick = vm::refreshNow) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh))
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                            label = { Text(stringResource(t.label)) },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                when {
                    !state.loaded -> Loading()
                    state.root == RootState.Denied -> NoRoot(vm::retryRoot)
                    else -> PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = {
                            scope.launch {
                                refreshing = true
                                vm.refresh()
                                refreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Crossfade(targetState = tab, label = "tab") { t ->
                            when (t) {
                                Tab.Home -> HomeScreen(
                                    state = state,
                                    onConnect = vm::setConnected,
                                    onLogin = { showLogin = true },
                                    onInstallModule = vm::installModule,
                                    onClearExitNode = { vm.useExitNode(null) },
                                    onOpenDevices = { tab = Tab.Devices },
                                )
                                Tab.Devices -> PeersScreen(state, vm::useExitNode, vm::ping)
                                Tab.Settings -> SettingsScreen(
                                    state,
                                    SettingsActions(
                                        onLogin = { showLogin = true },
                                        onLogout = vm::logout,
                                        onPref = vm::setPref,
                                        onHostname = vm::setHostname,
                                        onDaemon = vm::setDaemon,
                                        onAutostart = vm::setAutostart,
                                        onVpnCoexist = vm::setVpnCoexist,
                                        onRestartDaemon = vm::restartDaemon,
                                        onShowLog = vm::showLog,
                                        onInstallModule = vm::installModule,
                                        onReboot = vm::reboot,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    task?.let { t ->
        TaskDialog(t, onDismiss = vm::dismissTask)
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.requesting_root), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NoRoot(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.no_root_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.no_root_text),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
