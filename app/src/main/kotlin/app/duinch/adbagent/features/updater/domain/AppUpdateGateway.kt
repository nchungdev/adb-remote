package app.duinch.adbagent.features.updater.domain

interface AppUpdateGateway {
    val installedVersionCode: Int
    val installedVersionName: String

    suspend fun fetchLatestRelease(
        originHttpsUrl: String,
        cfAuthCookie: String,
        cfClientId: String,
        cfClientSecret: String
    ): Result<AppReleaseInfo>

    suspend fun downloadReleaseApk(
        release: AppReleaseInfo,
        cfAuthCookie: String,
        cfClientId: String,
        cfClientSecret: String,
        onProgress: (Int) -> Unit
    ): Result<String>

    fun canRequestPackageInstalls(): Boolean

    fun triggerPackageInstall(localFilePath: String): Result<Unit>
}
