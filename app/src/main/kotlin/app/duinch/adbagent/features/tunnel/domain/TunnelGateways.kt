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
    suspend fun connect(config: TunnelConfig): Result<Unit>
    suspend fun disconnect()
}
