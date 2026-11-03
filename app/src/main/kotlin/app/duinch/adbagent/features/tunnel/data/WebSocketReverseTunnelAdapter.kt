package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.application.ComputeReconnectBackoffUseCase
import app.duinch.adbagent.features.tunnel.domain.DeviceSecretProvider
import app.duinch.adbagent.features.tunnel.domain.HostActivityRepository
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryRepository
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class WebSocketReverseTunnelAdapter(
    context: Context,
    private val computeBackoff: ComputeReconnectBackoffUseCase,
    private val historyRepository: TunnelHistoryRepository,
    private val hostActivityRepository: HostActivityRepository,
    private val secrets: DeviceSecretProvider
) : ReverseTunnelGateway {

    private val appContext = context.applicationContext
    private val networkObserver = NetworkConnectivityObserver(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val connectionState = MutableStateFlow<TunnelConnectionState>(TunnelConnectionState.Disconnected)
    private val activeNetwork = MutableStateFlow(networkObserver.detectCurrentNetwork())

    private val userWantsConnected = AtomicBoolean(false)
    private val bytesBridged = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)
    private val streamBridge = WebSocketStreamBridge(scope, bytesBridged)

    @Volatile private var currentConfig: TunnelConfig? = null
    @Volatile private var activeWebSocket: WebSocket? = null
    @Volatile private var mappedServerPort: Int? = null
    @Volatile private var connectedAtEpochMs: Long = 0L
    @Volatile private var activeSessionId: String? = null
    private var reconnectJob: Job? = null
    private var retryAttempt = 0

    init {
        networkObserver.register { net ->
            activeNetwork.value = net
            if (net == NetworkType.NONE && connectionState.value is TunnelConnectionState.Connected) {
                handleConnectionLoss("Mất kết nối mạng Internet/Wi-Fi")
            }
        }
    }

    override fun observeConnectionState(): Flow<TunnelConnectionState> = connectionState.asStateFlow()
    override fun observeActiveNetwork(): Flow<NetworkType> = activeNetwork.asStateFlow()
    override fun observeUsbConnected(): Flow<Boolean> = networkObserver.observeUsbConnection()
    override fun getLocalWifiIp(): String? = networkObserver.findWifiIpAddress()
    override fun getCurrentWifiSsid(): String? = networkObserver.detectCurrentWifiSsid()
    override fun isUsbConnected(): Boolean = networkObserver.isUsbConnected()

    override suspend fun connect(config: TunnelConfig): Result<Unit> {
        return config.validate().map { valid ->
            userWantsConnected.set(true)
            currentConfig = valid
            retryAttempt = 0
            reconnectJob?.cancel()
            TunnelForegroundService.acquire(appContext, this)
            openWebSocketSession(valid)
        }
    }

    override suspend fun disconnect() {
        userWantsConnected.set(false)
        reconnectJob?.cancel()
        finishHistorySession("Chủ động ngắt kết nối")
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Disconnected
        TunnelForegroundService.release(appContext, this, keepRunning = currentConfig?.runInBackground == true)
    }

    private fun openWebSocketSession(config: TunnelConfig) {
        finishHistorySession("Mở lại kết nối")
        closeActiveSession()
        mappedServerPort = null
        val generation = sessionGeneration.get()
        val targetWsUrl = resolveWebSocketTunnelEndpoint(config.serverUrl)
        connectionState.value = TunnelConnectionState.Connecting(targetWsUrl)

        val request = Request.Builder()
            .url(targetWsUrl)
            .addHeader("X-Device-Alias", config.deviceAlias.ifBlank { "android-device" })
            .addHeader("X-Local-Adb-Port", config.localAdbPort.toString())
            .addHeader("Authorization", "Bearer ${secrets.deviceSecret()}")
            .build()

        activeWebSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != sessionGeneration.get()) return
                retryAttempt = 0
                bytesBridged.set(0L)
                val now = System.currentTimeMillis().also { connectedAtEpochMs = it }
                val sid = UUID.randomUUID().toString().also { activeSessionId = it }
                scope.publishBridgedBytes(bytesBridged, connectionState) { generation == sessionGeneration.get() }
                historyRepository.recordSessionStarted(
                    TunnelHistoryEntry(
                        sessionId = sid,
                        startedAtEpochMs = now,
                        networkType = activeNetwork.value,
                        localAdbPort = config.localAdbPort
                    )
                )
                connectionState.value = TunnelConnectionState.Connected(
                    targetUrl = targetWsUrl,
                    localAdbPort = config.localAdbPort,
                    networkType = activeNetwork.value,
                    connectedAtEpochMs = now
                )
                val ip = networkObserver.findWifiIpAddress().orEmpty()
                val ssid = networkObserver.detectCurrentWifiSsid().orEmpty()
                val hello = TunnelStreamProtocol.buildHelloJson(config.deviceAlias, config.localAdbPort, ip, ssid)
                webSocket.send(hello)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != sessionGeneration.get()) return
                handleTextMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (generation != sessionGeneration.get()) return
                streamBridge.handleBinaryFrame(webSocket, bytes, config.localAdbPort)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation != sessionGeneration.get()) return
                handleConnectionLoss("Đường hầm đóng ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (generation != sessionGeneration.get()) return
                val code = response?.code
                if (code == 401) {
                    handleNotApproved()
                    return
                }
                val reason = if (code != null) "Lỗi máy chủ ($code)" else (t.message ?: "Mất kết nối mạng")
                handleConnectionLoss(reason)
            }
        })
    }

    private fun handleTextMessage(text: String) {
        TunnelStreamProtocol.parsePathRoleIsStandby(text)?.let { standby ->
            val cur = connectionState.value
            if (cur is TunnelConnectionState.Connected) {
                connectionState.value = cur.copy(isStandby = standby)
            }
            return
        }
        TunnelStreamProtocol.parseWelcomeServerInfo(text)?.let { info ->
            mappedServerPort = info.mappedPort
            val cur = connectionState.value
            if (cur is TunnelConnectionState.Connected) {
                connectionState.value = cur.copy(mappedServerPort = info.mappedPort)
            }
            activeSessionId?.let { sid ->
                historyRepository.updateActiveSession(
                    sessionId = sid,
                    mappedServerPort = info.mappedPort,
                    bytesBridged = bytesBridged.get(),
                    networkType = activeNetwork.value
                )
            }
        }
        TunnelStreamProtocol.parseCommandEvent(text)?.let { cmd ->
            hostActivityRepository.recordCommand(cmd)
        }
    }

    private fun handleConnectionLoss(reason: String) {
        if (!userWantsConnected.get()) return
        finishHistorySession(reason)
        closeActiveSession()
        retryAttempt++
        val backoff = computeBackoff(retryAttempt)
        connectionState.value = TunnelConnectionState.Reconnecting(retryAttempt, backoff, reason)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoff)
            currentConfig?.let { openWebSocketSession(it) }
        }
    }

    // Retrying cannot help until the host owner approves this device again.
    private fun handleNotApproved() {
        userWantsConnected.set(false)
        reconnectJob?.cancel()
        finishHistorySession("Thiết bị chưa được duyệt")
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Error("Thiết bị chưa được máy chủ duyệt, hãy kết nối lại để ghép nối")
    }

    private fun finishHistorySession(reason: String) {
        val sid = activeSessionId ?: return
        historyRepository.recordSessionEnded(
            sessionId = sid,
            endedAtEpochMs = System.currentTimeMillis(),
            bytesBridged = bytesBridged.get(),
            reason = reason
        )
        activeSessionId = null
    }

    private fun closeActiveSession() {
        sessionGeneration.incrementAndGet()
        streamBridge.closeAll()
        runCatching { activeWebSocket?.close(1000, "Normal closure") }
        activeWebSocket = null
    }
}
