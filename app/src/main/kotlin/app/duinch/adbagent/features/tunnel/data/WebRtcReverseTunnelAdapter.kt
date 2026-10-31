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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
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

    private val userWantsConnected = AtomicBoolean(false)
    private val bytesBridged = AtomicLong(0L)
    private val streamBridge = DataChannelStreamBridge(scope, bytesBridged)

    @Volatile private var currentConfig: TunnelConfig? = null
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var dataChannel: DataChannel? = null
    @Volatile private var activeSessionId: String? = null
    @Volatile private var connectedAtEpochMs: Long = 0L
    @Volatile private var mappedServerPort: Int? = null

    private var pollJob: Job? = null
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
    override fun getLocalWifiIp(): String? = networkObserver.findWifiIpAddress()
    override fun getCurrentWifiSsid(): String? = networkObserver.detectCurrentWifiSsid()
    override fun isUsbConnected(): Boolean = networkObserver.isUsbConnected()

    override suspend fun connect(config: TunnelConfig): Result<Unit> {
        userWantsConnected.set(true)
        currentConfig = config
        retryAttempt = 0
        reconnectJob?.cancel()
        TunnelForegroundService.start(appContext)
        startWebRtcSession(config)
        return Result.success(Unit)
    }

    override suspend fun disconnect() {
        userWantsConnected.set(false)
        reconnectJob?.cancel()
        pollJob?.cancel()
        finishHistorySession("Chủ động ngắt kết nối")
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Disconnected
        if (currentConfig?.runInBackground != true) {
            TunnelForegroundService.stop(appContext)
        }
    }

    private fun startWebRtcSession(config: TunnelConfig) {
        finishHistorySession("Mở lại kết nối P2P")
        closeActiveSession()
        connectionState.value = TunnelConnectionState.Connecting(config.serverUrl)

        val signalUrl = resolveWebRtcSignalEndpoint(config.serverUrl)
        val room = "default"
        val peer = config.deviceAlias.ifBlank { "android-client" }

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = factory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate) {
                scope.launch {
                    val candObj = JSONObject().apply {
                        put("candidate", candidate.sdp)
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                    }
                    signalingClient.sendSignal(signalUrl, room, peer, "candidate", candObj)
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                if (newState == PeerConnection.IceConnectionState.DISCONNECTED ||
                    newState == PeerConnection.IceConnectionState.FAILED) {
                    handleConnectionLoss("Mất tín hiệu P2P ($newState)")
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                if (newState == PeerConnection.PeerConnectionState.FAILED) {
                    handleConnectionLoss("Kết nối P2P thất bại")
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
                    handleConnectionLoss("Kênh truyền P2P đã đóng")
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
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
                        scope.launch {
                            val offerObj = JSONObject().apply { put("sdp", desc.description) }
                            signalingClient.sendSignal(signalUrl, room, peer, "offer", offerObj)
                        }
                    }
                }, desc)
            }
        }, MediaConstraints())

        startSignalPolling(signalUrl, room, peer, pc)
    }

    private fun startSignalPolling(signalUrl: String, room: String, peer: String, pc: PeerConnection) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && userWantsConnected.get()) {
                val signal = signalingClient.pollSignal(signalUrl, room, peer).getOrNull()
                if (signal != null) {
                    val type = signal.optString("type")
                    val payload = signal.optJSONObject("payload")
                    when (type) {
                        "answer" -> {
                            val sdp = payload?.optString("sdp").orEmpty()
                            if (sdp.isNotBlank()) {
                                pc.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
                            }
                        }
                        "candidate" -> {
                            if (payload != null) {
                                val cand = payload.optString("candidate")
                                val sdpMid = payload.optString("sdpMid")
                                val sdpMLineIndex = payload.optInt("sdpMLineIndex", 0)
                                pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, cand))
                            }
                        }
                    }
                }
                delay(300L)
            }
        }
    }

    private fun handleTextMessage(text: String) {
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
        pollJob?.cancel()
        streamBridge.closeAll()
        runCatching { dataChannel?.close() }
        dataChannel = null
        runCatching { peerConnection?.close() }
        peerConnection = null
    }
}
