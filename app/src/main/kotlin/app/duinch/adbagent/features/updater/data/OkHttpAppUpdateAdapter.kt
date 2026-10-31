package app.duinch.adbagent.features.updater.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.duinch.adbagent.BuildConfig
import app.duinch.adbagent.features.updater.domain.AppReleaseInfo
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway
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
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getGitHubToken(): String {
        val saved = prefs.getString(KEY_GITHUB_TOKEN, null)
        if (!saved.isNullOrBlank()) return saved
        return BuildConfig.GITHUB_TOKEN.trim()
    }

    override fun setGitHubToken(token: String) {
        prefs.edit().putString(KEY_GITHUB_TOKEN, token.trim()).apply()
    }

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override val installedVersionCode: Int = BuildConfig.VERSION_CODE
    override val installedVersionName: String = BuildConfig.VERSION_NAME

    override suspend fun fetchLatestRelease(
        repository: String
    ): Result<AppReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val targetRepo = repository.trim().ifBlank { AppUpdateGateway.DEFAULT_REPOSITORY }
            val url = "https://api.github.com/repos/$targetRepo/releases/latest"
            val token = getGitHubToken()
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ADB-Agent/$installedVersionName")
            if (token.isNotEmpty()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            val req = reqBuilder.build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    val authHint = if (token.isEmpty()) " Bạn có thể nhập GitHub Token trong phần Cập nhật." else ""
                    error("Không tìm thấy bản phát hành trên GitHub (hoặc repository '$targetRepo' đang ở chế độ Private).$authHint")
                }
                if (!resp.isSuccessful) {
                    error("GitHub API trả về lỗi HTTP ${resp.code}")
                }
                val rawBody = resp.body?.string().orEmpty()
                val obj = JSONObject(rawBody)
                val tagName = obj.getString("tag_name")
                val versionName = tagName.removePrefix("v").trim()

                val assets = obj.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            // If token is present, use asset API URL; otherwise use browser_download_url
                            apkUrl = if (token.isNotEmpty() && asset.has("url")) {
                                asset.getString("url")
                            } else {
                                asset.getString("browser_download_url")
                            }
                            break
                        }
                    }
                }
                if (apkUrl.isNullOrBlank()) {
                    error("Bản phát hành $tagName trên GitHub không có tệp APK đính kèm")
                }

                val changelog = obj.optString("body", "Bản cập nhật $tagName từ GitHub Releases.")
                val derivedVersionCode = parseDerivedVersionCode(versionName, changelog)

                AppReleaseInfo(
                    versionCode = derivedVersionCode,
                    versionName = versionName,
                    downloadUrl = apkUrl,
                    sha256 = extractSha256(changelog),
                    changelog = changelog
                )
            }
        }
    }

    private fun parseDerivedVersionCode(versionName: String, changelog: String): Int {
        val codeMatch = Regex("versionCode:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(changelog)
        if (codeMatch != null) {
            val code = codeMatch.groupValues[1].toIntOrNull()
            if (code != null) return code
        }
        val parts = versionName.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return major * 10000 + minor * 100 + patch
    }

    private fun extractSha256(text: String): String {
        val regex = Regex("\\b[a-fA-F0-9]{64}\\b")
        return regex.find(text)?.value?.lowercase().orEmpty()
    }

    override suspend fun downloadReleaseApk(
        release: AppReleaseInfo,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getGitHubToken()
            val reqBuilder = Request.Builder()
                .url(release.downloadUrl)
                .header("User-Agent", "ADB-Agent/$installedVersionName")
                .header("Accept", "application/octet-stream")
            if (token.isNotEmpty() && release.downloadUrl.contains("api.github.com")) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            val req = reqBuilder.build()
            val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { runCatching { it.delete() } }
            val outFile = File(updatesDir, "adb-agent-v${release.versionName}.apk")

            httpClient.newCall(req).execute().use { resp ->
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
                    if (!actualHex.equals(release.sha256, ignoreCase = true)) {
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

    companion object {
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_GITHUB_TOKEN = "github_token"
    }
}
