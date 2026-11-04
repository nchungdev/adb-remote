package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import app.duinch.remotetunnel.ui.RemoteConnectionState
import app.duinch.remotetunnel.ui.RemoteTelemetryData
import app.duinch.remotetunnel.ui.RemoteTransportChannel

/** Live card metrics for one server connection, all taken from that connection's own session. */
@Composable
internal fun rememberServerSlotTelemetry(
    connection: TunnelConnectionState,
    mode: ConnectMode,
    serverUrl: String,
    latencyMs: Long?
): RemoteTelemetryData {
    val connected = connection as? TunnelConnectionState.Connected
    val isStandby = connected?.isStandby == true
    val connectedAt = if (!isStandby) (connected?.connectedAtEpochMs ?: 0L) else 0L
    val now by produceConnectedEpochNow(connectedAt)
    val traffic by produceTunnelTrafficMetrics(
        activeConnectedEpoch = connectedAt,
        bridgeBytes = connected?.bytesBridged ?: 0L,
        isRealSocketActive = false
    )
    return RemoteTelemetryData(
        state = when (connection) {
            is TunnelConnectionState.Connecting, is TunnelConnectionState.Reconnecting -> RemoteConnectionState.Connecting
            is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
            else -> RemoteConnectionState.Disconnected
        },
        channel = when {
            mode == ConnectMode.Lan -> RemoteTransportChannel.LanWifi
            serverUrl.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            else -> RemoteTransportChannel.WebRtcDirect
        },
        host = serverUrl,
        pingMs = latencyMs.takeIf { connected != null },
        uptimeSec = if (connectedAt > 0L) ((now - connectedAt) / 1000).coerceAtLeast(0L) else 0L,
        bytesTransferred = if (connected != null) traffic.first else 0L,
        transferSpeedBps = if (connected != null) traffic.second else 0L
    )
}
