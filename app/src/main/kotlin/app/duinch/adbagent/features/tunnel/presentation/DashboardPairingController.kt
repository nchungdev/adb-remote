package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DashboardPairingController(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<DashboardUiState>,
    private val effects: MutableSharedFlow<DashboardUiEffect>,
    private val onSaveConfig: () -> Unit
) {
    fun handleIntent(intent: PairingUiIntent) {
        when (intent) {
            is PairingUiIntent.UpdatePairingPort -> uiState.update { cur ->
                cur.copy(pairing = cur.pairing.copy(pairingPortInput = intent.value.filter(Char::isDigit).take(5)))
            }
            is PairingUiIntent.UpdateConnectPort -> uiState.update { cur ->
                cur.copy(pairing = cur.pairing.copy(connectPortInput = intent.value.filter(Char::isDigit).take(5)))
            }
            is PairingUiIntent.UpdatePairingCode -> uiState.update { cur ->
                cur.copy(pairing = cur.pairing.copy(pairingCodeInput = intent.value.filter(Char::isDigit).take(6)))
            }
            PairingUiIntent.StartNotificationPairing -> {
                container.pairingNotificationGateway.startInteractivePairing()
                effects.tryEmit(DashboardUiEffect.LaunchDeveloperSettings)
            }
            PairingUiIntent.ActivateTcpip5555 -> activateTcpipMode()
            PairingUiIntent.SubmitPair -> submitPairing()
        }
    }

    private fun activateTcpipMode() {
        val port = uiState.value.pairing.effectiveConnectPort ?: return
        uiState.update { it.copy(pairing = it.pairing.copy(pairingState = AdbPairingState.PairingInProgress)) }
        scope.launch {
            container.activateTcpipMode(port).fold(
                onSuccess = { paired ->
                    uiState.update { cur ->
                        cur.copy(
                            localAdbPortInput = paired.connectPort.toString(),
                            pairing = cur.pairing.copy(pairingState = paired)
                        )
                    }
                    onSaveConfig()
                    effects.emit(DashboardUiEffect.ShowMessage("Đã bật lại ADB thành công!"))
                },
                onFailure = { err ->
                    uiState.update { cur ->
                        cur.copy(pairing = cur.pairing.copy(pairingState = AdbPairingState.Failed(err.message ?: "Lỗi")))
                    }
                    effects.emit(DashboardUiEffect.ShowMessage(err.message ?: "Không thể bật lại ADB"))
                }
            )
        }
    }

    private fun submitPairing() {
        val snap = uiState.value.pairing
        val pairPort = snap.pairingPortInput.toIntOrNull() ?: snap.discoveredPairingPort ?: return
        val connectPort = snap.effectiveConnectPort

        uiState.update { it.copy(pairing = it.pairing.copy(pairingState = AdbPairingState.PairingInProgress)) }
        scope.launch {
            container.pairLocalAdb(pairPort, snap.pairingCodeInput, connectPort).fold(
                onSuccess = { paired ->
                    uiState.update { cur ->
                        cur.copy(
                            localAdbPortInput = paired.connectPort.toString(),
                            pairing = cur.pairing.copy(pairingCodeInput = "", pairingState = paired)
                        )
                    }
                    onSaveConfig()
                    effects.emit(DashboardUiEffect.ShowMessage("Ghép nối ADB thành công!"))
                },
                onFailure = { err ->
                    uiState.update { cur ->
                        cur.copy(pairing = cur.pairing.copy(pairingState = AdbPairingState.Failed(err.message ?: "Lỗi")))
                    }
                    effects.emit(DashboardUiEffect.ShowMessage(err.message ?: "Ghép nối thất bại"))
                }
            )
        }
    }
}
