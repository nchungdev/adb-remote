package app.duinch.adbagent.features.updater.application

import app.duinch.adbagent.features.updater.domain.AppReleaseInfo
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
import app.duinch.adbagent.features.updater.domain.UpdateStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdaterUseCasesTest {

    private class FakeAppUpdateGateway(
        override val installedVersionCode: Int = 2,
        override val installedVersionName: String = "0.2.0",
        var remoteRelease: AppReleaseInfo = AppReleaseInfo(
            versionCode = 3,
            versionName = "0.3.0",
            downloadUrl = "https://github.com/nchungdev/adb-agent/releases/download/v0.3.0/app-debug.apk",
            sha256 = "abc123",
            changelog = "GitHub Releases OTA update"
        ),
        var allowInstalls: Boolean = true,
        var currentToken: String = ""
    ) : AppUpdateGateway {
        var triggeredPath: String? = null

        override fun getGitHubToken(): String = currentToken

        override fun setGitHubToken(token: String) {
            currentToken = token
        }

        override suspend fun fetchLatestRelease(
            repository: String
        ): Result<AppReleaseInfo> = Result.success(remoteRelease)

        override suspend fun downloadReleaseApk(
            release: AppReleaseInfo,
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
        val check = CheckAppUpdateUseCase(gateway)

        val status = check()

        val available = assertIs<UpdateStatus.Available>(status)
        assertEquals("0.3.0", available.release.versionName)
    }

    @Test
    fun `reports up to date when remote versionCode matches installed`() = runTest {
        val gateway = FakeAppUpdateGateway(installedVersionCode = 3, installedVersionName = "0.3.0")
        val check = CheckAppUpdateUseCase(gateway)

        val status = check()

        assertEquals(UpdateStatus.UpToDate("0.3.0"), status)
    }

    @Test
    fun `downloads apk with progress and triggers installer automatically when permitted`() = runTest {
        val gateway = FakeAppUpdateGateway(allowInstalls = true)
        val download = DownloadAndInstallUpdateUseCase(gateway)
        val progressSeen = mutableListOf<Int>()

        val status = download(gateway.remoteRelease) { progressSeen += it }

        val ready = assertIs<UpdateStatus.ReadyToInstall>(status)
        assertEquals(listOf(50, 100), progressSeen)
        assertEquals(false, ready.needsInstallPermission)
        assertEquals("/cache/updates/adb-agent-v0.3.0.apk", gateway.triggeredPath)
    }
}
