package app.duinch.adbagent.features.updater.application

import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.updater.domain.AppReleaseInfo
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
import app.duinch.adbagent.features.updater.domain.CloudflareAuthRequiredException
import app.duinch.adbagent.features.updater.domain.UpdateStatus

class DownloadAndInstallUpdateUseCase(
    private val updateGateway: AppUpdateGateway,
    private val tunnelConfigRepository: TunnelConfigRepository
) {
    suspend operator fun invoke(
        release: AppReleaseInfo,
        onProgress: (Int) -> Unit
    ): UpdateStatus {
        val cfg = tunnelConfigRepository.getConfig()
        val downloadRes = updateGateway.downloadReleaseApk(
            release = release,
            cfAuthCookie = cfg.cfAuthCookie,
            cfClientId = cfg.cfAccessClientId,
            cfClientSecret = cfg.cfAccessClientSecret,
            onProgress = onProgress
        )

        return downloadRes.fold(
            onSuccess = { localPath ->
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
                if (err is CloudflareAuthRequiredException) {
                    UpdateStatus.AuthRequired
                } else {
                    UpdateStatus.Failed(err.message ?: "Tải bản cập nhật thất bại")
                }
            }
        )
    }
}
