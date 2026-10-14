package io.github.vahiru.tailscaled.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class Peer(
    val id: String,
    val hostName: String,
    val dnsName: String,
    val os: String,
    val ips: List<String>,
    val online: Boolean,
    val active: Boolean,
    val exitNode: Boolean,
    val exitNodeOption: Boolean,
    val lastSeen: Instant?,
    val relay: String,
    val curAddr: String,
    val rxBytes: Long,
    val txBytes: Long,
    val primaryRoutes: List<String>,
    val tags: List<String>,
    val userId: Long,
) {
    val ipv4: String? get() = ips.firstOrNull { '.' in it }
    val ipv6: String? get() = ips.firstOrNull { ':' in it }

    /** MagicDNS short name, e.g. "laptop" from "laptop.tail1234.ts.net." */
    val displayName: String get() = dnsName.substringBefore('.').ifBlank { hostName }

    /** Subnet routes other than the exit-node default routes. */
    val subnetRoutes: List<String>
        get() = primaryRoutes.filter { it != "0.0.0.0/0" && it != "::/0" }

    val connection: String
        get() = when {
            curAddr.isNotBlank() -> "direct"
            relay.isNotBlank() -> "relay:$relay"
            else -> ""
        }
}

data class TsUser(val loginName: String, val displayName: String)

data class TsStatus(
    val backendState: String,
    val authUrl: String,
    val version: String,
    val self: Peer?,
    val peers: List<Peer>,
    val users: Map<Long, TsUser>,
    val tailnetName: String,
    val magicDnsSuffix: String,
    val magicDnsEnabled: Boolean,
    val health: List<String>,
    val exitNodeId: String,
) {
    val running: Boolean get() = backendState == "Running"
    val needsLogin: Boolean get() = backendState == "NeedsLogin" || backendState == "NoState"
    val selfUser: TsUser? get() = self?.let { users[it.userId] }
    val currentExitNode: Peer? get() = peers.firstOrNull { it.exitNode } ?: peers.firstOrNull { it.id == exitNodeId }

    companion object {
        fun parse(json: String): TsStatus {
            val o = JSONObject(json)
            val peers = o.optJSONObject("Peer")?.let { peerObj ->
                peerObj.keys().asSequence().mapNotNull { peerObj.optJSONObject(it)?.let(::parsePeer) }.toList()
            }.orEmpty().sortedWith(compareByDescending<Peer> { it.online }.thenBy { it.displayName.lowercase() })
            val users = HashMap<Long, TsUser>()
            o.optJSONObject("User")?.let { u ->
                u.keys().forEach { k ->
                    val v = u.optJSONObject(k) ?: return@forEach
                    users[k.toLongOrNull() ?: v.optLong("ID")] = TsUser(v.str("LoginName"), v.str("DisplayName"))
                }
            }
            val tailnet = o.optJSONObject("CurrentTailnet")
            return TsStatus(
                backendState = o.str("BackendState"),
                authUrl = o.str("AuthURL"),
                version = o.str("Version").substringBefore('-'),
                self = o.optJSONObject("Self")?.let(::parsePeer),
                peers = peers,
                users = users,
                tailnetName = tailnet?.str("Name").orEmpty(),
                magicDnsSuffix = tailnet?.str("MagicDNSSuffix") ?: o.str("MagicDNSSuffix"),
                magicDnsEnabled = tailnet?.optBoolean("MagicDNSEnabled") ?: false,
                health = o.optJSONArray("Health").strings(),
                exitNodeId = o.optJSONObject("ExitNodeStatus")?.str("ID").orEmpty(),
            )
        }

        internal fun parsePeer(p: JSONObject) = Peer(
            id = p.str("ID"),
            hostName = p.str("HostName"),
            dnsName = p.str("DNSName"),
            os = p.str("OS"),
            ips = p.optJSONArray("TailscaleIPs").strings(),
            online = p.optBoolean("Online"),
            active = p.optBoolean("Active"),
            exitNode = p.optBoolean("ExitNode"),
            exitNodeOption = p.optBoolean("ExitNodeOption"),
            lastSeen = p.str("LastSeen").let { s ->
                runCatching { Instant.parse(s) }.getOrNull()?.takeIf { it.epochSecond > 0 }
            },
            relay = p.str("Relay"),
            curAddr = p.str("CurAddr"),
            rxBytes = p.optLong("RxBytes"),
            txBytes = p.optLong("TxBytes"),
            primaryRoutes = p.optJSONArray("PrimaryRoutes").strings(),
            tags = p.optJSONArray("Tags").strings(),
            userId = p.optLong("UserID"),
        )
    }
}

data class TsPrefs(
    val controlUrl: String = "",
    val routeAll: Boolean = false,
    val corpDns: Boolean = true,
    val runSsh: Boolean = false,
    val shieldsUp: Boolean = false,
    val exitNodeAllowLan: Boolean = false,
    val wantRunning: Boolean = false,
    val advertiseExitNode: Boolean = false,
    val advertiseRoutes: List<String> = emptyList(),
    val hostname: String = "",
    val loggedOut: Boolean = false,
) {
    companion object {
        fun parse(json: String): TsPrefs {
            val o = JSONObject(json)
            val routes = o.optJSONArray("AdvertiseRoutes").strings()
            return TsPrefs(
                controlUrl = o.str("ControlURL"),
                routeAll = o.optBoolean("RouteAll"),
                corpDns = o.optBoolean("CorpDNS", true),
                runSsh = o.optBoolean("RunSSH"),
                shieldsUp = o.optBoolean("ShieldsUp"),
                exitNodeAllowLan = o.optBoolean("ExitNodeAllowLANAccess"),
                wantRunning = o.optBoolean("WantRunning"),
                advertiseExitNode = "0.0.0.0/0" in routes || "::/0" in routes,
                advertiseRoutes = routes.filter { it != "0.0.0.0/0" && it != "::/0" },
                hostname = o.str("Hostname"),
                loggedOut = o.optBoolean("LoggedOut"),
            )
        }
    }
}

enum class RootManager(val label: String) { Magisk("Magisk"), KernelSU("KernelSU"), APatch("APatch"), Unknown("?") }

data class ModuleInfo(
    val installed: Boolean = false,
    val moduleDir: Boolean = false,
    val disabled: Boolean = false,
    val pendingUpdate: Boolean = false,
    val version: String = "",
    val versionCode: Int = 0,
    val autostart: Boolean = true,
    val rootManager: RootManager = RootManager.Unknown,
    val daemonPid: Int? = null,
    /** Other VPN interfaces that are up (tun0, ppp0, wg0, …), excluding tailscale0. */
    val vpnInterfaces: List<String> = emptyList(),
    /** TS_VPN_COEXIST: keep accepted subnet routes ahead of other VPNs. */
    val vpnCoexist: Boolean = true,
    /** Number of subnet route rules currently kept ahead of other VPNs. */
    val coexistRules: Int = 0,
) {
    val vpnActive: Boolean get() = vpnInterfaces.isNotEmpty()

    val daemonRunning: Boolean get() = daemonPid != null
}

internal fun JSONObject.str(key: String): String =
    if (isNull(key)) "" else optString(key, "")

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotEmpty() } }

/** Parses KEY=VALUE lines (as printed by [TailscaleRepository.probe]). */
internal fun parseKv(text: String): Map<String, String> =
    text.lineSequence().mapNotNull { line ->
        val i = line.indexOf('=')
        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
    }.toMap()
