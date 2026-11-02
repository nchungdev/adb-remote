package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.remotetunnel.ui.ConnectMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DashboardSlotController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<DashboardUiState>,
    private val effects: MutableSharedFlow<DashboardUiEffect>
) {
    private val slotGateways = mutableMapOf<String, ReverseTunnelGateway>()
    private val slotJobs = mutableMapOf<String, Job>()
    private var nextSlotIndex = 2
    var pendingQrSlotId: String? = null

    fun addSlot(mode: ConnectMode = ConnectMode.Remote) {
        val slotId = "slot-${nextSlotIndex++}"
        val gateway = container.createReverseTunnelGateway()
        slotGateways[slotId] = gateway

        uiState.update { cur ->
            cur.copy(
                extraSlots = cur.extraSlots + ServerConnectionSlot(
                    id = slotId,
                    mode = mode,
                    remoteRoomCode = if (mode == ConnectMode.Remote) "default" else "",
                    serverUrlInput = ""
                )
            )
        }

        slotJobs[slotId] = scope.launch {
            gateway.observeConnectionState().collect { conn ->
                uiState.update { cur ->
                    cur.copy(
                        extraSlots = cur.extraSlots.map { slot ->
                            if (slot.id == slotId) slot.copy(connectionState = conn) else slot
                        }
                    )
                }
            }
        }
    }

    fun removeSlot(slotId: String) {
        slotJobs.remove(slotId)?.cancel()
        val gw = slotGateways.remove(slotId)
        if (gw != null) {
            scope.launch { gw.disconnect() }
        }
        uiState.update { cur ->
            cur.copy(extraSlots = cur.extraSlots.filterNot { it.id == slotId })
        }
    }

    fun connectSlot(slotId: String, roomCode: String, serverUrl: String) {
        val currentSlot = uiState.value.extraSlots.find { it.id == slotId } ?: return
        val (finalServer, finalRoom) = resolveQrRemoteTarget(
            DashboardUiIntent.ApplyQrRemoteConfig(roomCode = roomCode, serverUrl = serverUrl),
            currentSlot.serverUrlInput,
            currentSlot.remoteRoomCode
        )
        uiState.update { cur ->
            cur.copy(
                extraSlots = cur.extraSlots.map { slot ->
                    if (slot.id == slotId) {
                        slot.copy(serverUrlInput = finalServer, remoteRoomCode = finalRoom)
                    } else {
                        slot
                    }
                }
            )
        }
        val gw = slotGateways[slotId] ?: return
        val config = buildDraftConfig(uiState.value).copy(
            serverUrl = finalServer,
            remoteRoomCode = finalRoom
        )
        scope.launch {
            gw.connect(config).onFailure { err ->
                effects.emit(DashboardUiEffect.ShowMessage(err.message ?: "Không thể bật kết nối phụ"))
            }
        }
    }

    fun disconnectSlot(slotId: String) {
        val gw = slotGateways[slotId] ?: return
        scope.launch {
            gw.disconnect()
        }
    }

    fun clearAll() {
        slotJobs.values.forEach { it.cancel() }
        slotJobs.clear()
        val gateways = slotGateways.values.toList()
        slotGateways.clear()
        scope.launch {
            gateways.forEach { it.disconnect() }
        }
    }
}
