package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DashboardSlotController(
    private val container: AppContainer,
    private val approvals: DashboardServerApprovalController,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<DashboardUiState>,
    private val effects: MutableSharedFlow<DashboardUiEffect>
) {
    private val slotGateways = mutableMapOf<String, ReverseTunnelGateway>()
    private val slotJobs = mutableMapOf<String, Job>()
    private val wantedSlots = mutableSetOf<String>()
    private var nextSlotIndex = 2
    var pendingQrSlotId: String? = null

    fun addSlot(mode: ConnectMode = ConnectMode.Remote, server: ServerIdentity? = null, scanQr: Boolean = false) {
        pendingQrSlotId = null
        if (!uiState.value.hasPrimarySlot) {
            uiState.update { cur ->
                cur.copy(hasPrimarySlot = true, primarySlotMode = mode, primaryServer = server)
            }
        } else {
            val slotId = "slot-${nextSlotIndex++}"
            registerGateway(slotId)
            uiState.update { cur ->
                cur.copy(extraSlots = cur.extraSlots + ServerConnectionSlot(id = slotId, mode = mode, server = server))
            }
            if (scanQr) pendingQrSlotId = slotId
        }
        if (scanQr) effects.tryEmit(DashboardUiEffect.LaunchQrScanner)
    }

    private fun registerGateway(slotId: String): ReverseTunnelGateway {
        val gateway = container.createReverseTunnelGateway(slotId)
        slotGateways[slotId] = gateway
        slotJobs[slotId] = scope.launch {
            gateway.observeConnectionState().collect { conn ->
                val server = (conn as? TunnelConnectionState.Connected)?.server
                if (server != null) container.tunnelConfigRepository.rememberServer(slotId, server)
                uiState.update { cur ->
                    cur.copy(
                        extraSlots = cur.extraSlots.map { slot ->
                            if (slot.id == slotId) slot.copy(connectionState = conn, server = server ?: slot.server) else slot
                        }
                    )
                }
            }
        }
        return gateway
    }

    /** Brings back the extra cards from the last run and reconnects those left connected. */
    fun restoreSavedSlots() {
        val allSaved = container.tunnelConfigRepository.getConnections()
        uiState.update { it.copy(connectionOrder = allSaved.map { conn -> conn.id }) }
        allSaved.filter { it.id != PRIMARY_CONNECTION_ID && it.serverUrl.isNotBlank() }.forEach { saved ->
            val gateway = registerGateway(saved.id)
            nextSlotIndex = maxOf(nextSlotIndex, (saved.id.substringAfter("slot-").toIntOrNull() ?: 1) + 1)
            uiState.update { cur ->
                cur.copy(
                    extraSlots = cur.extraSlots + ServerConnectionSlot(
                        id = saved.id,
                        mode = connectModeForServer(saved.serverUrl),
                        serverUrlInput = saved.serverUrl,
                        displayName = saved.name,
                        server = saved.server,
                        autoConnect = saved.wantsConnected
                    )
                )
            }
            if (saved.wantsConnected) {
                wantedSlots += saved.id
                val config = buildDraftConfig(uiState.value).copy(serverUrl = saved.serverUrl)
                approvals.restoreIfApproved(saved.serverUrl) { gateway.connect(config) }
            }
        }
    }

    private fun persistSlot(slotId: String) {
        val slot = uiState.value.extraSlots.find { it.id == slotId } ?: return
        if (slot.serverUrlInput.isBlank()) return
        container.tunnelConfigRepository.upsertConnection(
            SavedConnection(slotId, slot.serverUrlInput, slotId in wantedSlots, slot.displayName, slot.server)
        )
    }

    fun removePrimarySlot(onSaveEmptyConfig: () -> Unit) {
        container.tunnelConfigRepository.setWantsConnected(false)
        scope.launch {
            container.reverseTunnelGateway.disconnect()
        }
        uiState.update { cur ->
            cur.copy(
                hasPrimarySlot = false,
                serverUrlInput = "",
                primaryDisplayName = null,
                primaryServer = null
            )
        }
        onSaveEmptyConfig()
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
        wantedSlots -= slotId
        container.tunnelConfigRepository.removeConnection(slotId)
    }

    fun connectSlot(slotId: String, serverUrl: String) {
        if (uiState.value.extraSlots.none { it.id == slotId }) return
        val current = uiState.value
        val others = current.extraSlots.filter { it.id != slotId }.map { it.serverUrlInput } +
            listOfNotNull(current.serverUrlInput.takeIf { current.hasPrimarySlot })
        if (others.any { isSameServer(it, serverUrl) }) {
            effects.tryEmit(DashboardUiEffect.ShowMessage("Đã có thẻ kết nối tới địa chỉ này"))
            // The card keeps typed text in its own form state; a new revision clears it.
            uiState.update { cur ->
                cur.copy(extraSlots = cur.extraSlots.map { if (it.id == slotId) it.copy(formRevision = it.formRevision + 1) else it })
            }
            return
        }
        uiState.update { cur ->
            cur.copy(
                extraSlots = cur.extraSlots.map { slot ->
                    if (slot.id == slotId) {
                        slot.copy(
                            serverUrlInput = serverUrl,
                            mode = connectModeForServer(serverUrl)
                        )
                    } else {
                        slot
                    }
                }
            )
        }
        wantedSlots += slotId
        persistSlot(slotId)
        val gw = slotGateways[slotId] ?: return
        val config = buildDraftConfig(uiState.value).copy(serverUrl = serverUrl)
        approvals.connectWhenApproved(serverUrl, config.deviceAlias) {
            gw.connect(config).onFailure { err ->
                effects.emit(DashboardUiEffect.ShowMessage(err.message ?: "Không thể bật kết nối phụ"))
            }
        }
    }

    fun disconnectSlot(slotId: String) {
        val gw = slotGateways[slotId] ?: return
        wantedSlots -= slotId
        persistSlot(slotId)
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
