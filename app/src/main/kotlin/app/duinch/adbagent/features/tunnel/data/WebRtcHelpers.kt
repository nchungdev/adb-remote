package app.duinch.adbagent.features.tunnel.data

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

fun resolveServerBaseUrl(serverUrl: String): String =
    resolveWebRtcSignalEndpoint(serverUrl).removeSuffix("/api/webrtc/signal")

fun resolveWebRtcSignalEndpoint(serverUrl: String): String {
    var base = serverUrl.trim()
    if (base.startsWith("wss://")) base = "https://" + base.removePrefix("wss://")
    if (base.startsWith("ws://")) base = "http://" + base.removePrefix("ws://")
    if (!base.startsWith("http://") && !base.startsWith("https://")) {
        base = if (base.contains("trycloudflare.com")) "https://$base" else "http://$base"
    }
    val clean = base.substringBefore("/api/tunnel").trimEnd('/')
    return "$clean/api/webrtc/signal"
}

fun resolveWebSocketTunnelEndpoint(serverUrl: String): String {
    var base = serverUrl.trim()
    if (base.startsWith("https://")) base = "wss://" + base.removePrefix("https://")
    if (base.startsWith("http://")) base = "ws://" + base.removePrefix("http://")
    if (!base.startsWith("ws://") && !base.startsWith("wss://")) {
        base = if (base.contains("trycloudflare.com")) "wss://$base" else "ws://$base"
    }
    val clean = base.substringBefore("/api/tunnel").substringBefore("/api/webrtc").trimEnd('/')
    return "$clean/api/tunnel"
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(err: String?) {}
    override fun onSetFailure(err: String?) {}
}

open class SimplePeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState) {}
    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {}
    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
    override fun onIceCandidate(candidate: IceCandidate) {}
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
    override fun onAddStream(stream: org.webrtc.MediaStream) {}
    override fun onRemoveStream(stream: org.webrtc.MediaStream) {}
    override fun onDataChannel(dc: org.webrtc.DataChannel) {}
    override fun onRenegotiationNeeded() {}
}

internal fun buildTunnelRtcConfiguration(): PeerConnection.RTCConfiguration {
    val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
    )
    return PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }
}
