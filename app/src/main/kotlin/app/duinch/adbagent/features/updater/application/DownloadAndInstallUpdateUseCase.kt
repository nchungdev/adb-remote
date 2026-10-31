package app.duinch.adbagent.features.updater.application

import app.duinch.adbagent.features.updater.domain.AppReleaseInfo
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
import app.duinch.adbagent.features.updater.domain.UpdateStatus

class DownloadAndInstallUpdateUseCase(
    private val updateGateway: AppUpdateGateway
) {
    suspend operator fun invoke(
        release: AppReleaseInfo,
        onProgress: (Int) -> Unit
    ): UpdateStatus {
        val downloadRes = updateGateway.downloadReleaseApk(
            release = release,
            onProgress = onProgress
        )

        return downloadRes.fold(
            onSuccess = { localPath ->
                val silentRes = updateGateway.installPackageSilently(localPath)
                if (silentRes.isSuccess) {
                    return@fold UpdateStatus.UpToDate(release.versionName)
                }

                val canInstall = updateGateway.canRequestPackageInstalls()
                if (canInstall) {
                    updateGateway.triggerPackageInstall(localPath)
                }
                UpdateStatus.ReadyToInstall(
                    release = release,
                    localFilePath = localPath,
                    needsInstallPermission = !canInstall
                )
            },
            onFailure = { err ->
                UpdateStatus.Failed(err.message ?: "Tải bản cập nhật thất bại")
            }
        )
    }
}
