package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingUiState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class AdbTabVisualsTest {

    @Test
    fun `resolves connected state when tunnel connection is connected`() {
        val state = DashboardUiState(
            connectionState = TunnelConnectionState.Connected(
                targetUrl = "wss://adb.duinch.app",
                localAdbPort = 5555,
                networkType = NetworkType.WIFI
            )
        )
        assertEquals(AdbOverallState.CONNECTED, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves connecting state when tunnel is connecting`() {
        val state = DashboardUiState(
            connectionState = TunnelConnectionState.Connecting(targetUrl = "wss://adb.duinch.app")
        )
        assertEquals(AdbOverallState.CONNECTING, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves reconnecting state when tunnel is reconnecting`() {
        val state = DashboardUiState(
            connectionState = TunnelConnectionState.Reconnecting(
                attempt = 2,
                nextRetryDelayMs = 3000L,
                reason = "Network lost"
            )
        )
        assertEquals(AdbOverallState.RECONNECTING, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves error state when tunnel has error`() {
        val state = DashboardUiState(
            connectionState = TunnelConnectionState.Error("Signaling handshake failed")
        )
        assertEquals(AdbOverallState.ERROR, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves connecting state when pairing is in progress`() {
        val state = DashboardUiState(
            pairing = PairingUiState(pairingState = AdbPairingState.PairingInProgress)
        )
        assertEquals(AdbOverallState.CONNECTING, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves error state when pairing failed`() {
        val state = DashboardUiState(
            pairing = PairingUiState(pairingState = AdbPairingState.Failed("Wrong pairing code"))
        )
        assertEquals(AdbOverallState.ERROR, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves usb active when tunnel is disconnected but usb is plugged in`() {
        val state = DashboardUiState(
            connectionState = TunnelConnectionState.Disconnected,
            isUsbConnected = true
        )
        assertEquals(AdbOverallState.USB_ACTIVE, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves local ready when paired and connected on local port`() {
        val state = DashboardUiState(
            connectionState = TunnelConnectionState.Disconnected,
            isUsbConnected = false,
            pairing = PairingUiState(pairingState = AdbPairingState.PairedAndConnected(connectPort = 5555))
        )
        assertEquals(AdbOverallState.LOCAL_READY, resolveAdbOverallState(state))
    }

    @Test
    fun `resolves disconnected when idle`() {
        val state = DashboardUiState()
        assertEquals(AdbOverallState.DISCONNECTED, resolveAdbOverallState(state))
    }
}
