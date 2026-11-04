package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.key
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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

@Composable
fun TunnelHomeTab(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onPairingIntent: (PairingUiIntent) -> Unit
) {
    var addRequest by remember { mutableStateOf<AddConnectionRequest?>(null) }

    val isPaired = state.pairing.pairingState is AdbPairingState.PairedAndConnected

    val isWifiConnected = remember(state.activeNetwork, state.localWifiIp) {
        state.activeNetwork == NetworkType.WIFI || state.localWifiIp.isNotBlank()
    }
    val activePort = (state.pairing.pairingState as? AdbPairingState.PairedAndConnected)?.connectPort ?: 5555
    val showPrimarySlot = state.hasPrimarySlot || state.connectionState !is TunnelConnectionState.Disconnected

    val hasAnyWifiSlot = (showPrimarySlot && state.primarySlotMode == ConnectMode.Lan) ||
        state.extraSlots.any { it.mode == ConnectMode.Lan }

    val connections = buildList {
        if (showPrimarySlot) add(PRIMARY_CONNECTION_ID to state.connectionState)
        state.extraSlots.forEach { add(it.id to it.connectionState) }
    }
    val connectionIds = connections.map { it.first }
    val activeId = activeConnectionId(connections)
    val expansion = rememberConnectionExpansion(activeId, connectionIds)
    val expandedId = resolveExpandedConnection(expansion.userChoice, activeId, connectionIds)

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

        if (state.isUsbConnected && state.usbLinkedServerId == null) {
            UsbStatusRow(
                detail = if (state.usbLinkChecked) "Đang cắm, không thấy ở server đã kết nối" else "Đang cắm"
            )
        }

        val configuredConnections = remember(showPrimarySlot, state.primaryServer, state.extraSlots, state.connectionOrder) {
            val base = buildList {
                if (showPrimarySlot) add(PRIMARY_CONNECTION_ID to state.primaryServer)
                state.extraSlots.forEach { add(it.id to it.server) }
            }
            if (state.connectionOrder.isEmpty()) {
                base
            } else {
                base.sortedBy { (id, _) ->
                    val idx = state.connectionOrder.indexOf(id)
                    if (idx >= 0) idx else Int.MAX_VALUE
                }
            }
        }
        val allBelongToOneServer = remember(configuredConnections) {
            allConnectionsBelongToOneServer(configuredConnections)
        }

        val cards = CardHost(
            onIntent = onIntent,
            expandedId = expandedId,
            expansion = expansion,
            canCollapse = connectionIds.size > 1,
            activePort = activePort,
            isPaired = isPaired,
            isWifiConnected = isWifiConnected
        )
        val slotsById = state.extraSlots.associateBy { it.id }
        groupByServer(configuredConnections).forEach { group ->
            val isGrouped = group.server != null
            val connections = group.connectionIds.map { id ->
                val slot = slotsById[id]
                if (slot == null) {
                    GroupedConnection(id, state.primarySlotMode, state.connectionState, state.serverUrlInput)
                } else {
                    GroupedConnection(id, slot.mode, slot.connectionState, slot.serverUrlInput)
                }
            }
            val anyInUse = connections.any { it.state.isInUse() }
            val groupIds = group.connectionIds
            val groupAutoConnect = groupIds.any { id ->
                if (id == PRIMARY_CONNECTION_ID) state.primaryAutoConnect else (slotsById[id]?.autoConnect ?: true)
            }
            ServerGroupCard(
                server = group.server,
                summary = serverGroupSummary(connections),
                autoConnect = groupAutoConnect,
                onToggleAutoConnect = { enabled ->
                    onIntent(DashboardUiIntent.ToggleServerAutoConnect(groupIds, enabled))
                },
                onAddConnection = group.server?.let { server -> { addRequest = AddConnectionRequest(server) } },
                onRenameServer = { newName ->
                    if (group.server != null) {
                        onIntent(DashboardUiIntent.RenameServer(group.server.id, newName))
                    } else if (groupIds.isNotEmpty()) {
                        onIntent(DashboardUiIntent.RenameConnection(groupIds.first(), newName))
                    }
                },
                onDisconnectServer = if (anyInUse) {
                    {
                        groupIds.forEach { id ->
                            if (id == PRIMARY_CONNECTION_ID) {
                                if (state.connectionState.isInUse()) onIntent(DashboardUiIntent.ToggleTunnel)
                            } else {
                                if (slotsById[id]?.connectionState?.isInUse() == true) {
                                    onIntent(DashboardUiIntent.DisconnectExtraSlot(id))
                                }
                            }
                        }
                    }
                } else null,
                onDeleteServer = {
                    groupIds.forEach { id ->
                        if (id == PRIMARY_CONNECTION_ID) {
                            onIntent(DashboardUiIntent.RemovePrimarySlot)
                        } else {
                            onIntent(DashboardUiIntent.RemoveConnectionSlot(id))
                        }
                    }
                },
                metrics = {
                    liveConnection(connections)?.let { live ->
                        key(live.id) {
                            ServerGroupMetrics(
                                connection = live,
                                latencyMs = state.serverLatencyMs[live.serverUrl],
                                isPaired = isPaired,
                                onOpenPairing = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
                            )
                        }
                    }
                }
            ) {
                if (state.isUsbConnected && group.server?.id == state.usbLinkedServerId) {
                    UsbStatusRow(detail = "Đang cắm vào máy này", modifier = Modifier.padding(horizontal = 4.dp))
                }
                ReorderableConnectionList(
                    connectionIds = group.connectionIds,
                    onReorder = { newOrder ->
                        onIntent(DashboardUiIntent.ReorderConnections(newOrder))
                    },
                    canDrag = { id ->
                        connections.find { it.id == id }?.state is TunnelConnectionState.Connected || id != expandedId
                    }
                ) { id ->
                    val slot = slotsById[id]
                    if (slot == null) {
                        PrimaryConnectionCard(state, cards, isGroupedUnderServer = isGrouped)
                    } else {
                        ExtraConnectionCard(
                            slot = slot,
                            slotTitle = slotTitle(state, showPrimarySlot, slot, isGroupedUnderServer = isGrouped),
                            state = state,
                            host = cards
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = { addRequest = AddConnectionRequest(server = null) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Thêm server", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }

    addRequest?.let { request ->
        val serverName = request.server?.name?.ifBlank { null }
        AddConnectionModeDialog(
            title = if (request.server == null) "Thêm Server" else "Thêm Kết Nối",
            prompt = if (request.server == null) {
                "Thêm máy chủ mới và kết nối đầu tiên của nó:"
            } else {
                "Thêm đường tới ${serverName ?: "máy chủ này"}:"
            },
            onDismissRequest = { addRequest = null },
            onSelectMode = { mode ->
                onIntent(DashboardUiIntent.AddConnectionSlot(mode, request.server))
                addRequest = null
            },
            onScanQrNow = {
                onIntent(DashboardUiIntent.AddConnectionSlot(server = request.server, scanQr = true))
                addRequest = null
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
