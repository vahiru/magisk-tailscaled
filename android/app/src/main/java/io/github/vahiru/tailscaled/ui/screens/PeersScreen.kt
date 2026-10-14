package io.github.vahiru.tailscaled.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.NetworkPing
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PublicOff
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vahiru.tailscaled.R
import io.github.vahiru.tailscaled.data.Peer
import io.github.vahiru.tailscaled.ui.InfoRow
import io.github.vahiru.tailscaled.ui.Pill
import io.github.vahiru.tailscaled.ui.SectionHeader
import io.github.vahiru.tailscaled.ui.StatusDot
import io.github.vahiru.tailscaled.ui.UiState
import io.github.vahiru.tailscaled.ui.humanBytes
import io.github.vahiru.tailscaled.ui.relativeTime

private enum class PeerFilter(val label: Int) {
    All(R.string.filter_all),
    Online(R.string.filter_online),
    ExitNodes(R.string.filter_exit_nodes),
    Subnets(R.string.filter_subnets),
}

fun osIcon(os: String): ImageVector = when (os.lowercase()) {
    "android" -> Icons.Outlined.PhoneAndroid
    "ios" -> Icons.Outlined.PhoneIphone
    "macos", "windows" -> Icons.Outlined.Laptop
    "linux", "freebsd", "openbsd" -> Icons.Outlined.Computer
    "" -> Icons.Outlined.Devices
    else -> Icons.Outlined.Storage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(state: UiState, onUseExitNode: (String?) -> Unit, onPing: (String) -> Unit) {
    val status = state.status
    if (status == null || !status.running) {
        EmptyState(
            Icons.Outlined.Devices,
            stringResource(if (status?.needsLogin == true) R.string.peers_need_login else R.string.peers_not_connected),
        )
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PeerFilter.All) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }

    val peers = status.peers.filter { p ->
        val q = query.trim().lowercase()
        (q.isEmpty() || p.displayName.lowercase().contains(q) || p.hostName.lowercase().contains(q) ||
            p.ips.any { it.contains(q) } || p.os.lowercase().contains(q)) &&
            when (filter) {
                PeerFilter.All -> true
                PeerFilter.Online -> p.online
                PeerFilter.ExitNodes -> p.exitNodeOption
                PeerFilter.Subnets -> p.subnetRoutes.isNotEmpty()
            }
    }
    val online = peers.filter { it.online }
    val offline = peers.filter { !it.online }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) } }
                } else {
                    null
                },
                placeholder = { Text(stringResource(R.string.search_devices)) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PeerFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(stringResource(f.label)) },
                    )
                }
            }
        }
        if (peers.isEmpty()) {
            item { EmptyState(Icons.Outlined.Search, stringResource(R.string.no_matching_devices)) }
        }
        if (online.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.online_count, online.size)) }
            items(online, key = { "on-" + it.id }) { PeerRow(it) { selectedId = it.id } }
        }
        if (offline.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.offline_count, offline.size)) }
            items(offline, key = { "off-" + it.id }) { PeerRow(it) { selectedId = it.id } }
        }
    }

    val selected = status.peers.firstOrNull { it.id == selectedId }
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { selectedId = null }, sheetState = sheetState) {
            PeerSheet(
                peer = selected,
                onUseExitNode = { ip -> onUseExitNode(ip); selectedId = null },
                onPing = { ip -> onPing(ip) },
            )
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(osIcon(peer.os), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                StatusDot(peer.online, Modifier.align(Alignment.BottomEnd))
            }
        },
        headlineContent = { Text(peer.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val seen = if (peer.online) stringResource(R.string.online) else relativeTime(peer.lastSeen) ?: stringResource(R.string.offline)
            Text(
                listOfNotNull(peer.ipv4, peer.os.ifBlank { null }, seen).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (peer.exitNode) {
                    Pill(stringResource(R.string.tag_exit_in_use), MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                } else if (peer.exitNodeOption) {
                    Pill(stringResource(R.string.tag_exit_node), MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
                if (peer.subnetRoutes.isNotEmpty()) {
                    Pill(stringResource(R.string.tag_subnet), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        },
    )
}

@Composable
private fun PeerSheet(peer: Peer, onUseExitNode: (String?) -> Unit, onPing: (String) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(osIcon(peer.os), null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.displayName, style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(peer.online)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (peer.online) stringResource(R.string.online)
                        else stringResource(R.string.last_seen, relativeTime(peer.lastSeen) ?: "—"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val ip = peer.ipv4 ?: peer.ipv6
            if (ip != null) {
                OutlinedButton(onClick = { onPing(ip) }) {
                    Icon(Icons.Outlined.NetworkPing, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.ping))
                }
            }
            if (peer.exitNode) {
                FilledTonalButton(onClick = { onUseExitNode(null) }) {
                    Icon(Icons.Outlined.PublicOff, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.stop_using_exit_node))
                }
            } else if (peer.exitNodeOption && ip != null) {
                FilledTonalButton(onClick = { onUseExitNode(peer.ipv4 ?: ip) }, enabled = peer.online) {
                    Icon(Icons.Outlined.Public, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.use_exit_node))
                }
            }
        }
        peer.ipv4?.let { InfoRow("IPv4", it, copyable = true) }
        peer.ipv6?.let { InfoRow("IPv6", it, copyable = true) }
        if (peer.dnsName.isNotBlank()) InfoRow(stringResource(R.string.magicdns_name), peer.dnsName.trimEnd('.'), copyable = true)
        InfoRow(stringResource(R.string.os), peer.os)
        if (peer.subnetRoutes.isNotEmpty()) {
            InfoRow(stringResource(R.string.subnet_routes), peer.subnetRoutes.joinToString("\n"), icon = Icons.Outlined.Router)
        }
        if (peer.online) {
            InfoRow(
                stringResource(R.string.connection),
                when {
                    peer.curAddr.isNotBlank() -> stringResource(R.string.conn_direct, peer.curAddr)
                    peer.relay.isNotBlank() -> stringResource(R.string.conn_relay, peer.relay)
                    else -> "—"
                },
            )
            InfoRow(stringResource(R.string.traffic), "↓ ${humanBytes(peer.rxBytes)}   ↑ ${humanBytes(peer.txBytes)}")
        }
        if (peer.tags.isNotEmpty()) InfoRow(stringResource(R.string.tags), peer.tags.joinToString(", "))
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
