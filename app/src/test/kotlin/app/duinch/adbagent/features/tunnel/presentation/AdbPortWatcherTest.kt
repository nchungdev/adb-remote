package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import kotlin.test.Test
import kotlin.test.assertEquals

class AdbPortWatcherTest {
    @Test
    fun `an open port means ADB is on, even after an app restart`() {
        assertEquals(AdbPairingState.PairedAndConnected(5555), reconcileAdbState(AdbPairingState.Idle, true, 5555))
    }

    @Test
    fun `a closed port clears a remembered connection, as after a device reboot`() {
        assertEquals(AdbPairingState.Idle, reconcileAdbState(AdbPairingState.PairedAndConnected(5555), false, 5555))
    }

    @Test
    fun `a pairing in progress is left alone`() {
        assertEquals(AdbPairingState.PairingInProgress, reconcileAdbState(AdbPairingState.PairingInProgress, true, 5555))
    }

    @Test
    fun `a failure stays visible while the port is closed`() {
        val failed = AdbPairingState.Failed("x")
        assertEquals(failed, reconcileAdbState(failed, false, 5555))
    }
}

class AdbPortExplanationTest {
    @Test
    fun `command targets the computer side address`() {
        assertEquals("127.0.0.1:15555", computerAdbAddress(15555))
        assertEquals("adb -s 127.0.0.1:15555 shell", computerAdbCommand(15555))
    }
}
