package app.duinch.adbagent.features.tunnel.domain

import app.duinch.adbagent.core.model.NetworkType
import kotlinx.coroutines.flow.Flow

interface TunnelConfigRepository {
    fun getConfig(): TunnelConfig
    fun saveConfig(config: TunnelConfig)
}

interface ReverseTunnelGateway {
    fun observeConnectionState(): Flow<TunnelConnectionState>
    fun observeActiveNetwork(): Flow<NetworkType>
    fun getLocalWifiIp(): String? = null
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
