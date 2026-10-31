package app.duinch.adbagent.features.tunnel.application

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TunnelUseCasesTest {

    private class FakeTunnelConfigRepository(
        private var stored: TunnelConfig = TunnelConfig(deviceAlias = "Galaxy-S24")
    ) : TunnelConfigRepository {
        override fun getConfig(): TunnelConfig = stored
        override fun saveConfig(config: TunnelConfig) {
            stored = config
        }
    }

    private class FakeReverseTunnelGateway : ReverseTunnelGateway {
        val state = MutableStateFlow<TunnelConnectionState>(TunnelConnectionState.Disconnected)
        val network = MutableStateFlow(NetworkType.WIFI)
        var connectCalls = 0
        var disconnectCalls = 0

        override fun observeConnectionState(): Flow<TunnelConnectionState> = state
        override fun observeActiveNetwork(): Flow<NetworkType> = network

        override suspend fun connect(config: TunnelConfig): Result<Unit> {
            connectCalls += 1
            state.value = TunnelConnectionState.Connected(
                targetUrl = config.serverUrl,
                localAdbPort = config.localAdbPort,
                networkType = network.value,
                bytesBridged = 0L
            )
            return Result.success(Unit)
        }

        override suspend fun disconnect() {
            disconnectCalls += 1
            state.value = TunnelConnectionState.Disconnected
        }
    }

    @Test
    fun `rejects non-TLS ws url to enforce zero-trust encrypted transport`() {
        val repo = FakeTunnelConfigRepository()
        val saveUseCase = SaveTunnelConfigUseCase(repo)

        val result = saveUseCase(
            TunnelConfig(
                serverUrl = "ws://adb.duinch.app/api/tunnel",
                deviceAlias = "Pixel-9",
                localAdbPort = 5555
            )
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `toggles reverse tunnel on when disconnected and off when connected`() = runTest {
        val repo = FakeTunnelConfigRepository()
        val gateway = FakeReverseTunnelGateway()
        val toggle = ToggleReverseTunnelUseCase(repo, gateway)

        val connected = toggle(
            TunnelConfig(
                serverUrl = "wss://adb.duinch.app/api/tunnel",
                deviceAlias = "Pixel-9",
                localAdbPort = 39812
            )
        )
        assertTrue(connected.getOrThrow())
        assertEquals(1, gateway.connectCalls)
        assertEquals(39812, repo.getConfig().localAdbPort)

        val disconnected = toggle()
        assertFalse(disconnected.getOrThrow())
        assertEquals(1, gateway.disconnectCalls)
    }

    @Test
    fun `exponential reconnect backoff stays bounded between half base and max delay`() {
        val backoff = ComputeReconnectBackoffUseCase(baseDelayMs = 1_000L, maxDelayMs = 30_000L)
        val rng = Random(42)

        for (attempt in 1..12) {
            val delayMs = backoff(attempt, rng)
            assertTrue(delayMs in 500L..30_000L, "Unexpected delay $delayMs for attempt $attempt")
        }
    }
}
