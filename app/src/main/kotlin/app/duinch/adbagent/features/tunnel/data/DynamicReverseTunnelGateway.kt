package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class DynamicReverseTunnelGateway(
    private val webRtcAdapter: WebRtcReverseTunnelAdapter,
    private val webSocketAdapter: WebSocketReverseTunnelAdapter
) : ReverseTunnelGateway {

    private val currentGateway = MutableStateFlow<ReverseTunnelGateway>(webRtcAdapter)

    override fun observeConnectionState(): Flow<TunnelConnectionState> =
        currentGateway.flatMapLatest { it.observeConnectionState() }

    override fun observeActiveNetwork(): Flow<NetworkType> =
        currentGateway.flatMapLatest { it.observeActiveNetwork() }

    override fun getLocalWifiIp(): String? = currentGateway.value.getLocalWifiIp()
    override fun getCurrentWifiSsid(): String? = currentGateway.value.getCurrentWifiSsid()
    override fun isUsbConnected(): Boolean = currentGateway.value.isUsbConnected()

    override suspend fun connect(config: TunnelConfig): Result<Unit> {
        val target = if (config.tunnelMode == TunnelConfig.MODE_WEBSOCKET ||
            (config.serverUrl.startsWith("wss://") && config.serverUrl.contains("/api/tunnel"))
        ) {
            webSocketAdapter
        } else {
            webRtcAdapter
        }
        currentGateway.value = target
        return target.connect(config)
    }

    override suspend fun disconnect() {
        currentGateway.value.disconnect()
    }
}
