package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.tunnel.domain.ServerApprovalGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.remotetunnel.ui.ConnectMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun createInitialDashboardState(container: AppContainer): DashboardUiState {
    val saved = container.tunnelConfigRepository.getConfig()
    val usb = container.reverseTunnelGateway.isUsbConnected()
    val hasConfiguredServer = saved.serverUrl.isNotBlank()
    val primaryMode = if (saved.serverUrl.contains("trycloudflare.com")) {
        ConnectMode.Remote
    } else {
        ConnectMode.Lan
    }
    val firstTime = !container.tunnelConfigRepository.hasSeenOnboarding()
    return DashboardUiState(
        serverUrlInput = saved.serverUrl,
        deviceAliasInput = saved.deviceAlias,
        localAdbPortInput = saved.localAdbPort.toString(),
        runInBackground = saved.runInBackground,
        currentWifiSsid = container.reverseTunnelGateway.getCurrentWifiSsid().orEmpty(),
        localWifiIp = container.reverseTunnelGateway.getLocalWifiIp().orEmpty(),
        isUsbConnected = usb,
        usbConnectedAtEpochMs = if (usb) System.currentTimeMillis() else 0L,
        notifyConnectionChange = saved.notifyConnectionChange,
        themeMode = saved.themeMode,
        hasPrimarySlot = hasConfiguredServer,
        primarySlotMode = primaryMode,
        activeOnboardingStep = if (firstTime) OnboardingStep.STEP_ADB else null
    )
}

internal fun buildDraftConfig(state: DashboardUiState) = TunnelConfig(
    serverUrl = state.serverUrlInput,
    deviceAlias = state.deviceAliasInput,
    localAdbPort = state.localAdbPortInput.toIntOrNull() ?: 5555,
    runInBackground = state.runInBackground,
    notifyConnectionChange = state.notifyConnectionChange,
    themeMode = state.themeMode
)

/**
 * Picks the server from a scanned QR. Its LAN address only works on the host's own network,
 * so it is tried on Wi-Fi only and the tunnel URL covers every other case.
 */
internal suspend fun chooseReachableServer(
    gateway: ServerApprovalGateway,
    isOnWifi: Boolean,
    preferred: String,
    fallback: String?
): String = when {
    fallback.isNullOrBlank() -> preferred
    isOnWifi && gateway.measureLatencyMs(preferred) != null -> preferred
    else -> fallback
}

internal fun handleTermuxAction(
    intent: TermuxUiIntent,
    scope: CoroutineScope,
    uiState: MutableStateFlow<DashboardUiState>,
    runCommand: suspend (String) -> Any?,
    clearLogs: () -> Unit
) {
    when (intent) {
        TermuxUiIntent.OpenConsole ->
            uiState.update { cur -> cur.copy(termux = cur.termux.copy(isSheetVisible = true)) }
        TermuxUiIntent.CloseConsole ->
            uiState.update { cur -> cur.copy(termux = cur.termux.copy(isSheetVisible = false)) }
        is TermuxUiIntent.UpdateCommandInput ->
            uiState.update { cur -> cur.copy(termux = cur.termux.copy(commandInput = intent.value)) }
        TermuxUiIntent.ClearLogs -> clearLogs()
        TermuxUiIntent.SubmitCommand -> {
            val cmd = uiState.value.termux.commandInput
            uiState.update { cur -> cur.copy(termux = cur.termux.copy(commandInput = "", isRunningCommand = true)) }
            scope.launch {
                runCommand(cmd)
                uiState.update { cur -> cur.copy(termux = cur.termux.copy(isRunningCommand = false)) }
            }
        }
    }
}
