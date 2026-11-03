package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.ServerApprovalGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LATENCY_PROBE_INTERVAL_MS = 5_000L

/** Measures round trips to every connected server for the slot cards. */
internal fun CoroutineScope.launchLatencyProbes(
    gateway: ServerApprovalGateway,
    uiState: MutableStateFlow<DashboardUiState>
) = launch {
    while (isActive) {
        val latest = connectedServers(uiState.value).associateWith { gateway.measureLatencyMs(it) }
        uiState.update { it.copy(serverLatencyMs = latest) }
        delay(LATENCY_PROBE_INTERVAL_MS)
    }
}

private fun connectedServers(state: DashboardUiState): Set<String> = buildSet {
    if (state.connectionState is TunnelConnectionState.Connected) add(state.serverUrlInput)
    state.extraSlots.filter { it.connectionState is TunnelConnectionState.Connected }.forEach { add(it.serverUrlInput) }
}
