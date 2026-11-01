package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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

    val nowEpochMs by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = connected?.connectedAtEpochMs
    ) {
        if (connected != null) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val visual = resolveStatusVisual(state = state, ext = ext)
    val animatedContainer by animateColorAsState(targetValue = visual.container, label = "orbContainer")
    val animatedTint by animateColorAsState(targetValue = visual.tint, label = "orbTint")

    val telemetry = remember(state.connectionState, state.serverUrlInput, state.remoteRoomCode, nowEpochMs) {
        val connState = when (state.connectionState) {
            is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
            is TunnelConnectionState.Connecting -> RemoteConnectionState.Connecting
            is TunnelConnectionState.Disconnected -> RemoteConnectionState.Disconnected
            else -> RemoteConnectionState.Disconnected
        }
        val channel = when {
            state.isUsbConnected && state.selectedMode == AdbConnectionMode.USB -> RemoteTransportChannel.UsbCable
            state.selectedMode == AdbConnectionMode.LAN || state.isAtHomeWifi -> RemoteTransportChannel.LanWifi
            state.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            state.remoteRoomCode.isNotBlank() -> RemoteTransportChannel.WebRtcDirect
            else -> RemoteTransportChannel.None
        }
        val uptimeSec = connected?.connectedAtEpochMs?.let {
            if (it > 0 && nowEpochMs > it) (nowEpochMs - it) / 1000 else null
        }
        RemoteTelemetryData(
            state = connState,
            channel = channel,
            host = state.serverUrlInput,
            roomCode = state.remoteRoomCode,
            uptimeSec = uptimeSec ?: 0L
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Unified Bento Telemetry Bar
        ConnectionTelemetryBar(
            telemetry = telemetry,
            onTriggerConnect = { onIntent(DashboardUiIntent.ScanQrCode) }
        )

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
                onConnect = { lan, remote ->
                    onIntent(DashboardUiIntent.ApplyQrRemoteConfig(roomCode = remote, serverUrl = lan))
                },
                onDisconnect = { onIntent(DashboardUiIntent.ToggleTunnel) },
                onScanQr = { onIntent(DashboardUiIntent.ScanQrCode) }
            )

            // Collapsible Local Device Debugging Card (Android 11+ Pairing)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showLocalPairing = !showLocalPairing },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, ext.cardBorder)
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
                        Column {
                            Text(
                                text = "Ghép đôi ADB không dây nội bộ",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tùy chọn: Dành cho thiết bị chưa bật cổng 5555",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
