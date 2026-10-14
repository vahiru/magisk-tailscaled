package io.github.vahiru.tailscaled.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vahiru.tailscaled.BuildConfig
import io.github.vahiru.tailscaled.R
import io.github.vahiru.tailscaled.ui.InfoRow
import io.github.vahiru.tailscaled.ui.StatusDot
import io.github.vahiru.tailscaled.ui.UiState
import io.github.vahiru.tailscaled.ui.humanBytes

private enum class Conn { NoDaemon, Starting, NeedsLogin, Connecting, Connected, Disconnected }

private fun UiState.conn(): Conn {
    val s = status
    return when {
        !module.daemonRunning -> Conn.NoDaemon
        s == null -> Conn.Starting
        s.needsLogin -> Conn.NeedsLogin
        s.running -> Conn.Connected
        s.backendState == "Starting" -> Conn.Connecting
        else -> Conn.Disconnected
    }
}

@Composable
fun HomeScreen(
    state: UiState,
    onConnect: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onInstallModule: () -> Unit,
    onClearExitNode: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.module.installed) {
            item { ModuleMissingCard(state, onInstallModule) }
            return@LazyColumn
        }
        item { ConnectionCard(state, onConnect, onLogin) }
        if (state.module.disabled) {
            item {
                NoticeCard(Icons.Outlined.Extension, stringResource(R.string.module_disabled_notice), warning = false)
            }
        }
        val status = state.status
        if (status?.running == true && state.module.vpnActive) {
            item { VpnCoexistCard(state) }
        }
        if (status?.running == true && state.privateDns && state.prefs?.corpDns != false) {
            item { NoticeCard(Icons.Outlined.Dns, stringResource(R.string.private_dns_notice), warning = false) }
        }
        val health = status?.health.orEmpty()
        if (health.isNotEmpty()) {
            item { NoticeCard(Icons.Outlined.Warning, health.joinToString("\n\n"), warning = true) }
        }
        if (status?.running == true) {
            status.currentExitNode?.let { exit ->
                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Public, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text(stringResource(R.string.exit_node_in_use), style = MaterialTheme.typography.labelMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusDot(exit.online)
                                    Spacer(Modifier.size(8.dp))
                                    Text(exit.displayName, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "↓ ${humanBytes(exit.rxBytes)}  ↑ ${humanBytes(exit.txBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = onClearExitNode) { Text(stringResource(R.string.stop_using)) }
                        }
                    }
                }
            }
            item { ThisDeviceCard(state) }
            item {
                val online = status.peers.count { it.online }
                val exits = status.peers.count { it.exitNodeOption && it.online }
                val subnets = status.peers.count { it.subnetRoutes.isNotEmpty() }
                Card(onClick = onOpenDevices, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Stat(online.toString() + "/" + status.peers.size, stringResource(R.string.stat_online), Modifier.weight(1f))
                        Stat(exits.toString(), stringResource(R.string.stat_exit_nodes), Modifier.weight(1f))
                        Stat(subnets.toString(), stringResource(R.string.stat_subnet_routers), Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(state: UiState, onConnect: (Boolean) -> Unit, onLogin: () -> Unit) {
    val conn = state.conn()
    val on = conn == Conn.Connected || conn == Conn.Connecting
    val colors = if (on) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    val (title, icon) = when (conn) {
        Conn.NoDaemon -> stringResource(R.string.state_no_daemon) to Icons.Outlined.PowerSettingsNew
        Conn.Starting -> stringResource(R.string.state_starting) to Icons.Outlined.PowerSettingsNew
        Conn.NeedsLogin -> stringResource(R.string.state_needs_login) to Icons.AutoMirrored.Outlined.Login
        Conn.Connecting -> stringResource(R.string.state_connecting) to Icons.Outlined.Shield
        Conn.Connected -> stringResource(R.string.state_connected) to Icons.Outlined.VerifiedUser
        Conn.Disconnected -> stringResource(R.string.state_disconnected) to Icons.Outlined.CloudOff
    }
    val subtitle = when (conn) {
        Conn.NoDaemon -> stringResource(R.string.state_no_daemon_hint)
        Conn.NeedsLogin -> stringResource(R.string.state_needs_login_hint)
        Conn.Connected, Conn.Connecting ->
            state.status?.tailnetName?.ifBlank { null } ?: state.status?.selfUser?.loginName.orEmpty()
        Conn.Disconnected -> stringResource(R.string.state_disconnected_hint)
        Conn.Starting -> ""
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(36.dp))
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Switch(
                    checked = on,
                    onCheckedChange = onConnect,
                    enabled = !state.busy && conn != Conn.NeedsLogin && conn != Conn.Starting,
                )
            }
            if (conn == Conn.NeedsLogin) {
                Spacer(Modifier.size(16.dp))
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Login, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.login))
                }
            }
        }
    }
}

