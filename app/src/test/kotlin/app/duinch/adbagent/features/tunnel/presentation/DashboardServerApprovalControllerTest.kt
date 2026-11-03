package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.ServerApprovalGateway
import app.duinch.adbagent.features.tunnel.domain.ServerApprovalStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardServerApprovalControllerTest {

    private class FakeGateway(var status: Result<ServerApprovalStatus>) : ServerApprovalGateway {
        val requests = mutableListOf<String>()
        override suspend fun checkApproval(serverUrl: String) = status
        override suspend fun requestApproval(serverUrl: String, deviceName: String, code: String): Result<Unit> {
            requests += code
            return Result.success(Unit)
        }
        override suspend fun measureLatencyMs(serverUrl: String): Long? = null
    }

    private val uiState = MutableStateFlow(DashboardUiState())
    private val effects = MutableSharedFlow<DashboardUiEffect>(extraBufferCapacity = 8)
    private var connected = false

    private fun TestScope.controller(gateway: FakeGateway) = DashboardServerApprovalController(
        gateway = gateway,
        scope = backgroundScope,
        uiState = uiState,
        effects = effects,
        newCode = { "123-456" }
    )

    @Test
    fun `already approved device connects without asking`() = runTest {
        val gateway = FakeGateway(Result.success(ServerApprovalStatus.APPROVED))
        controller(gateway).connectWhenApproved("server", "phone") { connected = true }
        runCurrent()

        assertTrue(connected)
        assertTrue(gateway.requests.isEmpty())
        assertNull(uiState.value.serverApprovalCode)
    }

    @Test
    fun `new device stays disconnected until the host approves`() = runTest {
        val gateway = FakeGateway(Result.success(ServerApprovalStatus.NOT_REQUESTED))
        controller(gateway).connectWhenApproved("server", "phone") { connected = true }
        runCurrent()

        assertFalse(connected)
        assertEquals(listOf("123-456"), gateway.requests)
        assertEquals("123-456", uiState.value.serverApprovalCode)

        gateway.status = Result.success(ServerApprovalStatus.PENDING)
        advanceTimeBy(10_000L)
        runCurrent()
        assertFalse(connected)

        gateway.status = Result.success(ServerApprovalStatus.APPROVED)
        advanceTimeBy(2_001L)
        runCurrent()
        assertTrue(connected)
        assertNull(uiState.value.serverApprovalCode)
    }

    @Test
    fun `rejection never connects`() = runTest {
        val gateway = FakeGateway(Result.success(ServerApprovalStatus.NOT_REQUESTED))
        controller(gateway).connectWhenApproved("server", "phone") { connected = true }
        runCurrent()

        gateway.status = Result.success(ServerApprovalStatus.REJECTED)
        advanceTimeBy(2_001L)
        runCurrent()

        assertFalse(connected)
        assertNull(uiState.value.serverApprovalCode)
    }

    @Test
    fun `cancelling closes the prompt and never connects`() = runTest {
        val gateway = FakeGateway(Result.success(ServerApprovalStatus.NOT_REQUESTED))
        val controller = controller(gateway)
        controller.connectWhenApproved("server", "phone") { connected = true }
        runCurrent()

        controller.cancel()
        gateway.status = Result.success(ServerApprovalStatus.APPROVED)
        advanceTimeBy(10_000L)
        runCurrent()

        assertFalse(connected)
        assertNull(uiState.value.serverApprovalCode)
    }

    @Test
    fun `timeout never connects`() = runTest {
        val gateway = FakeGateway(Result.success(ServerApprovalStatus.PENDING))
        controller(gateway).connectWhenApproved("server", "phone") { connected = true }
        advanceTimeBy(171_000L)
        runCurrent()

        assertFalse(connected)
        assertNull(uiState.value.serverApprovalCode)
    }

    @Test
    fun `unreachable server falls through so the tunnel reports the failure`() = runTest {
        val gateway = FakeGateway(Result.failure(IllegalStateException("offline")))
        controller(gateway).connectWhenApproved("server", "phone") { connected = true }
        runCurrent()

        assertTrue(connected)
        assertTrue(gateway.requests.isEmpty())
    }
}

class ChooseReachableServerTest {
    private class ProbeGateway(private val reachable: Set<String>) : ServerApprovalGateway {
        val probed = mutableListOf<String>()
        override suspend fun checkApproval(serverUrl: String) = Result.success(ServerApprovalStatus.APPROVED)
        override suspend fun requestApproval(serverUrl: String, deviceName: String, code: String) = Result.success(Unit)
        override suspend fun measureLatencyMs(serverUrl: String): Long? {
            probed += serverUrl
            return if (serverUrl in reachable) 1L else null
        }
    }

    private val lan = "10.0.0.2:8080"
    private val tunnel = "https://a.trycloudflare.com"

    @Test
    fun `away from home on mobile data uses the tunnel without probing`() = runTest {
        val gateway = ProbeGateway(reachable = setOf(tunnel))
        assertEquals(tunnel, chooseReachableServer(gateway, isOnWifi = false, preferred = lan, fallback = tunnel))
        assertTrue(gateway.probed.isEmpty())
    }

    @Test
    fun `on another Wi-Fi the unreachable LAN address falls back to the tunnel`() = runTest {
        val gateway = ProbeGateway(reachable = setOf(tunnel))
        assertEquals(tunnel, chooseReachableServer(gateway, isOnWifi = true, preferred = lan, fallback = tunnel))
    }

    @Test
    fun `on the host's Wi-Fi prefers the LAN address`() = runTest {
        val gateway = ProbeGateway(reachable = setOf(lan, tunnel))
        assertEquals(lan, chooseReachableServer(gateway, isOnWifi = true, preferred = lan, fallback = tunnel))
    }

    @Test
    fun `without a tunnel the LAN address is used as is`() = runTest {
        assertEquals(lan, chooseReachableServer(ProbeGateway(emptySet()), isOnWifi = false, preferred = lan, fallback = null))
    }
}
