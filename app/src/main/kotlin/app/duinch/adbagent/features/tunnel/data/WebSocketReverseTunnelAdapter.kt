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
import kotlinx.coroutines.channels.Channel
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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
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
        .followRedirects(false)
        .followSslRedirects(false)
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val connectionState = MutableStateFlow<TunnelConnectionState>(TunnelConnectionState.Disconnected)
    private val activeNetwork = MutableStateFlow(detectCurrentNetwork())

    private val userWantsConnected = AtomicBoolean(false)
    private val bytesBridged = AtomicLong(0L)
    private val activeStreams = ConcurrentHashMap<Int, Socket>()
    private val streamWriteChannels = ConcurrentHashMap<Int, Channel<ByteArray>>()
    private val streamJobs = ConcurrentHashMap<Int, Job>()

    @Volatile private var currentConfig: TunnelConfig? = null
    @Volatile private var activeWebSocket: WebSocket? = null
    @Volatile private var mappedServerPort: Int? = null
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
        mappedServerPort = null
        connectionState.value = TunnelConnectionState.Connecting(config.serverUrl)

        val requestBuilder = Request.Builder()
            .url(config.serverUrl)
            .addHeader("X-Device-Alias", config.deviceAlias)
            .addHeader("X-Local-Adb-Port", config.localAdbPort.toString())

        if (config.cfAuthCookie.isNotEmpty()) {
            requestBuilder.addHeader("Cookie", config.cfAuthCookie)
        }
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
                    webSocket.send(TunnelStreamProtocol.buildHelloJson(config.deviceAlias, config.localAdbPort))
                    publishConnectedState(config)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    TunnelStreamProtocol.parseWelcomePort(text)?.let { port ->
                        mappedServerPort = port
                        publishConnectedState(config)
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    when (val frame = TunnelStreamProtocol.decode(bytes)) {
                        is TunnelStreamProtocol.Frame.Open -> openLocalAdbStream(webSocket, frame.streamId, config)
                        is TunnelStreamProtocol.Frame.Data -> writeToLocalAdbStream(frame.streamId, frame.payload, config)
                        is TunnelStreamProtocol.Frame.Close -> closeLocalStream(frame.streamId)
                        null -> Unit
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleSessionDropped("Đường hầm đóng ($code)", authBlocked = false)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code
                    if (code in listOf(302, 401, 403)) {
                        handleSessionDropped(
                            "Cần xác thực Cloudflare Access — bấm nút 'Đăng nhập Cloudflare' bên dưới",
                            authBlocked = true
                        )
                    } else {
                        handleSessionDropped(t.message ?: "Mất kết nối mạng", authBlocked = false)
                    }
                }
            }
        )
    }

    private fun openLocalAdbStream(webSocket: WebSocket, streamId: Int, config: TunnelConfig) {
        closeLocalStream(streamId)
        val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
        streamWriteChannels[streamId] = writeQueue

        val job = scope.launch {
            runCatching {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress("127.0.0.1", config.localAdbPort), 5_000)
                activeStreams[streamId] = socket

                val writerJob = launch {
                    runCatching {
                        val out = socket.getOutputStream()
                        for (payload in writeQueue) {
                            out.write(payload)
                            out.flush()
                            updateBytesCount(payload.size.toLong(), config)
                        }
                    }.onFailure {
                        runCatching { socket.close() }
                    }
                }

                val buffer = ByteArray(16 * 1024)
                val input = socket.getInputStream()
                while (isActive && !socket.isClosed) {
                    val readCount = input.read(buffer)
                    if (readCount <= 0) break
                    webSocket.send(TunnelStreamProtocol.encodeData(streamId, buffer, readCount))
                    updateBytesCount(readCount.toLong(), config)
                }
                writeQueue.close()
                writerJob.cancel()
            }
            writeQueue.close()
            streamWriteChannels.remove(streamId)
            activeStreams.remove(streamId)?.let { runCatching { it.close() } }
            streamJobs.remove(streamId)
            runCatching { webSocket.send(TunnelStreamProtocol.encodeClose(streamId)) }
        }
        streamJobs[streamId] = job
    }

    private fun writeToLocalAdbStream(streamId: Int, payload: ByteArray, config: TunnelConfig) {
        val channel = streamWriteChannels[streamId] ?: return
        if (channel.trySend(payload).isFailure) {
            closeLocalStream(streamId)
        }
    }

    private fun closeLocalStream(streamId: Int) {
        streamWriteChannels.remove(streamId)?.close()
        streamJobs.remove(streamId)?.cancel()
        activeStreams.remove(streamId)?.let { runCatching { it.close() } }
    }

    private fun publishConnectedState(config: TunnelConfig) {
        connectionState.value = TunnelConnectionState.Connected(
            targetUrl = config.serverUrl,
            localAdbPort = config.localAdbPort,
            networkType = activeNetwork.value,
            bytesBridged = bytesBridged.get(),
            mappedServerPort = mappedServerPort
        )
    }

    private fun updateBytesCount(delta: Long, config: TunnelConfig) {
        bytesBridged.addAndGet(delta)
        if (connectionState.value is TunnelConnectionState.Connected) {
            publishConnectedState(config)
        }
    }

    private fun handleSessionDropped(reason: String, authBlocked: Boolean) {
        closeActiveSession()
        val cfg = currentConfig
        if (authBlocked) {
            userWantsConnected.set(false)
            TunnelForegroundService.stop(appContext)
            connectionState.value = TunnelConnectionState.Error(reason)
            return
        }
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
            if (userWantsConnected.get()) openWebSocketSession(config)
        }
    }

    private fun closeActiveSession() {
        streamWriteChannels.values.forEach { it.close() }
        streamWriteChannels.clear()
        streamJobs.values.forEach { it.cancel() }
        streamJobs.clear()
        activeStreams.values.forEach { runCatching { it.close() } }
        activeStreams.clear()
        runCatching { activeWebSocket?.close(1000, "Client closing") }
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
