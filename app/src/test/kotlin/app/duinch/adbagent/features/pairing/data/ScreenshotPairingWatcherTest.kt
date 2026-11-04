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
        val port = ScreenshotPairingWatcher.parsePairingPortFromOcrText(sampleOcrText)
        assertEquals("729401", code)
        assertEquals(37599, port)
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
        val port = ScreenshotPairingWatcher.parsePairingPortFromOcrText(sampleSpacedText)
        assertEquals("491823", code)
        assertEquals(41029, port)
    }

    @Test
    fun `returns null if no 6 digit code found`() {
        val sampleInvalid = "Cài đặt Wi-Fi không có mã"
        val code = ScreenshotPairingWatcher.parsePairingCodeFromOcrText(sampleInvalid)
        val port = ScreenshotPairingWatcher.parsePairingPortFromOcrText(sampleInvalid)
        assertNull(code)
        assertNull(port)
    }

    @Test
    fun `picks the pairing port in the dialog, not the connect port on the page behind it`() {
        val text = """
            Wireless debugging
            On
            Device name
            S24+
            IP address & Port
            192.168.1.9:39171
            Pair device with pairing code
            Pair with device
            Wi-Fi pairing code
            438802
            IP address & Port
            192.168.1.9:41773
            Cancel
        """.trimIndent()

        val code = ScreenshotPairingWatcher.parsePairingCodeFromOcrText(text)
        assertEquals("438802", code)
        assertEquals(41773, ScreenshotPairingWatcher.parsePairingPortFromOcrText(text, code))
    }

    @Test
    fun `falls back to the last address when OCR reads the code after both addresses`() {
        val text = "192.168.1.9:39171\n192.168.1.9:41773\n438802"
        assertEquals(41773, ScreenshotPairingWatcher.parsePairingPortFromOcrText(text, "438802"))
    }

    @Test
    fun `prefers a code alone on its line over digits inside other text`() {
        assertEquals("438802", ScreenshotPairingWatcher.parsePairingCodeFromOcrText("Build 123456 ready\n438802\n"))
    }
}
