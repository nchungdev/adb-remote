package app.duinch.adbagent.features.termux.presentation

import app.duinch.adbagent.features.termux.domain.BootstrapState

data class TermuxUiState(
    val isSheetVisible: Boolean = false,
    val bootstrapState: BootstrapState = BootstrapState.NOT_INSTALLED,
    val commandInput: String = "",
    val isRunningCommand: Boolean = false,
    val logs: List<String> = emptyList()
)

sealed interface TermuxUiIntent {
    data object OpenConsole : TermuxUiIntent
    data object CloseConsole : TermuxUiIntent
    data class UpdateCommandInput(val value: String) : TermuxUiIntent
    data object SubmitCommand : TermuxUiIntent
    data object ClearLogs : TermuxUiIntent
}

sealed interface TermuxUiEffect {
    data class CommandError(val message: String) : TermuxUiEffect
}
