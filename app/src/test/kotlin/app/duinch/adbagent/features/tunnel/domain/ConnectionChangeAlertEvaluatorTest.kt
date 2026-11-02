package app.duinch.adbagent.features.tunnel.domain

import app.duinch.adbagent.core.model.NetworkType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionChangeAlertEvaluatorTest {

    @Test
    fun `server cold start emission does not trigger alert`() {
        val alert = ConnectionChangeAlertEvaluator.evaluateServerStateChange(
            previous = null,
            current = TunnelConnectionState.Disconnected
        )
        assertNull(alert)
    }

    @Test
    fun `server connected transition triggers alert`() {
        val alert = ConnectionChangeAlertEvaluator.evaluateServerStateChange(
            previous = TunnelConnectionState.Connecting("http://192.168.1.10:8080"),
            current = TunnelConnectionState.Connected(
                targetUrl = "http://192.168.1.10:8080",
                localAdbPort = 5555,
                networkType = NetworkType.WIFI
            )
        )
        assertEquals("Máy chủ ADB Remote đã kết nối", alert)
    }

    @Test
    fun `server reconnecting transition triggers alert once and suppresses repeated attempts`() {
        val firstAlert = ConnectionChangeAlertEvaluator.evaluateServerStateChange(
            previous = TunnelConnectionState.Connected(
                targetUrl = "http://192.168.1.10:8080",
                localAdbPort = 5555,
                networkType = NetworkType.WIFI
            ),
            current = TunnelConnectionState.Reconnecting(attempt = 1, nextRetryDelayMs = 2000L, reason = "Network lost")
        )
        assertEquals("Mất kết nối máy chủ, đang tự động kết nối lại...", firstAlert)

        val secondAlert = ConnectionChangeAlertEvaluator.evaluateServerStateChange(
            previous = TunnelConnectionState.Reconnecting(attempt = 1, nextRetryDelayMs = 2000L, reason = "Network lost"),
            current = TunnelConnectionState.Reconnecting(attempt = 2, nextRetryDelayMs = 4000L, reason = "Network lost")
        )
        assertNull(secondAlert)
    }

    @Test
    fun `server disconnected from connected triggers alert`() {
        val alert = ConnectionChangeAlertEvaluator.evaluateServerStateChange(
            previous = TunnelConnectionState.Connected(
                targetUrl = "http://192.168.1.10:8080",
                localAdbPort = 5555,
                networkType = NetworkType.WIFI
            ),
            current = TunnelConnectionState.Disconnected
        )
        assertEquals("Đã ngắt kết nối với máy chủ ADB Remote", alert)
    }

    @Test
    fun `server error transition triggers error message`() {
        val alert = ConnectionChangeAlertEvaluator.evaluateServerStateChange(
            previous = TunnelConnectionState.Connecting("http://192.168.1.10:8080"),
            current = TunnelConnectionState.Error("Timeout 5000ms")
        )
        assertEquals("Lỗi kết nối máy chủ: Timeout 5000ms", alert)
    }

    @Test
    fun `usb cable plug and unplug transitions`() {
        // Cold start
        assertNull(ConnectionChangeAlertEvaluator.evaluateUsbChange(null, true))
        assertNull(ConnectionChangeAlertEvaluator.evaluateUsbChange(null, false))

        // Same state
        assertNull(ConnectionChangeAlertEvaluator.evaluateUsbChange(true, true))

        // Plugged
        val plugged = ConnectionChangeAlertEvaluator.evaluateUsbChange(false, true)
        assertEquals("Đã kết nối cáp USB (ADB phần cứng sẵn sàng)", plugged)

        // Unplugged
        val unplugged = ConnectionChangeAlertEvaluator.evaluateUsbChange(true, false)
        assertEquals("Đã ngắt kết nối cáp USB", unplugged)
    }

    @Test
    fun `network transitions evaluate correctly`() {
        // Cold start
        assertNull(ConnectionChangeAlertEvaluator.evaluateNetworkChange(null, NetworkType.WIFI))

        // No change
        assertNull(ConnectionChangeAlertEvaluator.evaluateNetworkChange(NetworkType.WIFI, NetworkType.WIFI))

        // Wi-Fi to None
        val lostNet = ConnectionChangeAlertEvaluator.evaluateNetworkChange(NetworkType.WIFI, NetworkType.NONE)
        assertEquals("Mất kết nối mạng Internet / Wi-Fi", lostNet)

        // None to Wi-Fi
        val regainedWifi = ConnectionChangeAlertEvaluator.evaluateNetworkChange(NetworkType.NONE, NetworkType.WIFI)
        assertEquals("Đã kết nối mạng Wi-Fi", regainedWifi)

        // Wi-Fi to Cellular
        val switchedCellular = ConnectionChangeAlertEvaluator.evaluateNetworkChange(NetworkType.WIFI, NetworkType.CELLULAR)
        assertEquals("Mất Wi-Fi, đã chuyển sang Dữ liệu di động", switchedCellular)

        // Cellular to Wi-Fi
        val switchedWifi = ConnectionChangeAlertEvaluator.evaluateNetworkChange(NetworkType.CELLULAR, NetworkType.WIFI)
        assertEquals("Đã chuyển sang mạng Wi-Fi", switchedWifi)
    }
}
