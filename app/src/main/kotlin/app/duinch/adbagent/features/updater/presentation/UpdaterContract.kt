package app.duinch.adbagent.features.updater.presentation

import app.duinch.adbagent.features.updater.domain.UpdateStatus

data class UpdaterUiState(
    val currentVersionName: String = "",
    val currentVersionCode: Int = 1,
    val status: UpdateStatus = UpdateStatus.Idle
)

sealed interface UpdaterUiIntent {
    data object CheckForUpdate : UpdaterUiIntent
    data object StartUpdate : UpdaterUiIntent
    data object InstallDownloadedApk : UpdaterUiIntent
    data object GrantInstallPermission : UpdaterUiIntent
}

sealed interface UpdaterUiEffect {
    data class ShowMessage(val text: String) : UpdaterUiEffect
    data object OpenInstallUnknownAppsSettings : UpdaterUiEffect
    data object RequestCloudflareLogin : UpdaterUiEffect
}
