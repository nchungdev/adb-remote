package app.duinch.adbagent.features.tunnel.application

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.data.SharedPrefsTunnelHistoryRepository
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    @Test
    fun `history entry duration and codec preserve Vietnamese end reason and active flag`() = runTest {
        val activeEntry = TunnelHistoryEntry(
            sessionId = "s-1",
            startedAtEpochMs = 1_000_000L,
            endedAtEpochMs = null,
            networkType = NetworkType.WIFI,
            localAdbPort = 35503,
            mappedServerPort = 15555,
            bytesBridged = 524_288L,
            endReason = "Đang trực tuyến"
        )
        val closedEntry = activeEntry.copy(
            sessionId = "s-0",
            startedAtEpochMs = 900_000L,
            endedAtEpochMs = 965_000L,
            endReason = "Chủ động ngắt kết nối | Wi-Fi"
        )

        assertEquals(42_000L, activeEntry.durationMillis(1_042_000L))
        assertEquals(65_000L, closedEntry.durationMillis(2_000_000L))

        val encoded = SharedPrefsTunnelHistoryRepository.encodeEntries(listOf(activeEntry, closedEntry))
        val decoded = SharedPrefsTunnelHistoryRepository.decodeEntries(encoded)
        assertEquals(2, decoded.size)
        assertTrue(decoded[0].isActive)
        assertFalse(decoded[1].isActive)
        assertEquals("Chủ động ngắt kết nối | Wi-Fi", decoded[1].endReason)
        assertEquals(15555, decoded[0].mappedServerPort)

        val fakeRepo = object : TunnelHistoryRepository {
            val items = MutableStateFlow(decoded)
            override fun observeHistory(): Flow<List<TunnelHistoryEntry>> = items
            override fun recordSessionStarted(entry: TunnelHistoryEntry) = Unit
            override fun updateActiveSession(
                sessionId: String,
                mappedServerPort: Int?,
                bytesBridged: Long,
                networkType: NetworkType
            ) = Unit
            override fun recordSessionEnded(
                sessionId: String,
                endedAtEpochMs: Long,
                bytesBridged: Long,
                reason: String
            ) = Unit
            override fun clearHistory() {
                items.value = items.value.filter { it.isActive }
            }
        }

        ClearTunnelHistoryUseCase(fakeRepo)()
        val remaining = ObserveTunnelHistoryUseCase(fakeRepo)().first()
        assertEquals(1, remaining.size)
        assertEquals("s-1", remaining.single().sessionId)
    }

    @Test
    fun `isAtHomeWifi correctly identifies home Wi-Fi network and respects autoSwitch toggle`() {
        val stateAtHome = app.duinch.adbagent.features.tunnel.presentation.DashboardUiState(
            activeNetwork = NetworkType.WIFI,
            currentWifiSsid = "Chung Home 5G",
            trustedHomeSsidInput = "Chung Home 5G",
            autoSwitchOnHomeWifi = true
        )
        assertTrue(stateAtHome.isAtHomeWifi)

        val stateCaseInsensitive = stateAtHome.copy(trustedHomeSsidInput = "chung home 5g")
        assertTrue(stateCaseInsensitive.isAtHomeWifi)

        val stateDisabled = stateAtHome.copy(autoSwitchOnHomeWifi = false)
        assertFalse(stateDisabled.isAtHomeWifi)

        val stateOtherWifi = stateAtHome.copy(currentWifiSsid = "Coffee-Free-Wifi")
        assertFalse(stateOtherWifi.isAtHomeWifi)

        val stateCellular = stateAtHome.copy(activeNetwork = NetworkType.CELLULAR)
        assertFalse(stateCellular.isAtHomeWifi)
    }
}
