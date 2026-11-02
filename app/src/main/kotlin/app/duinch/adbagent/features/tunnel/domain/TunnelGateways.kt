package app.duinch.adbagent.features.tunnel.domain

import app.duinch.adbagent.core.model.NetworkType
import kotlinx.coroutines.flow.Flow

interface TunnelConfigRepository {
    fun getConfig(): TunnelConfig
    fun saveConfig(config: TunnelConfig)
    fun hasSeenOnboarding(): Boolean = true
    fun setHasSeenOnboarding(seen: Boolean) {}
}

interface ReverseTunnelGateway {
    fun observeConnectionState(): Flow<TunnelConnectionState>
    fun observeActiveNetwork(): Flow<NetworkType>
    fun observeUsbConnected(): Flow<Boolean> = kotlinx.coroutines.flow.emptyFlow()
    fun getLocalWifiIp(): String? = null
    fun getCurrentWifiSsid(): String? = null
    fun isUsbConnected(): Boolean = false
    suspend fun connect(config: TunnelConfig): Result<Unit>
    suspend fun disconnect()
}

interface TunnelHistoryRepository {
    fun observeHistory(): Flow<List<TunnelHistoryEntry>>
    fun recordSessionStarted(entry: TunnelHistoryEntry)
    fun updateActiveSession(
        sessionId: String,
        mappedServerPort: Int?,
        bytesBridged: Long,
        networkType: NetworkType
    )
    fun recordSessionEnded(
        sessionId: String,
        endedAtEpochMs: Long,
        bytesBridged: Long,
        reason: String
    )
    fun clearHistory()
}
