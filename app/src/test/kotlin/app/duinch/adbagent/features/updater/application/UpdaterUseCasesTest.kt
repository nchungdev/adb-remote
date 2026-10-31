package app.duinch.adbagent.features.updater.application

import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.updater.domain.AppReleaseInfo
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
import app.duinch.adbagent.features.updater.domain.UpdateStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdaterUseCasesTest {

    private class FakeTunnelConfigRepo : TunnelConfigRepository {
        private var stored = TunnelConfig(cfAuthCookie = "CF_Authorization=jwt")
        override fun getConfig(): TunnelConfig = stored
        override fun saveConfig(config: TunnelConfig) {
            stored = config
        }
    }

    private class FakeAppUpdateGateway(
        override val installedVersionCode: Int = 2,
        override val installedVersionName: String = "0.2.0",
        var remoteRelease: AppReleaseInfo = AppReleaseInfo(
            versionCode = 3,
            versionName = "0.3.0",
            downloadUrl = "https://adb.duinch.app/download/adb-agent.apk",
            sha256 = "abc123",
            changelog = "In-app OTA update"
        ),
        var allowInstalls: Boolean = true
    ) : AppUpdateGateway {
        var triggeredPath: String? = null

        override suspend fun fetchLatestRelease(
            originHttpsUrl: String,
            cfAuthCookie: String,
            cfClientId: String,
            cfClientSecret: String
        ): Result<AppReleaseInfo> = Result.success(remoteRelease)

        override suspend fun downloadReleaseApk(
            release: AppReleaseInfo,
            cfAuthCookie: String,
            cfClientId: String,
            cfClientSecret: String,
            onProgress: (Int) -> Unit
        ): Result<String> {
            onProgress(50)
            onProgress(100)
            return Result.success("/cache/updates/adb-agent-v${release.versionName}.apk")
        }

        override fun canRequestPackageInstalls(): Boolean = allowInstalls

        override fun triggerPackageInstall(localFilePath: String): Result<Unit> {
            triggeredPath = localFilePath
            return Result.success(Unit)
        }
    }

    @Test
    fun `detects available update when remote versionCode exceeds installed`() = runTest {
        val gateway = FakeAppUpdateGateway(installedVersionCode = 2)
        val check = CheckAppUpdateUseCase(gateway, FakeTunnelConfigRepo())

        val status = check()

        val available = assertIs<UpdateStatus.Available>(status)
        assertEquals("0.3.0", available.release.versionName)
    }

    @Test
    fun `reports up to date when remote versionCode matches installed`() = runTest {
        val gateway = FakeAppUpdateGateway(installedVersionCode = 3, installedVersionName = "0.3.0")
        val check = CheckAppUpdateUseCase(gateway, FakeTunnelConfigRepo())

        val status = check()

        assertEquals(UpdateStatus.UpToDate("0.3.0"), status)
    }

    @Test
    fun `downloads apk with progress and triggers installer automatically when permitted`() = runTest {
        val gateway = FakeAppUpdateGateway(allowInstalls = true)
        val download = DownloadAndInstallUpdateUseCase(gateway, FakeTunnelConfigRepo())
        val progressSeen = mutableListOf<Int>()

        val status = download(gateway.remoteRelease) { progressSeen += it }

        val ready = assertIs<UpdateStatus.ReadyToInstall>(status)
        assertEquals(listOf(50, 100), progressSeen)
        assertEquals(false, ready.needsInstallPermission)
        assertEquals("/cache/updates/adb-agent-v0.3.0.apk", gateway.triggeredPath)
    }
}
