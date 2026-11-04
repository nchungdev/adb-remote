package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val ADB_PORT_CHECK_INTERVAL_MS = 5_000L
private const val DEFAULT_ADB_PORT = 5555

/** Keeps the ADB status truthful: the port outlives app restarts and closes on device reboots. */
internal fun CoroutineScope.launchAdbPortWatch(
    gateway: AdbPairingGateway,
    uiState: MutableStateFlow<DashboardUiState>
) = launch {
    while (isActive) {
        val port = adbPortToCheck(uiState.value)
        val isOpen = gateway.isLocalPortOpen(port)
        uiState.update { cur ->
            cur.copy(pairing = cur.pairing.copy(pairingState = reconcileAdbState(cur.pairing.pairingState, isOpen, port)))
        }
        delay(ADB_PORT_CHECK_INTERVAL_MS)
    }
}

private fun adbPortToCheck(state: DashboardUiState): Int =
    (state.pairing.pairingState as? AdbPairingState.PairedAndConnected)?.connectPort
        ?: state.localAdbPortInput.toIntOrNull()
        ?: DEFAULT_ADB_PORT

internal fun reconcileAdbState(current: AdbPairingState, isPortOpen: Boolean, port: Int): AdbPairingState = when {
    current is AdbPairingState.PairingInProgress -> current
    isPortOpen -> AdbPairingState.PairedAndConnected(port)
    current is AdbPairingState.PairedAndConnected -> AdbPairingState.Idle
    else -> current
}
