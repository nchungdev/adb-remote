package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun buildDraftConfig(state: DashboardUiState) = TunnelConfig(
    serverUrl = state.serverUrlInput,
    deviceAlias = state.deviceAliasInput,
    localAdbPort = state.localAdbPortInput.toIntOrNull() ?: 5555,
    autoReconnectOnNetworkChange = state.autoReconnect,
    runInBackground = state.runInBackground,
    trustedHomeSsid = state.trustedHomeSsidInput,
    autoSwitchOnHomeWifi = state.autoSwitchOnHomeWifi,
    remoteRoomCode = state.remoteRoomCode
)

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
