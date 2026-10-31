package app.duinch.adbagent.features.updater.domain

data class AppReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val changelog: String
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class UpToDate(val versionName: String) : UpdateStatus
    data class Available(val release: AppReleaseInfo) : UpdateStatus
    data class Downloading(val release: AppReleaseInfo, val progressPercent: Int) : UpdateStatus
    data class ReadyToInstall(
        val release: AppReleaseInfo,
        val localFilePath: String,
        val needsInstallPermission: Boolean
    ) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}
