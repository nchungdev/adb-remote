package app.duinch.adbagent.features.updater.application

import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
import app.duinch.adbagent.features.updater.domain.UpdateStatus

class CheckAppUpdateUseCase(
    private val updateGateway: AppUpdateGateway
) {
    suspend operator fun invoke(repository: String = AppUpdateGateway.DEFAULT_REPOSITORY): UpdateStatus {
        return updateGateway.fetchLatestRelease(repository).fold(
            onSuccess = { release ->
                val isNewer = isUpdateAvailable(
                    remoteVersionName = release.versionName,
                    remoteVersionCode = release.versionCode,
                    installedVersionName = updateGateway.installedVersionName,
                    installedVersionCode = updateGateway.installedVersionCode
                )
                if (isNewer) {
                    UpdateStatus.Available(release)
                } else {
                    UpdateStatus.UpToDate(updateGateway.installedVersionName)
                }
            },
            onFailure = { err ->
                UpdateStatus.Failed(err.message ?: "Không thể kiểm tra cập nhật từ GitHub Releases")
            }
        )
    }

    companion object {
        fun isUpdateAvailable(
            remoteVersionName: String,
            remoteVersionCode: Int,
            installedVersionName: String,
            installedVersionCode: Int
        ): Boolean {
            if (isNewerVersion(remoteVersionName, installedVersionName)) return true
            if (isNewerVersion(installedVersionName, remoteVersionName)) return false
            return remoteVersionCode > installedVersionCode
        }

        fun isNewerVersion(remote: String, local: String): Boolean {
            val rParts = remote.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
            val lParts = local.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
            val maxLen = maxOf(rParts.size, lParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            return false
        }
    }
}
