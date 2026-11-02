package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HybridReverseTunnelGateway(
    private val webRtcAdapter: WebRtcReverseTunnelAdapter,
    private val webSocketAdapter: WebSocketReverseTunnelAdapter
) : ReverseTunnelGateway {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val currentGateway = MutableStateFlow<ReverseTunnelGateway>(webRtcAdapter)
    private val isConnecting = AtomicBoolean(false)
    private var fallbackJob: Job? = null
    private var observerJob: Job? = null
    private var lastConfig: TunnelConfig? = null

    override fun observeConnectionState(): Flow<TunnelConnectionState> =
        currentGateway.flatMapLatest { it.observeConnectionState() }

    override fun observeActiveNetwork(): Flow<NetworkType> =
        currentGateway.flatMapLatest { it.observeActiveNetwork() }

    override fun observeUsbConnected(): Flow<Boolean> =
        webRtcAdapter.observeUsbConnected()

    override fun getLocalWifiIp(): String? = currentGateway.value.getLocalWifiIp()
    override fun getCurrentWifiSsid(): String? = currentGateway.value.getCurrentWifiSsid()
    override fun isUsbConnected(): Boolean = currentGateway.value.isUsbConnected()

    override suspend fun connect(config: TunnelConfig): Result<Unit> {
        lastConfig = config
        isConnecting.set(true)
        fallbackJob?.cancel()
        observerJob?.cancel()

        // 1. Reset về WebRTC trước
        currentGateway.value = webRtcAdapter
        val res = webRtcAdapter.connect(config)

        // 2. Lắng nghe trạng thái WebRTC: Nếu lỗi hoặc quá 5s chưa Connected -> Fallback WebSocket
        observerJob = scope.launch {
            webRtcAdapter.observeConnectionState().collect { state ->
                if (!isConnecting.get()) return@collect
                when (state) {
                    is TunnelConnectionState.Connected -> {
                        // WebRTC đã thông suốt, hủy fallback timer
                        fallbackJob?.cancel()
                    }
                    is TunnelConnectionState.Error, is TunnelConnectionState.Reconnecting -> {
                        // Thất bại hoặc retry -> kích hoạt chuyển sang WebSocket ngay lập tức
                        triggerWebSocketFallback(config)
                    }
                    else -> Unit
                }
            }
        }

        // 3. Timer 5s: nếu WebRTC vẫn đang connecting (bị Symmetric NAT treo) -> Tự động chuyển WebSocket
        fallbackJob = scope.launch {
            delay(5000L)
            if (isConnecting.get() && currentGateway.value == webRtcAdapter) {
                val state = webRtcAdapter.observeConnectionState()
                // Nếu chưa Connected, kích hoạt WebSocket
                triggerWebSocketFallback(config)
            }
        }

        return res
    }

    private suspend fun triggerWebSocketFallback(config: TunnelConfig) {
        if (!isConnecting.get() || currentGateway.value == webSocketAdapter) return
        fallbackJob?.cancel()
        observerJob?.cancel()

        // Đóng WebRTC đang treo
        runCatching { webRtcAdapter.disconnect() }

        // Chuyển sang WebSocket
        currentGateway.value = webSocketAdapter
        webSocketAdapter.connect(config)
    }

    override suspend fun disconnect() {
        isConnecting.set(false)
        fallbackJob?.cancel()
        observerJob?.cancel()
        currentGateway.value.disconnect()
        currentGateway.value = webRtcAdapter
    }
}
