package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlin.test.Test
import kotlin.test.assertEquals

class UsbLinkTest {
    @Test
    fun `the server that recognises the phone owns the cable`() {
        assertEquals(UsbLinkResult("b", true), resolveUsbLink(listOf("a" to false, "b" to true)))
    }

    @Test
    fun `a server that denies it proves the cable goes elsewhere`() {
        assertEquals(UsbLinkResult(null, true), resolveUsbLink(listOf("a" to false)))
    }

    @Test
    fun `no answer proves nothing`() {
        assertEquals(UsbLinkResult(null, false), resolveUsbLink(listOf("a" to null)))
        assertEquals(UsbLinkResult(null, false), resolveUsbLink(emptyList()))
    }

    @Test
    fun `each server is asked once, through a live connection`() {
        val mac = ServerIdentity("a", "Mac")
        fun connected(server: ServerIdentity?) =
            TunnelConnectionState.Connected("u", 5555, NetworkType.WIFI, server = server)
        val state = DashboardUiState(
            serverUrlInput = "lan",
            connectionState = connected(mac),
            extraSlots = listOf(
                ServerConnectionSlot("tunnel", serverUrlInput = "tunnel", connectionState = connected(mac)),
                ServerConnectionSlot("off", serverUrlInput = "off", server = ServerIdentity("b", "NAS"))
            )
        )
        assertEquals(mapOf(mac to "lan"), usbLinkTargets(state))
    }
}
