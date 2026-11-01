package app.duinch.adbagent.features.tunnel.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.IceCandidate

interface TunnelSignalingChannel {
    fun start(onSignal: (type: String, payload: JSONObject) -> Unit)
    fun sendOffer(sdp: String)
    fun sendCandidate(candidate: IceCandidate)
    fun close()
}

class HttpTunnelSignalingChannel(
    private val client: WebRtcSignalingClient,
    private val signalUrl: String,
    private val room: String,
    private val peer: String,
    private val scope: CoroutineScope
) : TunnelSignalingChannel {
    private var pollJob: Job? = null

    override fun start(onSignal: (type: String, payload: JSONObject) -> Unit) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val signal = client.pollSignal(signalUrl, room, peer).getOrNull()
                if (signal != null) {
                    val type = signal.optString("type")
                    val payload = signal.optJSONObject("payload")
                    if (type.isNotBlank() && payload != null) {
                        onSignal(type, payload)
                    }
                }
                delay(300L)
            }
        }
    }

    override fun sendOffer(sdp: String) {
        scope.launch {
            val offerObj = JSONObject().apply { put("sdp", sdp) }
            client.sendSignal(signalUrl, room, peer, "offer", offerObj)
        }
    }

    override fun sendCandidate(candidate: IceCandidate) {
        scope.launch {
            val candObj = JSONObject().apply {
                put("candidate", candidate.sdp)
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
            }
            client.sendSignal(signalUrl, room, peer, "candidate", candObj)
        }
    }

    override fun close() {
        pollJob?.cancel()
        pollJob = null
    }
}

class MqttTunnelSignalingChannel(
    roomCode: String,
    brokerHost: String = "broker.hivemq.com"
) : TunnelSignalingChannel {
    private var signalingClient: MqttSignalingClient? = null
    private val room = roomCode
    private val broker = brokerHost

    override fun start(onSignal: (type: String, payload: JSONObject) -> Unit) {
        signalingClient?.close()
        val client = MqttSignalingClient(room, broker, onSignal)
        signalingClient = client
        client.connect()
    }

    override fun sendOffer(sdp: String) {
        val payload = JSONObject().apply { put("sdp", sdp) }
        signalingClient?.send("offer", payload)
    }

    override fun sendCandidate(candidate: IceCandidate) {
        val payload = JSONObject().apply {
            put("candidate", candidate.sdp)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        signalingClient?.send("candidate", payload)
    }

    override fun close() {
        signalingClient?.close()
        signalingClient = null
    }
}
