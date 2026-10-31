package app.duinch.adbagent.features.updater.domain

interface AppUpdateGateway {
    val installedVersionCode: Int
    val installedVersionName: String

    suspend fun fetchLatestRelease(
        repository: String = DEFAULT_REPOSITORY
    ): Result<AppReleaseInfo>

    suspend fun downloadReleaseApk(
        release: AppReleaseInfo,
        onProgress: (Int) -> Unit
    ): Result<String>

    fun canRequestPackageInstalls(): Boolean

    fun triggerPackageInstall(localFilePath: String): Result<Unit>

    companion object {
        const val DEFAULT_REPOSITORY = "nchungdev/adb-agent"
    }
}
