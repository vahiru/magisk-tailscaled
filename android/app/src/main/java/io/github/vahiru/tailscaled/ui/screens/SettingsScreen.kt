package io.github.vahiru.tailscaled.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vahiru.tailscaled.BuildConfig
import io.github.vahiru.tailscaled.R
import io.github.vahiru.tailscaled.ui.ConfirmDialog
import io.github.vahiru.tailscaled.ui.SectionHeader
import io.github.vahiru.tailscaled.ui.SwitchRow
import io.github.vahiru.tailscaled.ui.UiState

class SettingsActions(
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
    val onPref: (flag: String, on: Boolean) -> Unit,
    val onHostname: (String) -> Unit,
    val onDaemon: (Boolean) -> Unit,
    val onAutostart: (Boolean) -> Unit,
    val onRestartDaemon: () -> Unit,
    val onShowLog: (daemon: Boolean) -> Unit,
    val onInstallModule: () -> Unit,
    val onReboot: () -> Unit,
)

@Composable
fun SettingsScreen(state: UiState, a: SettingsActions) {
    val status = state.status
    val prefs = state.prefs
    val module = state.module
    val connected = status != null && !status.needsLogin
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var confirmReboot by rememberSaveable { mutableStateOf(false) }
    var editHostname by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Account
        item { SectionHeader(stringResource(R.string.section_account)) }
        if (connected) {
            item {
                ActionRow(
                    Icons.Outlined.Person,
                    status?.selfUser?.loginName?.ifBlank { null } ?: stringResource(R.string.account),
                    status?.selfUser?.displayName,
                )
            }
            item {
                ActionRow(
                    Icons.Outlined.Cloud,
                    stringResource(R.string.control_server),
                    prefs?.controlUrl?.ifBlank { null } ?: "https://controlplane.tailscale.com",
                )
            }
        }
        item {
            ActionRow(
                Icons.AutoMirrored.Outlined.Login,
                stringResource(if (connected) R.string.switch_account else R.string.login),
                stringResource(R.string.login_summary),
                enabled = module.installed,
                onClick = a.onLogin,
            )
        }
        if (connected) {
            item {
                ActionRow(
                    Icons.AutoMirrored.Outlined.Logout,
                    stringResource(R.string.logout),
                    null,
                    onClick = { confirmLogout = true },
                )
            }
        }

        // Network preferences; need a running, logged in daemon.
        if (connected && prefs != null) {
            item { SectionHeader(stringResource(R.string.section_network)) }
            item {
                ActionRow(
                    Icons.Outlined.Badge,
                    stringResource(R.string.device_name),
                    prefs.hostname.ifBlank { status?.self?.hostName.orEmpty() },
                    trailing = Icons.Outlined.Edit,
                    onClick = { editHostname = true },
                )
            }
            item {
                SwitchRow(stringResource(R.string.pref_accept_routes), stringResource(R.string.pref_accept_routes_summary), prefs.routeAll, !state.busy, Icons.Outlined.Router) {
                    a.onPref("accept-routes", it)
                }
            }
            item {
                SwitchRow(stringResource(R.string.pref_accept_dns), stringResource(R.string.pref_accept_dns_summary), prefs.corpDns, !state.busy, Icons.Outlined.Dns) {
                    a.onPref("accept-dns", it)
                }
            }
            item {
                SwitchRow(stringResource(R.string.pref_ssh), stringResource(R.string.pref_ssh_summary), prefs.runSsh, !state.busy, Icons.Outlined.Terminal) {
                    a.onPref("ssh", it)
                }
            }
            item {
                SwitchRow(stringResource(R.string.pref_shields_up), stringResource(R.string.pref_shields_up_summary), prefs.shieldsUp, !state.busy, Icons.Outlined.Shield) {
                    a.onPref("shields-up", it)
                }
            }
            item {
                SwitchRow(stringResource(R.string.pref_advertise_exit), stringResource(R.string.pref_advertise_exit_summary), prefs.advertiseExitNode, !state.busy, Icons.Outlined.Public) {
                    a.onPref("advertise-exit-node", it)
                }
            }
            item {
                SwitchRow(stringResource(R.string.pref_exit_lan), stringResource(R.string.pref_exit_lan_summary), prefs.exitNodeAllowLan, !state.busy, Icons.Outlined.Wifi) {
                    a.onPref("exit-node-allow-lan-access", it)
                }
            }
        }

        // Daemon
        if (module.installed) {
            item { SectionHeader(stringResource(R.string.section_daemon)) }
            item {
                SwitchRow(
                    stringResource(R.string.daemon_running),
                    module.daemonPid?.let { stringResource(R.string.daemon_pid, it) } ?: stringResource(R.string.daemon_stopped),
                    module.daemonRunning,
                    !state.busy,
                    Icons.Outlined.PowerSettingsNew,
                    a.onDaemon,
                )
            }
            item {
                SwitchRow(stringResource(R.string.autostart), stringResource(R.string.autostart_summary), module.autostart, !state.busy, Icons.Outlined.Security, a.onAutostart)
            }
            item { ActionRow(Icons.Outlined.RestartAlt, stringResource(R.string.restart_daemon), null, enabled = !state.busy, onClick = a.onRestartDaemon) }
            item { ActionRow(Icons.Outlined.Description, stringResource(R.string.daemon_log), "tailscaled.log", onClick = { a.onShowLog(true) }) }
            item { ActionRow(Icons.Outlined.Description, stringResource(R.string.service_log), "service.log", onClick = { a.onShowLog(false) }) }
        }

        // Module
        item { SectionHeader(stringResource(R.string.section_module)) }
        item {
            ActionRow(
                Icons.Outlined.Extension,
                stringResource(R.string.module_version),
                if (module.installed) {
                    module.version + (if (module.disabled) " · " + stringResource(R.string.module_disabled_short) else "")
                } else {
                    stringResource(R.string.not_installed)
                },
            )
        }
        item {
            ActionRow(
                Icons.Outlined.SystemUpdate,
                stringResource(
                    if (module.installed) R.string.update_bundled_module else R.string.install_bundled_module,
                    BuildConfig.MODULE_VERSION,
                ),
                if (state.bundledModuleNewer) null else stringResource(R.string.module_up_to_date),
                enabled = !state.busy && module.rootManager.name != "Unknown",
                onClick = a.onInstallModule,
            )
        }
        if (module.pendingUpdate) {
            item {
                ActionRow(
                    Icons.Outlined.RestartAlt,
                    stringResource(R.string.reboot),
                    stringResource(R.string.reboot_summary),
                    onClick = { confirmReboot = true },
                )
            }
        }
        item { ActionRow(Icons.Outlined.Lan, stringResource(R.string.root_manager), module.rootManager.label) }

        // About
        item { SectionHeader(stringResource(R.string.section_about)) }
        item {
            ActionRow(
                Icons.Outlined.Info,
                stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
                status?.version?.let { stringResource(R.string.tailscale_version, it) },
            )
        }
    }

    if (confirmLogout) {
        ConfirmDialog(
            stringResource(R.string.logout),
            stringResource(R.string.logout_confirm),
            stringResource(R.string.logout),
            a.onLogout,
        ) { confirmLogout = false }
    }
    if (confirmReboot) {
        ConfirmDialog(
            stringResource(R.string.reboot),
            stringResource(R.string.reboot_confirm),
            stringResource(R.string.reboot),
            a.onReboot,
        ) { confirmReboot = false }
    }
    if (editHostname) {
        var name by remember { mutableStateOf(prefs?.hostname.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editHostname = false },
            title = { Text(stringResource(R.string.device_name)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filter { c -> c.isLetterOrDigit() || c == '-' } },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.device_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = { editHostname = false; a.onHostname(name) }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { editHostname = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    enabled: Boolean = true,
    trailing: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = summary?.let {
            { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) }
        },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) },
        trailingContent = trailing?.let { { Icon(it, null) } },
        modifier = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
