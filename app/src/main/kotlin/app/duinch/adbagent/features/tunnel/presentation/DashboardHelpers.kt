package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun createInitialDashboardState(container: AppContainer): DashboardUiState {
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

internal fun buildDraftConfig(state: DashboardUiState) = TunnelConfig(
    serverUrl = state.serverUrlInput,
    deviceAlias = state.deviceAliasInput,
    localAdbPort = state.localAdbPortInput.toIntOrNull() ?: 5555,
    autoReconnectOnNetworkChange = state.autoReconnect,
    runInBackground = state.runInBackground,
    trustedHomeSsid = state.trustedHomeSsidInput,
    autoSwitchOnHomeWifi = state.autoSwitchOnHomeWifi,
    remoteRoomCode = state.remoteRoomCode,
    notifyConnectionChange = state.notifyConnectionChange,
    themeMode = state.themeMode
)

internal fun resolveQrRemoteTarget(
    intent: DashboardUiIntent.ApplyQrRemoteConfig,
    currentServer: String,
    currentRoom: String
): Pair<String, String> {
    val isUrl = intent.roomCode.startsWith("http://") || intent.roomCode.startsWith("https://") || intent.roomCode.contains("trycloudflare.com")
    val finalServer = when {
        intent.serverUrl?.isNotBlank() == true -> intent.serverUrl
        isUrl -> intent.roomCode
        else -> currentServer
    }
    val cleanStateRoom = currentRoom.takeIf { !it.contains("://") && !it.contains("trycloudflare.com") }.orEmpty()
    val finalRoom = if (isUrl) cleanStateRoom else if (intent.roomCode.contains("://") || intent.roomCode.contains("trycloudflare.com")) "" else intent.roomCode
    return finalServer to finalRoom
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
