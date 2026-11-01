package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import app.duinch.adbagent.core.designsystem.AdbExtendedColors
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingSection
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.parser.RemoteQrPayloadParser
import app.duinch.remotetunnel.ui.ConnectionTelemetryBar
import app.duinch.remotetunnel.ui.DualChannelConnectContent
import app.duinch.remotetunnel.ui.RemoteConnectContent
import app.duinch.remotetunnel.ui.RemoteConnectionState
import app.duinch.remotetunnel.ui.RemoteTelemetryData
import app.duinch.remotetunnel.ui.RemoteTransportChannel
import kotlinx.coroutines.delay

@Composable
fun TunnelHomeTab(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onPairingIntent: (PairingUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    val clipboard = LocalClipboardManager.current
    val connected = state.connectionState as? TunnelConnectionState.Connected
    val isTunnelOnline = state.connectionState is TunnelConnectionState.Connected

    var showLocalPairing by rememberSaveable { mutableStateOf(false) }

    val activeConnectedEpoch = when {
        connected != null -> connected.connectedAtEpochMs
        state.isUsbConnected -> state.usbConnectedAtEpochMs
        else -> 0L
    }

    val nowEpochMs by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = activeConnectedEpoch
    ) {
        if (activeConnectedEpoch > 0L) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val visual = resolveStatusVisual(state = state, ext = ext)
    val animatedContainer by animateColorAsState(targetValue = visual.container, label = "orbContainer")
    val animatedTint by animateColorAsState(targetValue = visual.tint, label = "orbTint")

    val telemetry = remember(state.connectionState, state.serverUrlInput, state.remoteRoomCode, state.isUsbConnected, nowEpochMs) {
        val connState = when {
            state.connectionState is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
            state.isUsbConnected -> RemoteConnectionState.Connected
            state.connectionState is TunnelConnectionState.Connecting -> RemoteConnectionState.Connecting
            else -> RemoteConnectionState.Disconnected
        }
        val channel = when {
            state.isUsbConnected -> RemoteTransportChannel.UsbCable
            state.selectedMode == AdbConnectionMode.LAN || state.isAtHomeWifi -> RemoteTransportChannel.LanWifi
            state.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            state.remoteRoomCode.isNotBlank() -> RemoteTransportChannel.WebRtcDirect
            else -> RemoteTransportChannel.None
        }
        val ping = when {
            state.isUsbConnected -> (1L..3L).random() // USB direct low latency ping
            else -> null
        }
        val uptimeSec = if (activeConnectedEpoch > 0L && nowEpochMs > activeConnectedEpoch) {
            (nowEpochMs - activeConnectedEpoch) / 1000
        } else {
            null
        }
        RemoteTelemetryData(
            state = connState,
            channel = channel,
            host = if (state.isUsbConnected) "" else state.serverUrlInput,
            roomCode = state.remoteRoomCode,
            pingMs = ping,
            uptimeSec = uptimeSec ?: 0L
        )
    }

    val isWifiConnected = remember(state.activeNetwork, state.localWifiIp) {
        state.activeNetwork == NetworkType.WIFI || state.localWifiIp.isNotBlank()
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Unified Bento Telemetry Bar (chỉ hiển thị khi đang kết nối hoặc đã kết nối)
        if (telemetry.state != RemoteConnectionState.Disconnected) {
            ConnectionTelemetryBar(
                telemetry = telemetry,
                onTriggerConnect = { onIntent(DashboardUiIntent.ScanQrCode) }
            )
        }

        if (state.forceShowPairing) {
            // Explicit local pairing flow when requested from top menu
            PairingSection(
                state = state.pairing,
                onIntent = onPairingIntent,
                onSwitchToConnect = {
                    onIntent(DashboardUiIntent.SetForceShowPairing(false))
                }
            )
        } else if (isTunnelOnline) {
            // Active session switchboard (State 3: Đã kết nối)
            TunnelSwitchboardCard(
                state = state,
                connected = connected,
                animatedContainer = animatedContainer,
                animatedTint = animatedTint,
                icon = visual.icon,
                badgeLabel = visual.badge,
                headline = visual.headline,
                subtitle = visual.subtitle,
                nowEpochMs = nowEpochMs,
                onIntent = onIntent,
                onCopyText = { clipboard.setText(AnnotatedString(it)) }
            )
        } else if (state.connectionState is TunnelConnectionState.Connecting ||
            state.connectionState is TunnelConnectionState.Reconnecting
        ) {
            // Transition switchboard (State 2: Đang kết nối)
            AdbConnectingCard(
                state = state,
                onCancel = { onIntent(DashboardUiIntent.ToggleTunnel) }
            )
        } else {
            // Unified Dual-Channel Profile Card (Cùng mạng LAN & Khác mạng Remote P2P song song)
            DualChannelConnectContent(
                initialLanAddress = state.serverUrlInput,
                initialRemoteTarget = state.remoteRoomCode,
                telemetry = telemetry,
                isUsbConnected = state.isUsbConnected,
                isWifiConnected = isWifiConnected,
                serverRemoteUrl = state.serverQuickTunnelUrl,
                isServerRemoteActive = state.isServerRemoteActive,
                onConnect = { lan, remote ->
                    onIntent(DashboardUiIntent.ApplyQrRemoteConfig(roomCode = remote, serverUrl = lan))
                },
                onDisconnect = { onIntent(DashboardUiIntent.ToggleTunnel) },
                onScanQr = { onIntent(DashboardUiIntent.ScanQrCode) }
            )

            // Collapsible Local Device Debugging Card (Android 11+ Pairing) - Flat secondary tile
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showLocalPairing = !showLocalPairing },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = AppIcons.LinkWireless,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Ghép đôi không dây (Android 11+)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (showLocalPairing) AppIcons.ChevronDown else AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (showLocalPairing) {
                PairingSection(
                    state = state.pairing,
                    onIntent = onPairingIntent,
                    onSwitchToConnect = { showLocalPairing = false }
                )
            }
        }
    }
}
