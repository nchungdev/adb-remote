package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import app.duinch.remotetunnel.ui.ServerCardTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionExpansionTest {
    private fun connected(standby: Boolean = false) = TunnelConnectionState.Connected(
        targetUrl = "u", localAdbPort = 5555, networkType = NetworkType.WIFI, isStandby = standby
    )

    @Test
    fun `serving path is active, standby path is not`() {
        val connections = listOf("primary" to connected(standby = true), "slot-2" to connected())
        assertEquals("slot-2", activeConnectionId(connections))
    }

    @Test
    fun `a connection being set up takes priority`() {
        val connections = listOf("primary" to connected(), "slot-2" to TunnelConnectionState.Connecting("u"))
        assertEquals("slot-2", activeConnectionId(connections))
    }

    @Test
    fun `active card is expanded by default`() {
        assertEquals("primary", resolveExpandedConnection(null, "primary", listOf("primary", "slot-2")))
    }

    @Test
    fun `with nothing active the newest card is expanded`() {
        assertEquals("slot-2", resolveExpandedConnection(null, null, listOf("primary", "slot-2")))
    }

    @Test
    fun `a tap overrides the default, and a removed card falls back`() {
        val state = ConnectionExpansionState()
        state.toggle("slot-2", currentlyExpanded = "primary")
        assertEquals("slot-2", resolveExpandedConnection(state.userChoice, "primary", listOf("primary", "slot-2")))
        assertEquals("primary", resolveExpandedConnection(state.userChoice, "primary", listOf("primary")))
    }

    @Test
    fun `tapping the open card collapses everything`() {
        val state = ConnectionExpansionState()
        state.toggle("primary", currentlyExpanded = "primary")
        assertNull(resolveExpandedConnection(state.userChoice, "primary", listOf("primary")))
    }

    @Test
    fun `each connection state has its own tone`() {
        assertEquals(ServerCardTone.Running, connectionTone(connected()))
        assertEquals(ServerCardTone.Standby, connectionTone(connected(standby = true)))
        assertEquals(ServerCardTone.Connecting, connectionTone(TunnelConnectionState.Connecting("u")))
        assertEquals(ServerCardTone.Connecting, connectionTone(TunnelConnectionState.Reconnecting(1, 1000L, "x")))
        assertEquals(ServerCardTone.Error, connectionTone(TunnelConnectionState.Error("x")))
        assertEquals(ServerCardTone.Neutral, connectionTone(TunnelConnectionState.Disconnected))
    }

    @Test
    fun `card type follows the address`() {
        assertEquals(ConnectMode.Lan, connectModeForServer("192.168.1.16:8080"))
        assertEquals(ConnectMode.Lan, connectModeForServer("http://192.168.1.16:8080"))
        assertEquals(ConnectMode.Remote, connectModeForServer("https://a.trycloudflare.com"))
        assertEquals(ConnectMode.Remote, connectModeForServer("a.trycloudflare.com"))
    }

    @Test
    fun `standby cards do not run or show uptime`() {
        assertNull(connectionNote(connected(standby = true), 192L))
        assertNull(connectionNote(connected(standby = true), 3605L))
        assertEquals("00:03:12", connectionNote(connected(), 192L))
        assertEquals("Lỗi", connectionNote(TunnelConnectionState.Error("x"), 0L))
        assertNull(connectionNote(TunnelConnectionState.Disconnected, 0L))
    }
}
