package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.data.isLanTargetAddress
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunnelNetworkCoordinatorTest {

    @Test
    fun isLanTargetAddress_identifiesPrivateIpFormats() {
        assertTrue(isLanTargetAddress("192.168.1.16:8080"))
        assertTrue(isLanTargetAddress("http://192.168.1.16:8080"))
        assertTrue(isLanTargetAddress("ws://10.0.0.5:8080"))
        assertTrue(isLanTargetAddress("https://172.16.0.2"))
        assertFalse(isLanTargetAddress("https://tunnel.example.com"))
        assertFalse(isLanTargetAddress("ws://cooperative-club.trycloudflare.com"))
    }

    @Test
    fun isLocalWifiNetwork_matchesOnlyOnWifiWithSameSubnet() {
        assertTrue(isLocalWifiNetwork(NetworkType.WIFI, "192.168.1.50", "192.168.1.16:8080"))
        assertFalse(isLocalWifiNetwork(NetworkType.WIFI, "192.168.2.50", "192.168.1.16:8080"))
        assertFalse(isLocalWifiNetwork(NetworkType.CELLULAR, "192.168.1.50", "192.168.1.16:8080"))
        assertTrue(isLocalWifiNetwork(NetworkType.CELLULAR, "10.10.10.10", "https://tunnel.trycloudflare.com"))
        assertTrue(isLocalWifiNetwork(NetworkType.WIFI, "", "192.168.1.16:8080"))
    }

    @Test
    fun coordinateNetworkRouting_activatesRemoteFallbackWhenOutside() {
        var remoteSlotConnected = false
        var primaryConnected = false

        val state = DashboardUiState(
            hasPrimarySlot = true,
            serverUrlInput = "192.168.1.16:8080",
            primarySlotMode = ConnectMode.Lan,
            primaryAutoConnect = true,
            connectionState = TunnelConnectionState.Disconnected,
            extraSlots = listOf(
                ServerConnectionSlot(
                    id = "slot_remote",
                    serverUrlInput = "https://example.trycloudflare.com",
                    mode = ConnectMode.Remote,
                    autoConnect = true,
                    connectionState = TunnelConnectionState.Disconnected
                )
            )
        )

        coordinateNetworkRouting(
            net = NetworkType.CELLULAR,
            localWifiIp = "10.0.0.1",
            state = state,
            onConnectSlot = { slotId, _ ->
                if (slotId == "slot_remote") remoteSlotConnected = true
            },
            onConnectPrimary = { primaryConnected = true }
        )

        assertTrue(remoteSlotConnected, "Should connect remote slot when on Cellular")
        assertFalse(primaryConnected, "Should not connect LAN primary when on Cellular")
    }

    @Test
    fun coordinateNetworkRouting_restoresLanWhenReturningToLocalWifi() {
        var primaryConnected = false

        val state = DashboardUiState(
            hasPrimarySlot = true,
            serverUrlInput = "192.168.1.16:8080",
            primarySlotMode = ConnectMode.Lan,
            primaryAutoConnect = true,
            connectionState = TunnelConnectionState.Disconnected
        )

        coordinateNetworkRouting(
            net = NetworkType.WIFI,
            localWifiIp = "192.168.1.50",
            state = state,
            onConnectSlot = { _, _ -> },
            onConnectPrimary = { primaryConnected = true }
        )

        assertTrue(primaryConnected, "Should reconnect LAN primary when back on local Wi-Fi")
    }
}
