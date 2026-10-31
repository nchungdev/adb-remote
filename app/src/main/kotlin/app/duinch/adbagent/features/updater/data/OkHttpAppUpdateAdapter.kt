package app.duinch.adbagent.features.updater.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.duinch.adbagent.BuildConfig
import app.duinch.adbagent.features.updater.domain.AppReleaseInfo
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
import app.duinch.adbagent.features.updater.domain.CloudflareAuthRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class OkHttpAppUpdateAdapter(
    context: Context
) : AppUpdateGateway {

    private val appContext = context.applicationContext

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override val installedVersionCode: Int = BuildConfig.VERSION_CODE
    override val installedVersionName: String = BuildConfig.VERSION_NAME

    override suspend fun fetchLatestRelease(
        originHttpsUrl: String,
        cfAuthCookie: String,
        cfClientId: String,
        cfClientSecret: String
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val base = originHttpsUrl.trimEnd('/')
            val req = buildAuthRequest(
                url = "$base/api/app-version",
                cfAuthCookie = cfAuthCookie,
                cfClientId = cfClientId,
                cfClientSecret = cfClientSecret
            )
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code in listOf(302, 401, 403)) {
                    throw CloudflareAuthRequiredException()
                }
                if (!resp.isSuccessful) {
                    error("Máy chủ trả về mã lỗi HTTP ${resp.code}")
                }
                val rawBody = resp.body?.string().orEmpty()
                if (rawBody.trimStart().startsWith("<")) {
                    throw CloudflareAuthRequiredException()
                }
                val obj = JSONObject(rawBody)
                val downloadPath = obj.optString("downloadUrl", "/download/adb-agent.apk")
                val resolvedUrl = if (downloadPath.startsWith("http")) {
                    downloadPath
                } else {
                    "$base/${downloadPath.trimStart('/')}"
                }
                AppReleaseInfo(
                    versionCode = obj.getInt("versionCode"),
                    versionName = obj.getString("versionName"),
                    downloadUrl = resolvedUrl,
                    sha256 = obj.optString("sha256", "").lowercase(),
                    changelog = obj.optString("changelog", "Bản cập nhật cải tiến hiệu năng & kết nối.")
                )
            }
        }
    }

    override suspend fun downloadReleaseApk(
        release: AppReleaseInfo,
        cfAuthCookie: String,
        cfClientId: String,
        cfClientSecret: String,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = buildAuthRequest(
                url = release.downloadUrl,
                cfAuthCookie = cfAuthCookie,
                cfClientId = cfClientId,
                cfClientSecret = cfClientSecret
            )
            val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { runCatching { it.delete() } }
            val outFile = File(updatesDir, "adb-agent-v${release.versionName}.apk")

            httpClient.newCall(req).execute().use { resp ->
                if (resp.code in listOf(302, 401, 403)) {
                    throw CloudflareAuthRequiredException()
                }
                if (!resp.isSuccessful) {
                    error("Tải APK lỗi HTTP ${resp.code}")
                }
                val body = resp.body ?: error("Phản hồi APK rỗng")
                val totalBytes = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        var lastReported = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }

                if (release.sha256.isNotEmpty()) {
                    val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
                    if (actualHex != release.sha256) {
                        outFile.delete()
                        error("Sai mã kiểm tra SHA-256 của gói APK")
                    }
                }
            }
            outFile.absolutePath
        }
    }

    override fun canRequestPackageInstalls(): Boolean {
        return appContext.packageManager.canRequestPackageInstalls()
    }

    override fun triggerPackageInstall(localFilePath: String): Result<Unit> = runCatching {
        val apkFile = File(localFilePath)
        require(apkFile.isFile) { "Không tìm thấy tệp APK cập nhật" }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        appContext.startActivity(installIntent)
    }

    private fun buildAuthRequest(
        url: String,
        cfAuthCookie: String,
        cfClientId: String,
        cfClientSecret: String
    ): Request {
        val builder = Request.Builder().url(url)
        if (cfAuthCookie.isNotBlank()) {
            builder.addHeader("Cookie", cfAuthCookie.trim())
        }
        if (cfClientId.isNotBlank() && cfClientSecret.isNotBlank()) {
            builder.addHeader("CF-Access-Client-Id", cfClientId.trim())
            builder.addHeader("CF-Access-Client-Secret", cfClientSecret.trim())
        }
        return builder.build()
    }
}
