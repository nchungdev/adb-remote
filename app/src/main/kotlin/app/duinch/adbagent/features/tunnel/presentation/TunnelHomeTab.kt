package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AdbAgentTheme
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import app.duinch.remotetunnel.ui.DualChannelConnectContent
import app.duinch.remotetunnel.ui.RemoteConnectionState
import app.duinch.remotetunnel.ui.RemoteTelemetryData
import app.duinch.remotetunnel.ui.RemoteTransportChannel

@Composable
fun TunnelHomeTab(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onPairingIntent: (PairingUiIntent) -> Unit
) {
    val connected = state.connectionState as? TunnelConnectionState.Connected
    var showAddSlotPicker by remember { mutableStateOf(false) }

    val activeConnectedEpoch = when {
        connected != null -> connected.connectedAtEpochMs
        state.isUsbConnected -> state.usbConnectedAtEpochMs
        else -> 0L
    }

    val nowEpochMs by produceConnectedEpochNow(activeConnectedEpoch)
    val trafficState by produceTunnelTrafficMetrics(
        activeConnectedEpoch = activeConnectedEpoch,
        bridgeBytes = connected?.bytesBridged ?: 0L
    )

    val telemetry = remember(state.connectionState, state.serverUrlInput, state.remoteRoomCode, state.isUsbConnected, nowEpochMs, trafficState) {
        val isTunnelActive = state.connectionState is TunnelConnectionState.Connecting ||
            state.connectionState is TunnelConnectionState.Reconnecting ||
            state.connectionState is TunnelConnectionState.Connected

        val connState = when {
            state.connectionState is TunnelConnectionState.Connecting ||
                state.connectionState is TunnelConnectionState.Reconnecting -> RemoteConnectionState.Connecting
            state.connectionState is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
            state.isUsbConnected -> RemoteConnectionState.Connected
            else -> RemoteConnectionState.Disconnected
        }
        val channel = when {
            isTunnelActive && state.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            isTunnelActive && state.remoteRoomCode.isNotBlank() -> RemoteTransportChannel.WebRtcDirect
            isTunnelActive && (state.selectedMode == AdbConnectionMode.LAN || state.isAtHomeWifi) -> RemoteTransportChannel.LanWifi
            state.isUsbConnected -> RemoteTransportChannel.UsbCable
            state.selectedMode == AdbConnectionMode.LAN || state.isAtHomeWifi -> RemoteTransportChannel.LanWifi
            state.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            state.remoteRoomCode.isNotBlank() -> RemoteTransportChannel.WebRtcDirect
            else -> RemoteTransportChannel.None
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
            pingMs = if (state.isUsbConnected) (1L..3L).random() else null,
            uptimeSec = uptimeSec ?: 0L,
            bytesTransferred = trafficState.first,
            transferSpeedBps = trafficState.second
        )
    }

    val isWifiConnected = remember(state.activeNetwork, state.localWifiIp) {
        state.activeNetwork == NetworkType.WIFI || state.localWifiIp.isNotBlank()
    }
    val isPaired = state.pairing.pairingState is AdbPairingState.PairedAndConnected
    val activePort = (state.pairing.pairingState as? AdbPairingState.PairedAndConnected)?.connectPort ?: 5555

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (state.isUsbConnected) {
            UsbConnectionCard(
                telemetry = telemetry,
                modifier = Modifier.fillMaxWidth()
            )
        }

        DualChannelConnectContent(
            title = if (state.extraSlots.isNotEmpty()) "Máy Chủ 1" else "Kết Nối Máy Chủ",
            initialLanAddress = state.serverUrlInput,
            initialRemoteTarget = state.remoteRoomCode,
            telemetry = telemetry,
            isUsbConnected = state.isUsbConnected,
            isWifiConnected = isWifiConnected,
            serverRemoteUrl = state.serverQuickTunnelUrl,
            isServerRemoteActive = state.isServerRemoteActive,
            recentTunnels = remember(state.serverQuickTunnelUrl, state.serverUrlInput, state.recentTunnelUrls) {
                (state.recentTunnelUrls + listOf(state.serverQuickTunnelUrl, state.serverUrlInput))
                    .filter { it.isNotBlank() && (it.contains("trycloudflare.com") || it.startsWith("http")) }
                    .distinct()
                    .take(3)
            },
            adbStatusContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WirelessAdbStatusRow(
                        isPaired = isPaired,
                        activePort = activePort,
                        onOpenPairing = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
                    )
                    if (state.connectionState is TunnelConnectionState.Connected) {
                        AdbTelemetryBentoGrid(
                            isUsbConnected = false,
                            isPaired = isPaired,
                            activePort = activePort,
                            telemetry = telemetry
                        )
                    }
                }
            },
            onConnect = { lan, remote ->
                val isUrl = remote.startsWith("http://") || remote.startsWith("https://") || remote.contains("trycloudflare.com")
                val targetServer = when {
                    lan.isNotBlank() -> lan
                    isUrl -> remote
                    state.serverQuickTunnelUrl.isNotBlank() -> state.serverQuickTunnelUrl
                    else -> state.serverUrlInput
                }
                val targetRoom = when {
                    !isUrl && remote.isNotBlank() -> remote
                    state.remoteRoomCode.isNotBlank() -> state.remoteRoomCode
                    else -> "default"
                }
                onIntent(DashboardUiIntent.ApplyQrRemoteConfig(roomCode = targetRoom, serverUrl = targetServer))
            },
            onDisconnect = { onIntent(DashboardUiIntent.ToggleTunnel) },
            onScanQr = { onIntent(DashboardUiIntent.ScanQrCode) },
            onOpenPairing = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
        )

        state.extraSlots.forEachIndexed { index, slot ->
            val slotConnState = when {
                slot.connectionState is TunnelConnectionState.Connecting ||
                    slot.connectionState is TunnelConnectionState.Reconnecting -> RemoteConnectionState.Connecting
                slot.connectionState is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
                else -> RemoteConnectionState.Disconnected
            }
            val slotTelemetry = remember(slot.connectionState, slot.serverUrlInput, slot.remoteRoomCode) {
                RemoteTelemetryData(
                    state = slotConnState,
                    channel = if (slot.serverUrlInput.contains("trycloudflare.com")) RemoteTransportChannel.QuickTunnel else RemoteTransportChannel.WebRtcDirect,
                    host = slot.serverUrlInput,
                    roomCode = slot.remoteRoomCode
                )
            }
            DualChannelConnectContent(
                title = "Máy Chủ ${index + 2}",
                dedicatedMode = slot.mode,
                initialLanAddress = slot.serverUrlInput,
                initialRemoteTarget = slot.remoteRoomCode,
                telemetry = slotTelemetry,
                isWifiConnected = isWifiConnected,
                adbStatusContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        WirelessAdbStatusRow(
                            isPaired = isPaired,
                            activePort = activePort,
                            onOpenPairing = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
                        )
                        if (slot.connectionState is TunnelConnectionState.Connected) {
                            AdbTelemetryBentoGrid(
                                isUsbConnected = false,
                                isPaired = isPaired,
                                activePort = activePort,
                                telemetry = slotTelemetry
                            )
                        }
                    }
                },
                onClose = { onIntent(DashboardUiIntent.RemoveConnectionSlot(slot.id)) },
                onConnect = { lan, remote ->
                    val isUrl = remote.startsWith("http://") || remote.startsWith("https://") || remote.contains("trycloudflare.com")
                    val targetServer = if (lan.isNotBlank()) lan else if (isUrl) remote else ""
                    val targetRoom = if (!isUrl && remote.isNotBlank()) remote else "default"
                    onIntent(DashboardUiIntent.ConnectExtraSlot(slot.id, targetRoom, targetServer))
                },
                onDisconnect = { onIntent(DashboardUiIntent.DisconnectExtraSlot(slot.id)) },
                onScanQr = { onIntent(DashboardUiIntent.ScanQrForSlot(slot.id)) },
                onOpenPairing = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
            )
        }

        OutlinedButton(
            onClick = { showAddSlotPicker = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Thêm máy chủ kết nối", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }

    if (showAddSlotPicker) {
        AddConnectionModeDialog(
            onDismissRequest = { showAddSlotPicker = false },
            onSelectMode = { mode ->
                onIntent(DashboardUiIntent.AddConnectionSlot(mode))
                showAddSlotPicker = false
            }
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TunnelHomeTabPreview() {
    AdbAgentTheme(darkTheme = false) {
        TunnelHomeTab(
            state = DashboardUiState(),
            onIntent = {},
            onPairingIntent = {}
        )
    }
}
