package app.duinch.adbagent.features.tunnel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialDashboardState(container))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<DashboardUiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<DashboardUiEffect> = _effects.asSharedFlow()

    private val pairingController = DashboardPairingController(
        container = container,
        scope = viewModelScope,
        uiState = _uiState,
        effects = _effects,
        onSaveConfig = { saveCurrentConfig(silent = true) }
    )

    private val approvalController = DashboardServerApprovalController(
        gateway = container.serverApprovalGateway,
        scope = viewModelScope,
        uiState = _uiState,
        effects = _effects
    )

    private val slotController = DashboardSlotController(
        container = container,
        approvals = approvalController,
        scope = viewModelScope,
        uiState = _uiState,
        effects = _effects
    )

    init {
        viewModelScope.launch { container.ensureTermuxBootstrap() }
        container.adbDiscoveryGateway.startDiscovery()
        observeFlows()
        viewModelScope.launchAdbPortWatch(container.adbPairingGateway, _uiState)
        slotController.restoreSavedSlots()
        restoreConnectionIfWanted(container, approvalController, _uiState.value.serverUrlInput) { runToggle(wanted = true) }
    }

    fun onIntent(intent: DashboardUiIntent) {
        if (handleDashboardSettingsIntent(
                intent = intent,
                uiState = _uiState,
                container = container,
                onSave = { saveCurrentConfig(silent = it) },
                onMessage = { _effects.tryEmit(DashboardUiEffect.ShowMessage(it)) }
            )
        ) return

        when (intent) {
            DashboardUiIntent.SaveConfig -> saveCurrentConfig()
            DashboardUiIntent.ToggleTunnel -> toggleTunnel()
            DashboardUiIntent.CancelServerApproval -> approvalController.cancel()
            DashboardUiIntent.ToggleHistoryExpand -> _uiState.update { it.copy(showAllHistory = !it.showAllHistory) }
            DashboardUiIntent.ClearHistory -> {
                container.clearTunnelHistory()
                _effects.tryEmit(DashboardUiEffect.ShowMessage("Đã xóa lịch sử kết nối cũ"))
            }
            DashboardUiIntent.ToggleHostCommandsExpand -> _uiState.update { it.copy(showAllHostCommands = !it.showAllHostCommands) }
            DashboardUiIntent.ClearHostCommands -> {
                container.hostActivityRepository.clearCommands()
                _effects.tryEmit(DashboardUiEffect.ShowMessage("Đã xóa danh sách lệnh máy tính"))
            }
            is DashboardUiIntent.SetForceShowPairing -> _uiState.update { it.copy(selectedTab = AppTab.CONNECT, forceShowPairing = intent.force) }
            DashboardUiIntent.ScanQrCode -> {
                slotController.pendingQrSlotId = null
                _effects.tryEmit(DashboardUiEffect.LaunchQrScanner)
            }
            is DashboardUiIntent.ScanQrForSlot -> {
                slotController.pendingQrSlotId = intent.slotId
                _effects.tryEmit(DashboardUiEffect.LaunchQrScanner)
            }
            DashboardUiIntent.ScanLanServers -> container.adbDiscoveryGateway.startDiscovery()
            is DashboardUiIntent.ConnectDiscoveredServer -> {
                _uiState.update {
                    it.copy(
                        hasPrimarySlot = true,
                        primarySlotMode = ConnectMode.Lan,
                        serverUrlInput = intent.server.address,
                        selectedTab = AppTab.CONNECT
                    )
                }
                saveCurrentConfig(silent = true)
                toggleTunnel()
            }
            is DashboardUiIntent.ApplyQrRemoteConfig -> {
                val targetSlotId = slotController.pendingQrSlotId
                slotController.pendingQrSlotId = null
                viewModelScope.launch {
                    val server = chooseQrServerForTarget(_uiState.value, targetSlotId, container.serverApprovalGateway, intent)
                    when {
                        server == null -> _effects.emit(DashboardUiEffect.ShowMessage("Máy chủ chưa bật Quick Tunnel"))
                        targetSlotId != null -> slotController.connectSlot(targetSlotId, server)
                        else -> connectPrimary(server)
                    }
                }
            }
            is DashboardUiIntent.AddConnectionSlot -> slotController.addSlot(intent.mode, intent.server, intent.scanQr)
            DashboardUiIntent.RemovePrimarySlot -> slotController.removePrimarySlot { saveCurrentConfig(silent = true) }
            is DashboardUiIntent.RemoveConnectionSlot -> slotController.removeSlot(intent.slotId)
            is DashboardUiIntent.ConnectExtraSlot -> slotController.connectSlot(intent.slotId, intent.serverUrl)
            is DashboardUiIntent.DisconnectExtraSlot -> slotController.disconnectSlot(intent.slotId)
            is DashboardUiIntent.OnServerRemoteTunnelPushed -> {
                _uiState.update { cur ->
                    cur.copy(
                        usbHostName = intent.hostName.ifBlank { cur.usbHostName },
                        serverQuickTunnelUrl = if (intent.active) intent.url else "",
                        isServerRemoteActive = intent.active,
                        serverUrlInput = if (intent.active && intent.url.isNotBlank()) intent.url else cur.serverUrlInput
                    )
                }
            }
            is DashboardUiIntent.OnPairingIntent -> handlePairingIntent(intent.intent)
            is DashboardUiIntent.OnTermuxIntent -> handleTermuxAction(
                intent = intent.intent, scope = viewModelScope, uiState = _uiState,
                runCommand = container.runTermuxCommand::invoke, clearLogs = container.termuxRuntimeGateway::clearConsoleLogs
            )
            DashboardUiIntent.NextOnboardingStep -> {
                val next = when (_uiState.value.activeOnboardingStep) {
                    OnboardingStep.STEP_ADB -> OnboardingStep.STEP_SERVER_MODE
                    OnboardingStep.STEP_SERVER_MODE -> OnboardingStep.STEP_SCAN_QR
                    else -> null
                }
                if (next == null) container.tunnelConfigRepository.setHasSeenOnboarding(true)
                _uiState.update { it.copy(activeOnboardingStep = next) }
            }
            DashboardUiIntent.DismissOnboarding -> {
                container.tunnelConfigRepository.setHasSeenOnboarding(true)
                _uiState.update { it.copy(activeOnboardingStep = null) }
            }
            DashboardUiIntent.ShowOnboarding -> _uiState.update { it.copy(activeOnboardingStep = OnboardingStep.STEP_ADB) }
            else -> Unit
        }
    }

    private fun observeFlows() {
        viewModelScope.launch {
            container.reverseTunnelGateway.observeConnectionState().collect { conn ->
                applyPrimaryConnectionState(_uiState, container.tunnelConfigRepository, conn)
            }
        }
        viewModelScope.launch {
            container.reverseTunnelGateway.observeActiveNetwork().collect { net ->
                val ip = container.reverseTunnelGateway.getLocalWifiIp().orEmpty()
                val ssid = container.reverseTunnelGateway.getCurrentWifiSsid().orEmpty()
                _uiState.update { it.copy(activeNetwork = net, localWifiIp = ip, currentWifiSsid = ssid) }
            }
        }
        viewModelScope.launchServerProbes(container.serverApprovalGateway, _uiState)
        viewModelScope.launch {
            container.reverseTunnelGateway.observeUsbConnected().collect { usb ->
                val now = System.currentTimeMillis()
                _uiState.update { cur ->
                    val usbStarted = when {
                        usb && cur.usbConnectedAtEpochMs == 0L -> now
                        !usb -> 0L
                        else -> cur.usbConnectedAtEpochMs
                    }
                    cur.copy(isUsbConnected = usb, usbConnectedAtEpochMs = usbStarted)
                }
            }
        }
        viewModelScope.launch {
            container.observeTunnelHistory().collect { items ->
                _uiState.update { it.copy(history = items) }
            }
        }
        viewModelScope.launch {
            container.hostActivityRepository.observeCommands().collect { cmds ->
                _uiState.update { it.copy(hostCommands = cmds) }
            }
        }
        viewModelScope.launch {
            container.adbDiscoveryGateway.observeDiscoveredPorts().collect { ports ->
                _uiState.update { cur ->
                    cur.copy(
                        pairing = cur.pairing.copy(
                            discoveredPairingPort = ports.pairingPort,
                            discoveredConnectPort = ports.connectPort
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            container.adbDiscoveryGateway.observeDiscoveredServers().collect { servers ->
                _uiState.update { it.copy(discoveredServers = servers) }
            }
        }
        viewModelScope.launch {
            container.pairingNotificationGateway.observeNotificationPairing().collect { pState ->
                _uiState.update { cur ->
                    val isSuccess = pState is AdbPairingState.PairedAndConnected
                    val portStr = (pState as? AdbPairingState.PairedAndConnected)?.connectPort?.toString()
                        ?: cur.localAdbPortInput
                    cur.copy(
                        selectedTab = if (isSuccess) AppTab.CONNECT else cur.selectedTab,
                        forceShowPairing = if (isSuccess) false else cur.forceShowPairing,
                        localAdbPortInput = portStr,
                        pairing = cur.pairing.copy(pairingState = pState)
                    )
                }
                if (pState is AdbPairingState.PairedAndConnected) {
                    saveCurrentConfig(silent = true)
                    _effects.emit(DashboardUiEffect.ShowMessage("Đã ghép nối ADB thành công!"))
                }
            }
        }
        viewModelScope.launch {
            container.termuxRuntimeGateway.observeBootstrapState().collect { boot ->
                _uiState.update { cur -> cur.copy(termux = cur.termux.copy(bootstrapState = boot)) }
            }
        }
        viewModelScope.launch {
            container.termuxRuntimeGateway.observeConsoleLogs().collect { logs ->
                _uiState.update { cur -> cur.copy(termux = cur.termux.copy(logs = logs)) }
            }
        }
    }

    private fun handlePairingIntent(intent: PairingUiIntent) {
        pairingController.handleIntent(intent)
    }

    private fun saveCurrentConfig(silent: Boolean = false) {
        val draft = buildDraftConfig(_uiState.value)
        container.saveTunnelConfig(draft).fold(
            onSuccess = { if (!silent) _effects.tryEmit(DashboardUiEffect.ShowMessage("Đã lưu cấu hình đường hầm")) },
            onFailure = { err -> if (!silent) _effects.tryEmit(DashboardUiEffect.ShowMessage(err.message ?: "Lỗi")) }
        )
    }

    private fun connectPrimary(server: String) {
        savePrimaryTarget(container.tunnelConfigRepository, server, _uiState.value.primaryDisplayName)
        _uiState.update {
            it.copy(
                hasPrimarySlot = true,
                primarySlotMode = connectModeForServer(server),
                serverUrlInput = server,
                selectedTab = AppTab.CONNECT
            )
        }
        saveCurrentConfig(silent = true)
        toggleTunnel()
    }

    private fun toggleTunnel() {
        val current = _uiState.value.connectionState
        if (current is TunnelConnectionState.Disconnected || current is TunnelConnectionState.Error) {
            approvalController.connectWhenApproved(_uiState.value.serverUrlInput, _uiState.value.deviceAliasInput) { runToggle(wanted = true) }
        } else {
            viewModelScope.launch { runToggle(wanted = false) }
        }
    }

    private suspend fun runToggle(wanted: Boolean) {
        container.tunnelConfigRepository.setWantsConnected(wanted)
        container.toggleReverseTunnel(buildDraftConfig(_uiState.value)).onFailure {
            _effects.emit(DashboardUiEffect.ShowMessage(it.message ?: "Không thể bật đường hầm"))
        }
    }

    override fun onCleared() {
        slotController.clearAll()
        container.adbDiscoveryGateway.stopDiscovery()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(container) as T
                }
            }
    }
}