@Composable
private fun ThisDeviceCard(state: UiState) {
    val status = state.status ?: return
    val self = status.self ?: return
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.this_device),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            InfoRow(stringResource(R.string.device_name), self.displayName, icon = Icons.Outlined.PhoneAndroid)
            InfoRow("IPv4", self.ipv4.orEmpty(), copyable = true, icon = Icons.Outlined.Language)
            InfoRow("IPv6", self.ipv6.orEmpty(), copyable = true, icon = Icons.Outlined.Language)
            if (self.dnsName.isNotBlank()) {
                InfoRow(
                    stringResource(R.string.magicdns_name),
                    self.dnsName.trimEnd('.'),
                    copyable = true,
                    icon = Icons.Outlined.Dns,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            InfoRow(
                stringResource(R.string.account),
                status.selfUser?.loginName.orEmpty(),
                icon = Icons.Outlined.Person,
            )
        }
    }
}

/** Explains what still goes through Tailscale while another VPN app is active. */
@Composable
private fun VpnCoexistCard(state: UiState) {
    val module = state.module
    val prefs = state.prefs
    val usingExitNode = state.status?.currentExitNode != null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VpnLock, null)
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.vpn_detected, module.vpnInterfaces.joinToString(", ")),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            VpnLine(true, stringResource(R.string.vpn_tailnet_ok))
            if (prefs?.routeAll == true) {
                if (module.vpnCoexist) {
                    VpnLine(true, stringResource(R.string.vpn_subnets_kept, module.coexistRules))
                } else {
                    VpnLine(false, stringResource(R.string.vpn_subnets_lost))
                }
            }
            VpnLine(null, stringResource(R.string.vpn_internet_via_vpn))
            if (usingExitNode) VpnLine(false, stringResource(R.string.vpn_exit_node_inactive))
            if (prefs?.corpDns != false) VpnLine(false, stringResource(R.string.vpn_magicdns))
        }
    }
}

/** ok = true: works, false: affected, null: neutral information. */
@Composable
private fun VpnLine(ok: Boolean?, text: String) {
    Row {
        Icon(
            when (ok) {
                true -> Icons.Outlined.CheckCircle
                false -> Icons.Outlined.ErrorOutline
                null -> Icons.Outlined.Info
            },
            null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoticeCard(icon: ImageVector, text: String, warning: Boolean) {
    val colors = if (warning) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, null)
            Spacer(Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ModuleMissingCard(state: UiState, onInstall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Extension, null, modifier = Modifier.size(36.dp))
            Text(stringResource(R.string.module_missing_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                if (state.module.pendingUpdate) {
                    stringResource(R.string.module_pending_reboot)
                } else {
                    stringResource(R.string.module_missing_text, state.module.rootManager.label)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = onInstall, enabled = !state.busy) {
                Text(stringResource(R.string.install_bundled_module, BuildConfig.MODULE_VERSION))
            }
        }
    }
}
