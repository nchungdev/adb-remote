package app.duinch.adbagent.features.tunnel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.tunnel.data.TunnelForegroundService
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
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

    private val _uiState = MutableStateFlow(createInitialState())
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

    init {
        viewModelScope.launch { container.ensureTermuxBootstrap() }
        container.adbDiscoveryGateway.startDiscovery()
        observeFlows()
    }

    fun onIntent(intent: DashboardUiIntent) {
        when (intent) {
            is DashboardUiIntent.SelectTab ->
                _uiState.update { it.copy(selectedTab = intent.tab) }
            is DashboardUiIntent.SelectConnectionMode ->
                _uiState.update { it.copy(selectedMode = intent.mode) }
            is DashboardUiIntent.UpdateServerUrl ->
                _uiState.update { it.copy(serverUrlInput = intent.value) }
            is DashboardUiIntent.UpdateRemoteRoomCode ->
                _uiState.update { it.copy(remoteRoomCode = intent.value.trim()) }
            is DashboardUiIntent.UpdateDeviceAlias ->
                _uiState.update { it.copy(deviceAliasInput = intent.value) }
            is DashboardUiIntent.UpdateLocalAdbPort ->
                _uiState.update { it.copy(localAdbPortInput = intent.value.filter(Char::isDigit).take(5)) }
            is DashboardUiIntent.ToggleAutoReconnect ->
                _uiState.update { it.copy(autoReconnect = intent.enabled) }
            is DashboardUiIntent.ToggleRunInBackground -> {
                _uiState.update { it.copy(runInBackground = intent.enabled) }
                saveCurrentConfig(silent = true)
                val msg = if (intent.enabled) {
                    TunnelForegroundService.start(container.appContext)
                    "Đã bật dịch vụ chạy ngầm trên thanh thông báo"
                } else {
                    if (!_uiState.value.isTunnelActive) {
                        TunnelForegroundService.stop(container.appContext)
                    }
                    "Đã tắt dịch vụ chạy ngầm"
                }
                _effects.tryEmit(DashboardUiEffect.ShowMessage(msg))
            }
            is DashboardUiIntent.UpdateTrustedHomeSsid ->
                _uiState.update { it.copy(trustedHomeSsidInput = intent.value) }
            is DashboardUiIntent.ToggleAutoSwitchHomeWifi ->
                _uiState.update { it.copy(autoSwitchOnHomeWifi = intent.enabled) }
            is DashboardUiIntent.ToggleNotifyConnectionChange -> {
                _uiState.update { it.copy(notifyConnectionChange = intent.enabled) }
                saveCurrentConfig(silent = true)
            }
            is DashboardUiIntent.SelectThemeMode -> {
                _uiState.update { it.copy(themeMode = intent.mode) }
                saveCurrentConfig(silent = true)
            }
            DashboardUiIntent.UseCurrentWifiAsHome -> {
                val currentSsid = container.reverseTunnelGateway.getCurrentWifiSsid().orEmpty()
                if (currentSsid.isNotBlank()) {
                    _uiState.update { it.copy(trustedHomeSsidInput = currentSsid) }
                    saveCurrentConfig(silent = true)
                    _effects.tryEmit(DashboardUiEffect.ShowMessage("Đã đặt Wi-Fi '$currentSsid' làm Wi-Fi nhà"))
                } else {
                    _effects.tryEmit(DashboardUiEffect.ShowMessage("Chưa phát hiện tên Wi-Fi hiện tại"))
                }
            }
            DashboardUiIntent.SaveConfig -> saveCurrentConfig()
            DashboardUiIntent.ToggleTunnel -> toggleTunnel()
            DashboardUiIntent.ToggleHistoryExpand ->
                _uiState.update { it.copy(showAllHistory = !it.showAllHistory) }
            DashboardUiIntent.ClearHistory -> {
                container.clearTunnelHistory()
                _effects.tryEmit(DashboardUiEffect.ShowMessage("Đã xóa lịch sử kết nối cũ"))
            }
            DashboardUiIntent.ToggleHostCommandsExpand ->
                _uiState.update { it.copy(showAllHostCommands = !it.showAllHostCommands) }
            DashboardUiIntent.ClearHostCommands -> {
                container.hostActivityRepository.clearCommands()
                _effects.tryEmit(DashboardUiEffect.ShowMessage("Đã xóa danh sách lệnh máy tính"))
            }
            is DashboardUiIntent.SetForceShowPairing ->
                _uiState.update { it.copy(selectedTab = AppTab.CONNECT, forceShowPairing = intent.force) }
            DashboardUiIntent.ScanQrCode ->
                _effects.tryEmit(DashboardUiEffect.LaunchQrScanner)
            is DashboardUiIntent.ApplyQrRemoteConfig -> {
                val (finalServer, finalRoom) = resolveQrRemoteTarget(intent, _uiState.value.serverUrlInput, _uiState.value.remoteRoomCode)
                _uiState.update { it.copy(remoteRoomCode = finalRoom, serverUrlInput = finalServer, selectedTab = AppTab.CONNECT) }
                saveCurrentConfig(silent = true)
                toggleTunnel()
            }
            is DashboardUiIntent.OnServerRemoteTunnelPushed -> {
                _uiState.update { cur ->
                    cur.copy(
                        serverQuickTunnelUrl = if (intent.active) intent.url else "",
                        isServerRemoteActive = intent.active,
                        remoteRoomCode = if (intent.active && intent.roomCode.isNotBlank()) intent.roomCode else cur.remoteRoomCode,
                        serverUrlInput = if (intent.active && intent.url.isNotBlank()) intent.url else cur.serverUrlInput
                    )
                }
            }
            is DashboardUiIntent.OnPairingIntent -> handlePairingIntent(intent.intent)
            is DashboardUiIntent.OnTermuxIntent -> handleTermuxAction(
                intent = intent.intent,
                scope = viewModelScope,
                uiState = _uiState,
                runCommand = container.runTermuxCommand::invoke,
                clearLogs = container.termuxRuntimeGateway::clearConsoleLogs
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
            DashboardUiIntent.ShowOnboarding ->
                _uiState.update { it.copy(activeOnboardingStep = OnboardingStep.STEP_ADB) }
        }
    }

    private fun observeFlows() {
        viewModelScope.launch {
            container.reverseTunnelGateway.observeConnectionState().collect { conn ->
                _uiState.update { it.copy(connectionState = conn) }
            }
        }
        viewModelScope.launch {
            container.reverseTunnelGateway.observeActiveNetwork().collect { net ->
                val ip = container.reverseTunnelGateway.getLocalWifiIp().orEmpty()
                val ssid = container.reverseTunnelGateway.getCurrentWifiSsid().orEmpty()
                _uiState.update { it.copy(activeNetwork = net, localWifiIp = ip, currentWifiSsid = ssid) }
            }
        }
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

    private fun toggleTunnel() {
        val draft = buildDraftConfig(_uiState.value)
        viewModelScope.launch {
            container.toggleReverseTunnel(draft).onFailure { err ->
                _effects.emit(DashboardUiEffect.ShowMessage(err.message ?: "Không thể bật đường hầm"))
            }
        }
    }

    private fun createInitialState(): DashboardUiState {
        val saved = container.tunnelConfigRepository.getConfig()
        val usb = container.reverseTunnelGateway.isUsbConnected()
        val initMode = when {
            usb -> AdbConnectionMode.USB
            container.reverseTunnelGateway.getLocalWifiIp()?.isNotBlank() == true -> AdbConnectionMode.LAN
            else -> AdbConnectionMode.REMOTE
        }
        val firstTime = !container.tunnelConfigRepository.hasSeenOnboarding()
        return DashboardUiState(
            serverUrlInput = saved.serverUrl,
            deviceAliasInput = saved.deviceAlias,
            localAdbPortInput = saved.localAdbPort.toString(),
            autoReconnect = saved.autoReconnectOnNetworkChange,
            runInBackground = saved.runInBackground,
            trustedHomeSsidInput = saved.trustedHomeSsid,
            autoSwitchOnHomeWifi = saved.autoSwitchOnHomeWifi,
            currentWifiSsid = container.reverseTunnelGateway.getCurrentWifiSsid().orEmpty(),
            localWifiIp = container.reverseTunnelGateway.getLocalWifiIp().orEmpty(),
            isUsbConnected = usb,
            usbConnectedAtEpochMs = if (usb) System.currentTimeMillis() else 0L,
            selectedMode = initMode,
            remoteRoomCode = saved.remoteRoomCode,
            notifyConnectionChange = saved.notifyConnectionChange,
            themeMode = saved.themeMode,
            activeOnboardingStep = if (firstTime) OnboardingStep.STEP_ADB else null
        )
    }

    override fun onCleared() {
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
