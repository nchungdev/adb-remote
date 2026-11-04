package app.duinch.adbagent.features.pairing.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PairFailureMessageTest {
    private val startup = "* daemon not running; starting now at tcp:5038\n* daemon started successfully\n"

    @Test
    fun `protocol fault becomes an actionable message`() {
        val output = startup + "error: protocol fault (couldn't read status message): Success"
        assertEquals("Mã chưa đúng hoặc cổng ghép nối đã đóng", describePairFailure(output, 41773))
    }

    @Test
    fun `daemon start up lines are never shown as the error`() {
        assertEquals("error: device offline", withoutDaemonNoise(startup + "error: device offline"))
    }

    @Test
    fun `other failures keep their detail`() {
        assertEquals("Failed: boom", describePairFailure(startup + "Failed: boom", 1))
    }

    @Test
    fun `empty output names the port`() {
        assertEquals("Không thể ghép đôi với 127.0.0.1:41773", describePairFailure(startup, 41773))
    }
}
