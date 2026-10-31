package app.duinch.adbagent.features.tunnel.data

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

fun resolveWebRtcSignalEndpoint(serverUrl: String): String {
    var base = serverUrl.trim()
    if (base.startsWith("wss://")) base = "https://" + base.removePrefix("wss://")
    if (base.startsWith("ws://")) base = "http://" + base.removePrefix("ws://")
    if (!base.startsWith("http://") && !base.startsWith("https://")) base = "https://$base"
    val clean = base.substringBefore("/api/tunnel").trimEnd('/')
    return "$clean/api/webrtc/signal"
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
