package app.duinch.adbagent.features.tunnel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
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

    init {
        viewModelScope.launch { container.ensureTermuxBootstrap() }
        container.adbDiscoveryGateway.startDiscovery()
        observeFlows()
    }

    fun onIntent(intent: DashboardUiIntent) {
        when (intent) {
            is DashboardUiIntent.UpdateServerUrl ->
                _uiState.update { it.copy(serverUrlInput = intent.value) }
            is DashboardUiIntent.UpdateDeviceAlias ->
                _uiState.update { it.copy(deviceAliasInput = intent.value) }
            is DashboardUiIntent.UpdateLocalAdbPort ->
                _uiState.update { it.copy(localAdbPortInput = intent.value.filter(Char::isDigit).take(5)) }
            is DashboardUiIntent.UpdateCfClientId ->
                _uiState.update { it.copy(cfClientIdInput = intent.value) }
            is DashboardUiIntent.UpdateCfClientSecret ->
                _uiState.update { it.copy(cfClientSecretInput = intent.value) }
            is DashboardUiIntent.ToggleAutoReconnect ->
                _uiState.update { it.copy(autoReconnect = intent.enabled) }
            DashboardUiIntent.ToggleAdvancedAuth ->
                _uiState.update { it.copy(showAdvancedAuth = !it.showAdvancedAuth) }
            DashboardUiIntent.SaveConfig -> saveCurrentConfig()
            DashboardUiIntent.ToggleTunnel -> toggleTunnel()
            is DashboardUiIntent.OnPairingIntent -> handlePairingIntent(intent.intent)
            is DashboardUiIntent.OnTermuxIntent -> handleTermuxIntent(intent.intent)
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
                _uiState.update { it.copy(activeNetwork = net) }
            }
        }
        viewModelScope.launch {
            container.adbDiscoveryGateway.observeDiscoveredPorts().collect { ports ->
                _uiState.update { cur ->
                    val updatedLocalPort = ports.connectPort?.toString() ?: cur.localAdbPortInput
                    cur.copy(
                        localAdbPortInput = updatedLocalPort,
                        pairing = cur.pairing.copy(
                            discoveredPairingPort = ports.pairingPort,
                            discoveredConnectPort = ports.connectPort
                        )
                    )
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
        when (intent) {
            is PairingUiIntent.UpdatePairingPort -> _uiState.update { cur ->
                cur.copy(pairing = cur.pairing.copy(pairingPortInput = intent.value.filter(Char::isDigit).take(5)))
            }
            is PairingUiIntent.UpdateConnectPort -> _uiState.update { cur ->
                cur.copy(pairing = cur.pairing.copy(connectPortInput = intent.value.filter(Char::isDigit).take(5)))
            }
            is PairingUiIntent.UpdatePairingCode -> _uiState.update { cur ->
                cur.copy(pairing = cur.pairing.copy(pairingCodeInput = intent.value.filter(Char::isDigit).take(6)))
            }
            PairingUiIntent.OpenDeveloperOptions -> {
                _effects.tryEmit(DashboardUiEffect.LaunchDeveloperSettings)
            }
            PairingUiIntent.SubmitPair -> submitPairing()
        }
    }

    private fun submitPairing() {
        val snap = _uiState.value.pairing
        val pairPort = snap.pairingPortInput.toIntOrNull() ?: snap.discoveredPairingPort ?: return
        val connectPort = snap.connectPortInput.toIntOrNull() ?: snap.discoveredConnectPort

        _uiState.update { cur ->
            cur.copy(pairing = cur.pairing.copy(pairingState = AdbPairingState.PairingInProgress))
        }
        viewModelScope.launch {
            val result = container.pairLocalAdb(pairPort, snap.pairingCodeInput, connectPort)
            result.fold(
                onSuccess = { paired ->
                    _uiState.update { cur ->
                        cur.copy(
                            localAdbPortInput = paired.connectPort.toString(),
                            pairing = cur.pairing.copy(
                                pairingCodeInput = "",
                                pairingState = paired
                            )
                        )
                    }
                    _effects.emit(DashboardUiEffect.ShowMessage("Đã ghép đôi & nối cổng ${paired.connectPort}"))
                },
                onFailure = { err ->
                    _uiState.update { cur ->
                        cur.copy(
                            pairing = cur.pairing.copy(
                                pairingState = AdbPairingState.Failed(err.message ?: "Lỗi ghép đôi")
                            )
                        )
                    }
                    _effects.emit(DashboardUiEffect.ShowMessage(err.message ?: "Ghép đôi thất bại"))
                }
            )
        }
    }

    private fun handleTermuxIntent(intent: TermuxUiIntent) {
        when (intent) {
            TermuxUiIntent.OpenConsole ->
                _uiState.update { cur -> cur.copy(termux = cur.termux.copy(isSheetVisible = true)) }
            TermuxUiIntent.CloseConsole ->
                _uiState.update { cur -> cur.copy(termux = cur.termux.copy(isSheetVisible = false)) }
            is TermuxUiIntent.UpdateCommandInput ->
                _uiState.update { cur -> cur.copy(termux = cur.termux.copy(commandInput = intent.value)) }
            TermuxUiIntent.ClearLogs -> container.termuxRuntimeGateway.clearConsoleLogs()
            TermuxUiIntent.SubmitCommand -> executeConsoleCommand()
        }
    }

    private fun executeConsoleCommand() {
        val cmd = _uiState.value.termux.commandInput
        _uiState.update { cur ->
            cur.copy(termux = cur.termux.copy(commandInput = "", isRunningCommand = true))
        }
        viewModelScope.launch {
            container.runTermuxCommand(cmd)
            _uiState.update { cur -> cur.copy(termux = cur.termux.copy(isRunningCommand = false)) }
        }
    }

    private fun saveCurrentConfig() {
        val draft = buildDraftConfig(_uiState.value)
        container.saveTunnelConfig(draft).fold(
            onSuccess = { _effects.tryEmit(DashboardUiEffect.ShowMessage("Đã lưu cấu hình đường hầm")) },
            onFailure = { err -> _effects.tryEmit(DashboardUiEffect.ShowMessage(err.message ?: "Cấu hình không hợp lệ")) }
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
        return DashboardUiState(
            serverUrlInput = saved.serverUrl,
            deviceAliasInput = saved.deviceAlias,
            localAdbPortInput = saved.localAdbPort.toString(),
            cfClientIdInput = saved.cfAccessClientId,
            cfClientSecretInput = saved.cfAccessClientSecret,
            autoReconnect = saved.autoReconnectOnNetworkChange
        )
    }

    private fun buildDraftConfig(state: DashboardUiState) = TunnelConfig(
        serverUrl = state.serverUrlInput,
        deviceAlias = state.deviceAliasInput,
        localAdbPort = state.localAdbPortInput.toIntOrNull() ?: 5555,
        cfAccessClientId = state.cfClientIdInput,
        cfAccessClientSecret = state.cfClientSecretInput,
        autoReconnectOnNetworkChange = state.autoReconnect
    )

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
