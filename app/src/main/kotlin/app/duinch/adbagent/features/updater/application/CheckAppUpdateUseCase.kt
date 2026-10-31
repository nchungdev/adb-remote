package app.duinch.adbagent.features.updater.application

import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
import app.duinch.adbagent.features.updater.domain.CloudflareAuthRequiredException
import app.duinch.adbagent.features.updater.domain.UpdateStatus

class CheckAppUpdateUseCase(
    private val updateGateway: AppUpdateGateway,
    private val tunnelConfigRepository: TunnelConfigRepository
) {
    suspend operator fun invoke(): UpdateStatus {
        val cfg = tunnelConfigRepository.getConfig()
        val httpsOrigin = cfg.serverUrl.trim()
            .replaceFirst("wss://", "https://")
            .substringBefore("/api/")
            .ifBlank { "https://adb.duinch.app" }

        return updateGateway.fetchLatestRelease(
            originHttpsUrl = httpsOrigin,
            cfAuthCookie = cfg.cfAuthCookie,
            cfClientId = cfg.cfAccessClientId,
            cfClientSecret = cfg.cfAccessClientSecret
        ).fold(
            onSuccess = { release ->
                if (release.versionCode > updateGateway.installedVersionCode) {
                    UpdateStatus.Available(release)
                } else {
                    UpdateStatus.UpToDate(updateGateway.installedVersionName)
                }
            },
            onFailure = { err ->
                if (err is CloudflareAuthRequiredException) {
                    UpdateStatus.AuthRequired
                } else {
                    UpdateStatus.Failed(err.message ?: "Không thể kiểm tra cập nhật")
                }
            }
        )
    }
}
