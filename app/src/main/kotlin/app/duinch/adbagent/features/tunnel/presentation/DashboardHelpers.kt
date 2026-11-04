package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.tunnel.domain.ServerApprovalGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.tunnel.domain.normalizedServerAddress
import app.duinch.remotetunnel.ui.ConnectMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun createInitialDashboardState(container: AppContainer): DashboardUiState {
    val saved = container.tunnelConfigRepository.getConfig()
    val primary = container.tunnelConfigRepository.getConnections().find { it.id == PRIMARY_CONNECTION_ID }
    val usb = container.reverseTunnelGateway.isUsbConnected()
    val hasConfiguredServer = saved.serverUrl.isNotBlank()
    val primaryMode = connectModeForServer(saved.serverUrl)
    val firstTime = !container.tunnelConfigRepository.hasSeenOnboarding()
    return DashboardUiState(
        serverUrlInput = saved.serverUrl,
        primaryDisplayName = primary?.name,
        primaryServer = primary?.server,
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

// Checks the gateway itself: UI state starts as disconnected even while a tunnel survives
// in the background, and toggling then would drop that live connection.
internal fun restoreConnectionIfWanted(
    container: AppContainer,
    approvals: DashboardServerApprovalController,
    serverUrl: String,
    connect: suspend () -> Unit
) {
    if (!container.tunnelConfigRepository.wantsConnected() || serverUrl.isBlank()) return
    approvals.restoreIfApproved(serverUrl) {
        if (container.reverseTunnelGateway.observeConnectionState().first() is TunnelConnectionState.Disconnected) connect()
    }
}

/** A card's type comes from its address: a LAN address is never a remote connection. */
internal fun connectModeForServer(server: String): ConnectMode =
    if (server.startsWith("https://") || server.contains("trycloudflare.com")) ConnectMode.Remote else ConnectMode.Lan

/**
 * Picks the server from a scanned QR. A remote card must use the tunnel, even when the LAN
 * address also answers; null means the QR has none. Otherwise the LAN address only works on
 * the host's own network, so it is tried on Wi-Fi only and the tunnel covers the rest.
 */
internal suspend fun chooseQrServer(
    gateway: ServerApprovalGateway,
    wantsRemote: Boolean,
    isOnWifi: Boolean,
    preferred: String,
    fallback: String?
): String? = when {
    wantsRemote -> listOfNotNull(fallback, preferred).firstOrNull { connectModeForServer(it) == ConnectMode.Remote }
    fallback.isNullOrBlank() -> preferred
    isOnWifi && gateway.measureLatencyMs(preferred) != null -> preferred
    else -> fallback
}

internal suspend fun chooseQrServerForTarget(
    state: DashboardUiState,
    targetSlotId: String?,
    gateway: ServerApprovalGateway,
    intent: DashboardUiIntent.ApplyQrRemoteConfig
): String? {
    val targetMode = state.extraSlots.find { it.id == targetSlotId }?.mode ?: state.primarySlotMode.takeIf { state.hasPrimarySlot }
    return chooseQrServer(gateway, targetMode == ConnectMode.Remote, state.activeNetwork == NetworkType.WIFI, intent.serverUrl, intent.fallbackServerUrl)
}

internal fun isSameServer(a: String, b: String): Boolean =
    normalizedServerAddress(a).let { it.isNotEmpty() && it == normalizedServerAddress(b) }

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
