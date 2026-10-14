package io.github.vahiru.tailscaled.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    private val statusJson = Fixtures.STATUS_JSON

    @Test
    fun parsesStatus() {
        val s = TsStatus.parse(statusJson)
        assertTrue(s.running)
        assertFalse(s.needsLogin)
        assertEquals("1.98.8", s.version)
        assertEquals("pixel", s.self?.displayName)
        assertEquals("100.64.0.5", s.self?.ipv4)
        assertEquals("fd7a:115c:a1e0::5", s.self?.ipv6)
        assertNull(s.self?.lastSeen)
        assertEquals("me@example.com", s.selfUser?.loginName)
        assertEquals(listOf("Tailscale DNS is not configured"), s.health)
        assertTrue(s.magicDnsEnabled)

        // Online peers first.
        assertEquals(listOf("nas", "old-laptop"), s.peers.map { it.displayName })
        val nas = s.peers[0]
        assertEquals(listOf("192.168.1.0/24"), nas.subnetRoutes)
        assertEquals("direct", nas.connection)
        assertEquals(listOf("tag:server"), nas.tags)
        assertEquals("nas", s.currentExitNode?.displayName)
        val laptop = s.peers[1]
        assertFalse(laptop.online)
        assertNull(laptop.ipv6)
        assertTrue(laptop.tags.isEmpty())
        assertEquals(1790848800L, laptop.lastSeen?.epochSecond)
    }

    @Test
    fun parsesLoggedOutStatus() {
        val s = TsStatus.parse("""{"BackendState":"NeedsLogin","AuthURL":"https://login.tailscale.com/a/x","Self":null,"Peer":null,"User":null}""")
        assertTrue(s.needsLogin)
        assertNull(s.self)
        assertTrue(s.peers.isEmpty())
        assertEquals("https://login.tailscale.com/a/x", s.authUrl)
    }

    @Test
    fun parsesPrefs() {
        val p = TsPrefs.parse(
            """
            {"ControlURL":"https://headscale.example.com","RouteAll":true,"ExitNodeID":"","CorpDNS":false,
             "RunSSH":true,"WantRunning":true,"LoggedOut":false,"ShieldsUp":false,"ExitNodeAllowLANAccess":true,
             "AdvertiseRoutes":["0.0.0.0/0","::/0","10.0.0.0/8"],"Hostname":"phone"}
            """.trimIndent(),
        )
        assertEquals("https://headscale.example.com", p.controlUrl)
        assertTrue(p.routeAll)
        assertFalse(p.corpDns)
        assertTrue(p.runSsh)
        assertTrue(p.advertiseExitNode)
        assertEquals(listOf("10.0.0.0/8"), p.advertiseRoutes)
        assertTrue(p.exitNodeAllowLan)
        assertEquals("phone", p.hostname)
    }

    @Test
    fun parsesProbeOutput() {
        val kv = parseKv("INSTALLED=1\nVERSION=v3.0.0.0\nPID=\nMGR=KernelSU\nnoise line")
        assertEquals("1", kv["INSTALLED"])
        assertEquals("v3.0.0.0", kv["VERSION"])
        assertEquals("", kv["PID"])
        assertEquals("KernelSU", kv["MGR"])
    }
}
