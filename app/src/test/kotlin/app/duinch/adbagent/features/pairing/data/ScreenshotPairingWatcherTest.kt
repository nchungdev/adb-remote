package app.duinch.adbagent.features.pairing.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenshotPairingWatcherTest {

    @Test
    fun `extracts 6 digit pairing code from system dialog text`() {
        val sampleOcrText = """
            Ghép nối với thiết bị
            Mã ghép nối Wi-Fi
            729401
            Địa chỉ IP và cổng
            192.168.1.9:37599
            HỦY
        """.trimIndent()

        val code = ScreenshotPairingWatcher.parsePairingCodeFromOcrText(sampleOcrText)
        assertEquals("729401", code)
    }

    @Test
    fun `extracts spaced 6 digit pairing code`() {
        val sampleSpacedText = """
            Pair with pairing code
            Wi-Fi pairing code
            491 823
            IP address & Port
            192.168.1.15:41029
        """.trimIndent()

        val code = ScreenshotPairingWatcher.parsePairingCodeFromOcrText(sampleSpacedText)
        assertEquals("491823", code)
    }

    @Test
    fun `returns null if no 6 digit code found`() {
        val sampleInvalid = "Cài đặt Wi-Fi không có mã"
        val code = ScreenshotPairingWatcher.parsePairingCodeFromOcrText(sampleInvalid)
        assertNull(code)
    }
}
