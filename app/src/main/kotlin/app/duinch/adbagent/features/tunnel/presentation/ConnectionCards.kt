package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.DualChannelConnectContent
import app.duinch.remotetunnel.ui.RemoteTelemetryData

/** What every connection card needs from the home tab. */
internal class CardHost(
    val onIntent: (DashboardUiIntent) -> Unit,
    val expandedId: String?,
    val expansion: ConnectionExpansionState,
    val canCollapse: Boolean,
    val activePort: Int,
    val isPaired: Boolean,
    val isWifiConnected: Boolean
)

internal fun slotTitle(
    state: DashboardUiState,
    showPrimarySlot: Boolean,
    slot: ServerConnectionSlot,
    isGroupedUnderServer: Boolean = false
): String {
    if (isGroupedUnderServer) {
        return when (slot.mode) {
            app.duinch.remotetunnel.ui.ConnectMode.Lan -> "Kết nối Wi-Fi"
            app.duinch.remotetunnel.ui.ConnectMode.Remote -> "Kết nối từ xa"
        }
    }
    val index = state.extraSlots.indexOf(slot)
    val number = if (showPrimarySlot) index + 2 else index + 1
    return if (!showPrimarySlot && state.extraSlots.size == 1) "Kết Nối Máy Chủ" else "Máy Chủ $number"
}

@Composable
internal fun PrimaryConnectionCard(
    state: DashboardUiState,
    host: CardHost,
    isGroupedUnderServer: Boolean = false
) {
    val connected = state.connectionState as? TunnelConnectionState.Connected
    val serverTelemetry = rememberServerSlotTelemetry(
        connection = state.connectionState,
        mode = state.primarySlotMode,
        serverUrl = state.serverUrlInput,
        latencyMs = state.serverLatencyMs[state.serverUrlInput]
    )
    val cardTitle = if (isGroupedUnderServer) {
        when (state.primarySlotMode) {
            app.duinch.remotetunnel.ui.ConnectMode.Lan -> "Kết nối Wi-Fi"
            app.duinch.remotetunnel.ui.ConnectMode.Remote -> "Kết nối từ xa"
        }
    } else {
        if (state.extraSlots.isNotEmpty()) "Máy Chủ 1" else "Kết Nối Máy Chủ"
    }
    SwipeableConnectionCard(
        isInUse = state.connectionState.isInUse(),
        canConnect = state.serverUrlInput.isNotBlank(),
        onToggleConnection = {
            if (state.connectionState.isInUse()) {
                host.onIntent(DashboardUiIntent.ToggleTunnel)
            } else {
                host.onIntent(DashboardUiIntent.ApplyQrRemoteConfig(serverUrl = state.serverUrlInput))
            }
        },
        onDelete = { host.onIntent(DashboardUiIntent.RemovePrimarySlot) },
        displayName = state.primaryDisplayName,
        onRename = { host.onIntent(DashboardUiIntent.RenameConnection(PRIMARY_CONNECTION_ID, it)) }
    ) { openRename ->
        DualChannelConnectContent(
            displayName = state.primaryDisplayName,
            onRename = openRename,
            title = cardTitle,
            statusNote = connectionNote(state.connectionState, serverTelemetry.uptimeSec),
            tone = connectionTone(state.connectionState),
            detailItems = adbPortDetails(connected?.mappedServerPort, host.activePort),
            detailsFooter = null,
            dedicatedMode = state.primarySlotMode,
            initialLanAddress = state.serverUrlInput,
            initialRemoteTarget = state.serverUrlInput,
            remotePlaceholder = REMOTE_URL_PLACEHOLDER,
            telemetry = serverTelemetry,
            isUsbConnected = state.isUsbConnected,
            isWifiConnected = host.isWifiConnected,
            serverRemoteUrl = state.serverQuickTunnelUrl,
            isServerRemoteActive = state.isServerRemoteActive,
            recentTunnels = recentTunnelsOf(state),
            adbStatusContent = null,
            onClose = { host.onIntent(DashboardUiIntent.RemovePrimarySlot) },
            expanded = connected == null && host.expandedId == PRIMARY_CONNECTION_ID,
            onToggleExpanded = { host.expansion.toggle(PRIMARY_CONNECTION_ID, host.expandedId) }.takeIf { host.canCollapse && connected == null },
            onConnect = { lan, remote ->
                val targetServer = when {
                    lan.isNotBlank() -> lan
                    remote.isNotBlank() -> remote
                    state.serverQuickTunnelUrl.isNotBlank() -> state.serverQuickTunnelUrl
                    else -> state.serverUrlInput
                }
                host.onIntent(DashboardUiIntent.ApplyQrRemoteConfig(serverUrl = targetServer))
            },
            onDisconnect = { host.onIntent(DashboardUiIntent.ToggleTunnel) },
            onScanQr = { host.onIntent(DashboardUiIntent.ScanQrCode) },
            onMdnsScan = { host.onIntent(DashboardUiIntent.ScanLanServers) }
        )
    }
}

