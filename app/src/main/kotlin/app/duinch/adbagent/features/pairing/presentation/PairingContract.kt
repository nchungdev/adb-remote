package app.duinch.adbagent.features.pairing.presentation

import app.duinch.adbagent.features.pairing.domain.AdbPairingState

data class PairingUiState(
    val pairingPortInput: String = "",
    val connectPortInput: String = "",
    val pairingCodeInput: String = "",
    val discoveredPairingPort: Int? = null,
    val discoveredConnectPort: Int? = null,
    val pairingState: AdbPairingState = AdbPairingState.Idle
) {
    val canSubmitPair: Boolean
        get() = pairingCodeInput.length == 6 &&
            (pairingPortInput.toIntOrNull() ?: discoveredPairingPort) != null &&
            pairingState !is AdbPairingState.PairingInProgress

    val effectiveConnectPort: Int?
        get() = connectPortInput.toIntOrNull() ?: discoveredConnectPort
}

sealed interface PairingUiIntent {
    data class UpdatePairingPort(val value: String) : PairingUiIntent
    data class UpdateConnectPort(val value: String) : PairingUiIntent
    data class UpdatePairingCode(val value: String) : PairingUiIntent
    data object SubmitPair : PairingUiIntent
    data object StartNotificationPairing : PairingUiIntent
    data object StartInteractiveWatcher : PairingUiIntent
    data object ActivateTcpip5555 : PairingUiIntent
}
