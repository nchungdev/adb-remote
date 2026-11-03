package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.application.ComputeReconnectBackoffUseCase
import app.duinch.adbagent.features.tunnel.domain.HostActivityRepository
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryRepository
import java.util.UUID
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
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription

class WebRtcReverseTunnelAdapter(
    context: Context,
    private val computeBackoff: ComputeReconnectBackoffUseCase,
    private val historyRepository: TunnelHistoryRepository,
    private val hostActivityRepository: HostActivityRepository,
    private val signalingClient: WebRtcSignalingClient = WebRtcSignalingClient()
) : ReverseTunnelGateway {

    private val appContext = context.applicationContext
    private val networkObserver = NetworkConnectivityObserver(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectionState = MutableStateFlow<TunnelConnectionState>(TunnelConnectionState.Disconnected)
    private val activeNetwork = MutableStateFlow(networkObserver.detectCurrentNetwork())

    init {
        networkObserver.register { net ->
            activeNetwork.value = net
            if (net == NetworkType.NONE && connectionState.value is TunnelConnectionState.Connected) {
                handleConnectionLoss("Mất kết nối mạng Internet/Wi-Fi")
            }
        }
    }

    private val userWantsConnected = AtomicBoolean(false)
    private val bytesBridged = AtomicLong(0L)
    private val sessionGeneration = AtomicLong(0L)
    private val connectionId = UUID.randomUUID().toString().take(8)
    private val streamBridge = DataChannelStreamBridge(scope, bytesBridged)

    @Volatile private var currentConfig: TunnelConfig? = null
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var dataChannel: DataChannel? = null
    @Volatile private var signalingChannel: TunnelSignalingChannel? = null
    @Volatile private var activeSessionId: String? = null
    @Volatile private var connectedAtEpochMs: Long = 0L
    @Volatile private var mappedServerPort: Int? = null

    private var reconnectJob: Job? = null
    private var retryAttempt = 0

    private val factory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    override fun observeConnectionState(): Flow<TunnelConnectionState> = connectionState.asStateFlow()
    override fun observeActiveNetwork(): Flow<NetworkType> = activeNetwork.asStateFlow()
    override fun observeUsbConnected(): Flow<Boolean> = networkObserver.observeUsbConnection()
    override fun getLocalWifiIp(): String? = networkObserver.findWifiIpAddress()
    override fun getCurrentWifiSsid(): String? = networkObserver.detectCurrentWifiSsid()
    override fun isUsbConnected(): Boolean = networkObserver.isUsbConnected()

    override suspend fun connect(config: TunnelConfig): Result<Unit> {
        userWantsConnected.set(true)
        currentConfig = config
        retryAttempt = 0
        reconnectJob?.cancel()
        TunnelForegroundService.acquire(appContext, this)
        startWebRtcSession(config)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        userWantsConnected.set(false)
        reconnectJob?.cancel()
        finishHistorySession("Chủ động ngắt kết nối")
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Disconnected
        TunnelForegroundService.release(appContext, this, keepRunning = currentConfig?.runInBackground == true)
    }

    private fun startWebRtcSession(config: TunnelConfig) {
        finishHistorySession("Mở lại kết nối P2P")
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Connecting(config.serverUrl)
        val generation = sessionGeneration.get()

        val room = config.remoteRoomCode.takeIf { it.isNotBlank() }?.replace("-", "")?.trim() ?: "default"
        val peer = config.deviceAlias.ifBlank { "android-device" } + "~" + connectionId

        val signalUrl = resolveWebRtcSignalEndpoint(config.serverUrl)
        val sc: TunnelSignalingChannel = HttpTunnelSignalingChannel(signalingClient, signalUrl, room, peer, scope)
        signalingChannel = sc

        val pc = factory.createPeerConnection(buildTunnelRtcConfiguration(), object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate) {
                sc.sendCandidate(candidate)
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                // DISCONNECTED thường tự hồi phục khi đổi mạng; chỉ FAILED mới là mất kết nối thật.
                if (newState == PeerConnection.IceConnectionState.FAILED) {
                    handleLossAsync(generation, "Mất tín hiệu P2P ($newState)")
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                if (newState == PeerConnection.PeerConnectionState.FAILED) {
                    handleLossAsync(generation, "Kết nối P2P thất bại")
                }
            }
        }) ?: run {
            connectionState.value = TunnelConnectionState.Error("Không thể khởi tạo WebRTC")
            return
        }
        peerConnection = pc

        val dcInit = DataChannel.Init().apply { ordered = true }
        val dc = pc.createDataChannel("adb-stream", dcInit)
        dataChannel = dc

        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                if (generation != sessionGeneration.get()) return
                if (dc.state() == DataChannel.State.OPEN) {
                    retryAttempt = 0
                    bytesBridged.set(0L)
                    val now = System.currentTimeMillis().also { connectedAtEpochMs = it }
                    val sid = UUID.randomUUID().toString().also { activeSessionId = it }
                    historyRepository.recordSessionStarted(
                        TunnelHistoryEntry(
                            sessionId = sid,
                            startedAtEpochMs = now,
                            networkType = activeNetwork.value,
                            localAdbPort = config.localAdbPort
                        )
                    )
                    connectionState.value = TunnelConnectionState.Connected(
                        targetUrl = config.serverUrl,
                        localAdbPort = config.localAdbPort,
                        networkType = activeNetwork.value,
                        connectedAtEpochMs = now
                    )
                    val ip = networkObserver.findWifiIpAddress().orEmpty()
                    val ssid = networkObserver.detectCurrentWifiSsid().orEmpty()
                    val hello = TunnelStreamProtocol.buildHelloJson(config.deviceAlias, config.localAdbPort, ip, ssid)
                    streamBridge.sendText(dc, hello)
                } else if (dc.state() == DataChannel.State.CLOSED) {
                    handleLossAsync(generation, "Kênh truyền P2P đã đóng")
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                if (generation != sessionGeneration.get()) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                if (!buffer.binary) {
                    handleTextMessage(String(bytes, Charsets.UTF_8))
                } else {
                    streamBridge.handleBinaryFrame(dc, bytes.toByteString(), config.localAdbPort)
                }
            }
        })

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        sc.sendOffer(desc.description)
                    }
                }, desc)
            }
        }, MediaConstraints())

        sc.start { type, payload ->
            when (type) {
                "answer" -> {
                    val sdp = payload.optString("sdp").orEmpty()
                    if (sdp.isNotBlank()) {
                        pc.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
                    }
                }
                "candidate" -> {
                    val cand = payload.optString("candidate")
                    val sdpMid = payload.optString("sdpMid")
                    val sdpMLineIndex = payload.optInt("sdpMLineIndex", 0)
                    pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, cand))
                }
            }
        }
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

    // Callback WebRTC chạy trên signaling thread: đóng PeerConnection tại đó dễ gây deadlock.
    private fun handleLossAsync(generation: Long, reason: String) {
        scope.launch {
            if (generation == sessionGeneration.get()) handleConnectionLoss(reason)
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
            currentConfig?.let { startWebRtcSession(it) }
        }
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
        signalingChannel?.close()
        signalingChannel = null
        streamBridge.closeAll()
        runCatching { dataChannel?.close() }
        dataChannel = null
        runCatching { peerConnection?.close() }
        peerConnection = null
    }
}
