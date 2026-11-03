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

    val usbTelemetry = remember(state.isUsbConnected, state.usbConnectedAtEpochMs, state.usbHostName, nowEpochMs, trafficState) {
        val uptimeSec = if (state.usbConnectedAtEpochMs > 0L && nowEpochMs > state.usbConnectedAtEpochMs) {
            (nowEpochMs - state.usbConnectedAtEpochMs) / 1000
        } else {
            0L
        }
        RemoteTelemetryData(
            state = if (state.isUsbConnected) RemoteConnectionState.Connected else RemoteConnectionState.Disconnected,
            channel = RemoteTransportChannel.UsbCable,
            host = state.usbHostName,
            pingMs = if (state.isUsbConnected) 1L else null,
            uptimeSec = uptimeSec,
            bytesTransferred = trafficState.first,
            transferSpeedBps = trafficState.second
        )
    }

    val serverTelemetry = remember(state.connectionState, state.serverUrlInput, state.remoteRoomCode, state.primarySlotMode, state.serverPingMs, nowEpochMs, trafficState) {
        val connState = when {
            state.connectionState is TunnelConnectionState.Connecting ||
                state.connectionState is TunnelConnectionState.Reconnecting -> RemoteConnectionState.Connecting
            state.connectionState is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
            else -> RemoteConnectionState.Disconnected
        }
        val channel = when {
            state.primarySlotMode == ConnectMode.Lan -> RemoteTransportChannel.LanWifi
            state.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
            state.remoteRoomCode.isNotBlank() -> RemoteTransportChannel.WebRtcDirect
            else -> RemoteTransportChannel.WebRtcDirect
        }
        val serverEpoch = connected?.connectedAtEpochMs ?: 0L
        val uptimeSec = if (serverEpoch > 0L && nowEpochMs > serverEpoch) {
            (nowEpochMs - serverEpoch) / 1000
        } else {
            0L
        }
        val actualPing = if (connState == RemoteConnectionState.Connected) state.serverPingMs ?: 3L else null
        RemoteTelemetryData(
            state = connState,
            channel = channel,
            host = state.serverUrlInput,
            roomCode = state.remoteRoomCode,
            pingMs = actualPing,
            uptimeSec = uptimeSec,
            bytesTransferred = trafficState.first,
            transferSpeedBps = trafficState.second
        )
    }

    val isWifiConnected = remember(state.activeNetwork, state.localWifiIp) {
        state.activeNetwork == NetworkType.WIFI || state.localWifiIp.isNotBlank()
    }
    val isPaired = state.pairing.pairingState is AdbPairingState.PairedAndConnected
    val activePort = (state.pairing.pairingState as? AdbPairingState.PairedAndConnected)?.connectPort ?: 5555
    val showPrimarySlot = state.hasPrimarySlot || state.connectionState !is TunnelConnectionState.Disconnected

    val hasAnyWifiSlot = (showPrimarySlot && state.primarySlotMode == ConnectMode.Lan) ||
        state.extraSlots.any { it.mode == ConnectMode.Lan }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (!hasAnyWifiSlot) {
            state.discoveredServers.forEach { server ->
                DiscoveredServerCard(
                    server = server,
                    onConnect = { onIntent(DashboardUiIntent.ConnectDiscoveredServer(it)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!state.isUsbConnected && !showPrimarySlot && state.extraSlots.isEmpty()) {
            EmptyConnectionCard(
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (state.isUsbConnected) {
            UsbConnectionCard(
                telemetry = usbTelemetry,
                hostName = state.usbHostName,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showPrimarySlot) {
            DualChannelConnectContent(
                title = if (state.extraSlots.isNotEmpty()) "Máy Chủ 1" else "Kết Nối Máy Chủ",
                dedicatedMode = state.primarySlotMode,
                initialLanAddress = state.serverUrlInput,
                initialRemoteTarget = state.remoteRoomCode,
                telemetry = serverTelemetry,
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
                                telemetry = serverTelemetry
                            )
                        }
                    }
                },
                onClose = { onIntent(DashboardUiIntent.RemovePrimarySlot) },
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
                onMdnsScan = { onIntent(DashboardUiIntent.ScanLanServers) },
                onOpenPairing = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
            )
        }

        state.extraSlots.forEachIndexed { index, slot ->
            val slotNumber = if (showPrimarySlot) index + 2 else index + 1
            val slotTitle = if (!showPrimarySlot && state.extraSlots.size == 1) "Kết Nối Máy Chủ" else "Máy Chủ $slotNumber"
            val slotConnState = when {
                slot.connectionState is TunnelConnectionState.Connecting ||
                    slot.connectionState is TunnelConnectionState.Reconnecting -> RemoteConnectionState.Connecting
                slot.connectionState is TunnelConnectionState.Connected -> RemoteConnectionState.Connected
                else -> RemoteConnectionState.Disconnected
            }
            val slotTelemetry = remember(slot.connectionState, slot.serverUrlInput, slot.remoteRoomCode, slot.mode) {
                RemoteTelemetryData(
                    state = slotConnState,
                    channel = when {
                        slot.mode == ConnectMode.Lan -> RemoteTransportChannel.LanWifi
                        slot.serverUrlInput.contains("trycloudflare.com") -> RemoteTransportChannel.QuickTunnel
                        else -> RemoteTransportChannel.WebRtcDirect
                    },
                    host = slot.serverUrlInput,
                    roomCode = slot.remoteRoomCode
                )
            }
            DualChannelConnectContent(
                title = slotTitle,
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
            },
            onScanQrNow = {
                showAddSlotPicker = false
                onIntent(DashboardUiIntent.ScanQrCode)
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