@Composable
internal fun ExtraConnectionCard(slot: ServerConnectionSlot, slotTitle: String, state: DashboardUiState, host: CardHost) {
    val slotMappedPort = (slot.connectionState as? TunnelConnectionState.Connected)?.mappedServerPort
    val slotTelemetry = rememberServerSlotTelemetry(
        connection = slot.connectionState,
        mode = slot.mode,
        serverUrl = slot.serverUrlInput,
        latencyMs = state.serverLatencyMs[slot.serverUrlInput]
    )
    SwipeableConnectionCard(
        isInUse = slot.connectionState.isInUse(),
        canConnect = slot.serverUrlInput.isNotBlank(),
        onToggleConnection = {
            if (slot.connectionState.isInUse()) {
                host.onIntent(DashboardUiIntent.DisconnectExtraSlot(slot.id))
            } else {
                host.onIntent(DashboardUiIntent.ConnectExtraSlot(slot.id, slot.serverUrlInput))
            }
        },
        onDelete = { host.onIntent(DashboardUiIntent.RemoveConnectionSlot(slot.id)) },
        displayName = slot.displayName,
        onRename = { host.onIntent(DashboardUiIntent.RenameConnection(slot.id, it)) }
    ) { openRename ->
        key(slot.formRevision) {
            DualChannelConnectContent(
                displayName = slot.displayName,
                onRename = openRename,
                title = slotTitle,
                statusNote = connectionNote(slot.connectionState, slotTelemetry.uptimeSec),
                tone = connectionTone(slot.connectionState),
                detailItems = adbPortDetails(slotMappedPort, host.activePort),
                detailsFooter = null,
                dedicatedMode = slot.mode,
                initialLanAddress = slot.serverUrlInput,
                initialRemoteTarget = slot.serverUrlInput,
                remotePlaceholder = REMOTE_URL_PLACEHOLDER,
                telemetry = slotTelemetry,
                isWifiConnected = host.isWifiConnected,
                adbStatusContent = null,
                onClose = { host.onIntent(DashboardUiIntent.RemoveConnectionSlot(slot.id)) },
                expanded = slot.connectionState !is TunnelConnectionState.Connected && host.expandedId == slot.id,
                onToggleExpanded = { host.expansion.toggle(slot.id, host.expandedId) }.takeIf {
                    host.canCollapse && slot.connectionState !is TunnelConnectionState.Connected
                },
                onConnect = { lan, remote ->
                    host.onIntent(DashboardUiIntent.ConnectExtraSlot(slot.id, lan.ifBlank { remote }))
                },
                onDisconnect = { host.onIntent(DashboardUiIntent.DisconnectExtraSlot(slot.id)) },
                onScanQr = { host.onIntent(DashboardUiIntent.ScanQrForSlot(slot.id)) }
            )
        }
    }
}

private fun recentTunnelsOf(state: DashboardUiState): List<String> =
    listOf(state.serverQuickTunnelUrl, state.serverUrlInput)
        .filter { it.isNotBlank() && (it.contains("trycloudflare.com") || it.startsWith("http")) }
        .distinct()
        .take(3)

private const val REMOTE_URL_PLACEHOLDER = "Dán URL Cloudflare của máy chủ"
