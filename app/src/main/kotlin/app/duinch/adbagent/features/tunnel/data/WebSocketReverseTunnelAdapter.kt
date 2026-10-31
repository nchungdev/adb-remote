package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.application.ComputeReconnectBackoffUseCase
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class WebSocketReverseTunnelAdapter(
    context: Context,
    private val computeBackoff: ComputeReconnectBackoffUseCase
) : ReverseTunnelGateway {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val connectionState = MutableStateFlow<TunnelConnectionState>(TunnelConnectionState.Disconnected)
    private val activeNetwork = MutableStateFlow(detectCurrentNetwork())

    private val userWantsConnected = AtomicBoolean(false)
    private val bytesBridged = AtomicLong(0L)
    private var currentConfig: TunnelConfig? = null
    private var activeWebSocket: WebSocket? = null
    private var activeLocalSocket: Socket? = null
    private var bridgeReaderJob: Job? = null
    private var reconnectJob: Job? = null
    private var retryAttempt = 0

    init {
        registerNetworkCallback()
    }

    override fun observeConnectionState(): Flow<TunnelConnectionState> = connectionState.asStateFlow()

    override fun observeActiveNetwork(): Flow<NetworkType> = activeNetwork.asStateFlow()

    override suspend fun connect(config: TunnelConfig): Result<Unit> {
        return config.validate().map { valid ->
            userWantsConnected.set(true)
            currentConfig = valid
            retryAttempt = 0
            reconnectJob?.cancel()
            TunnelForegroundService.start(appContext)
            openWebSocketSession(valid)
        }
    }

    override suspend fun disconnect() {
        userWantsConnected.set(false)
        reconnectJob?.cancel()
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Disconnected
        TunnelForegroundService.stop(appContext)
    }

    private fun openWebSocketSession(config: TunnelConfig) {
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Connecting(config.serverUrl)

        val requestBuilder = Request.Builder()
            .url(config.serverUrl)
            .addHeader("X-Device-Alias", config.deviceAlias)
            .addHeader("X-Local-Adb-Port", config.localAdbPort.toString())

        if (config.cfAccessClientId.isNotEmpty() && config.cfAccessClientSecret.isNotEmpty()) {
            requestBuilder.addHeader("CF-Access-Client-Id", config.cfAccessClientId)
            requestBuilder.addHeader("CF-Access-Client-Secret", config.cfAccessClientSecret)
        }

        activeWebSocket = httpClient.newWebSocket(
            requestBuilder.build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    retryAttempt = 0
                    bytesBridged.set(0L)
                    connectionState.value = TunnelConnectionState.Connected(
                        targetUrl = config.serverUrl,
                        localAdbPort = config.localAdbPort,
                        networkType = activeNetwork.value,
                        bytesBridged = 0L
                    )
                    attachLocalAdbSocket(webSocket, config)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    forwardToLocalAdb(bytes)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleSessionDropped("Đường hầm đóng ($code)")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleSessionDropped(t.message ?: "Mất kết nối mạng")
                }
            }
        )
    }

    private fun attachLocalAdbSocket(webSocket: WebSocket, config: TunnelConfig) {
        bridgeReaderJob?.cancel()
        bridgeReaderJob = scope.launch {
            runCatching {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", config.localAdbPort), 5_000)
                activeLocalSocket = socket
                val buffer = ByteArray(16 * 1024)
                val input = socket.getInputStream()
                while (isActive && !socket.isClosed) {
                    val readCount = input.read(buffer)
                    if (readCount <= 0) break
                    webSocket.send(buffer.toByteString(0, readCount))
                    updateBytesCount(readCount.toLong(), config)
                }
            }
        }
    }

    private fun forwardToLocalAdb(bytes: ByteString) {
        val config = currentConfig ?: return
        scope.launch {
            runCatching {
                val out = activeLocalSocket?.getOutputStream() ?: return@runCatching
                out.write(bytes.toByteArray())
                out.flush()
                updateBytesCount(bytes.size.toLong(), config)
            }
        }
    }

    private fun updateBytesCount(delta: Long, config: TunnelConfig) {
        val total = bytesBridged.addAndGet(delta)
        if (connectionState.value is TunnelConnectionState.Connected) {
            connectionState.value = TunnelConnectionState.Connected(
                targetUrl = config.serverUrl,
                localAdbPort = config.localAdbPort,
                networkType = activeNetwork.value,
                bytesBridged = total
            )
        }
    }

    private fun handleSessionDropped(reason: String) {
        closeActiveSession()
        val cfg = currentConfig
        if (!userWantsConnected.get() || cfg == null || !cfg.autoReconnectOnNetworkChange) {
            connectionState.value = TunnelConnectionState.Error(reason)
            return
        }
        scheduleReconnect(cfg, reason)
    }

    private fun scheduleReconnect(config: TunnelConfig, reason: String) {
        reconnectJob?.cancel()
        retryAttempt += 1
        val delayMs = computeBackoff(retryAttempt)
        connectionState.value = TunnelConnectionState.Reconnecting(
            attempt = retryAttempt,
            nextRetryDelayMs = delayMs,
            reason = reason
        )
        reconnectJob = scope.launch {
            delay(delayMs)
            if (userWantsConnected.get()) {
                openWebSocketSession(config)
            }
        }
    }

    private fun closeActiveSession() {
        bridgeReaderJob?.cancel()
        runCatching { activeLocalSocket?.close() }
        runCatching { activeWebSocket?.close(1000, "Client closing") }
        activeLocalSocket = null
        activeWebSocket = null
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    activeNetwork.value = detectCurrentNetwork()
                    val cfg = currentConfig
                    if (userWantsConnected.get() && cfg != null && cfg.autoReconnectOnNetworkChange) {
                        retryAttempt = 0
                        reconnectJob?.cancel()
                        openWebSocketSession(cfg)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    activeNetwork.value = mapCapabilities(caps)
                }

                override fun onLost(network: Network) {
                    activeNetwork.value = detectCurrentNetwork()
                }
            })
        }
    }

    private fun detectCurrentNetwork(): NetworkType {
        val cm = connectivityManager ?: return NetworkType.UNKNOWN
        val net = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkType.NONE
        return mapCapabilities(caps)
    }

    private fun mapCapabilities(caps: NetworkCapabilities): NetworkType = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
        else -> NetworkType.UNKNOWN
    }
}
