package app.duinch.adbagent.features.tunnel.data

import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

internal fun startPeerNegotiation(
    pc: PeerConnection,
    sc: TunnelSignalingChannel
) {
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
