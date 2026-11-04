package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerGroupsTest {
    private val mac = ServerIdentity("a", "Mac")
    private val nas = ServerIdentity("b", "NAS")

    @Test
    fun `a LAN and a tunnel card of one server share a group`() {
        val groups = groupByServer(listOf("primary" to mac, "slot-2" to nas, "slot-3" to mac))
        assertEquals(listOf(listOf("primary", "slot-3"), listOf("slot-2")), groups.map { it.connectionIds })
        assertEquals(listOf(mac, nas), groups.map { it.server })
    }

    @Test
    fun `reordered connections inside a group preserve their sorted sequence`() {
        val reordered = listOf("slot-3" to mac, "primary" to mac)
        val groups = groupByServer(reordered)
        assertEquals(listOf(listOf("slot-3", "primary")), groups.map { it.connectionIds })
    }

    @Test
    fun `cards no server has answered yet go last, each on its own`() {
        val groups = groupByServer(listOf("slot-2" to null, "primary" to mac, "slot-3" to null))
        assertEquals(listOf(mac, null, null), groups.map { it.server })
        assertEquals(listOf(listOf("primary"), listOf("slot-2"), listOf("slot-3")), groups.map { it.connectionIds })
    }

    @Test
    fun `no cards means no groups`() {
        assertEquals(emptyList(), groupByServer(emptyList()))
    }

    private fun connected(standby: Boolean) =
        TunnelConnectionState.Connected("u", 5555, NetworkType.WIFI, isStandby = standby)

    private fun card(id: String, mode: ConnectMode, state: TunnelConnectionState) = GroupedConnection(id, mode, state, "u")

    @Test
    fun `the summary names the path in use, not the standby`() {
        val summary = serverGroupSummary(
            listOf(card("a", ConnectMode.Remote, connected(standby = true)), card("b", ConnectMode.Lan, connected(standby = false)))
        )
        assertEquals("2 kết nối • Đang dùng Wi-Fi", summary)
    }

    @Test
    fun `a lone connection shows only its state`() {
        assertEquals("Chưa kết nối", serverGroupSummary(listOf(card("a", ConnectMode.Lan, TunnelConnectionState.Disconnected))))
        assertEquals("Đang kết nối...", serverGroupSummary(listOf(card("a", ConnectMode.Remote, TunnelConnectionState.Connecting("u")))))
        assertEquals("Đang dùng từ xa", serverGroupSummary(listOf(card("a", ConnectMode.Remote, connected(standby = false)))))
    }

    @Test
    fun `the metrics follow the connection in use, then any live one`() {
        val standby = card("a", ConnectMode.Remote, connected(standby = true))
        val active = card("b", ConnectMode.Lan, connected(standby = false))
        val off = card("c", ConnectMode.Lan, TunnelConnectionState.Disconnected)
        assertEquals("b", liveConnection(listOf(off, standby, active))?.id)
        assertEquals("a", liveConnection(listOf(off, standby))?.id)
        assertNull(liveConnection(listOf(off)))
    }

    @Test
    fun `allConnectionsBelongToOneServer is true only when all have the same server identity`() {
        assertTrue(allConnectionsBelongToOneServer(listOf("primary" to mac, "slot-2" to mac)))
        assertTrue(allConnectionsBelongToOneServer(listOf("primary" to mac)))
        assertFalse(allConnectionsBelongToOneServer(listOf("primary" to mac, "slot-2" to nas)))
        assertFalse(allConnectionsBelongToOneServer(listOf("primary" to mac, "slot-2" to null)))
        assertFalse(allConnectionsBelongToOneServer(listOf("primary" to null, "slot-2" to null)))
        assertFalse(allConnectionsBelongToOneServer(emptyList()))
    }

    @Test
    fun `reordering one server leaves the others in place`() {
        assertEquals(
            listOf("a2", "b1", "a1", "b2"),
            mergeGroupOrder(listOf("a1", "b1", "a2", "b2"), listOf("a2", "a1"))
        )
    }

    @Test
    fun `a card missing from the saved order is added at the end`() {
        assertEquals(listOf("b", "a", "new"), mergeGroupOrder(listOf("a", "b"), listOf("b", "a", "new")))
    }
}
