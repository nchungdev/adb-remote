package app.duinch.adbagent.features.sharing.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import app.duinch.adbagent.features.sharing.domain.FileSendException
import app.duinch.adbagent.features.sharing.domain.FileSendFailure
import app.duinch.adbagent.features.sharing.domain.FileSendGateway
import app.duinch.adbagent.features.sharing.domain.SharedFile
import app.duinch.adbagent.features.tunnel.data.resolveServerBaseUrl
import app.duinch.adbagent.features.tunnel.domain.DeviceSecretProvider
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject

class OkHttpFileSendAdapter(
    private val resolver: ContentResolver,
    private val secrets: DeviceSecretProvider,
    // A large file needs far longer than the short calls elsewhere; only a stalled link should fail.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) : FileSendGateway {

    override suspend fun send(
        serverUrl: String,
        file: SharedFile,
        onBytesSent: (Long) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job]
        runCatching {
            val url = "${resolveServerBaseUrl(serverUrl)}/api/files/receive".toHttpUrl().newBuilder()
                .addQueryParameter("name", file.name)
                .build()
            val request = Request.Builder().url(url)
                .header("Authorization", "Bearer ${secrets.deviceSecret()}")
                .post(ContentBody(file) { sent ->
                    if (job?.isActive == false) throw IOException("cancelled")
                    onBytesSent(sent)
                })
                .build()
            client.newCall(request).execute().use { resp ->
                when {
                    resp.code == 401 || resp.code == 403 ->
                        throw FileSendException(FileSendFailure.NOT_APPROVED, "HTTP ${resp.code}")
                    resp.code == 413 -> throw FileSendException(FileSendFailure.TOO_LARGE, "HTTP 413")
                    !resp.isSuccessful -> throw FileSendException(FileSendFailure.OTHER, "HTTP ${resp.code}")
                }
                JSONObject(resp.body?.string().orEmpty()).optString("path")
            }
        }
    }

    private inner class ContentBody(
        private val file: SharedFile,
        private val onSent: (Long) -> Unit
    ) : RequestBody() {
        override fun contentType(): MediaType = "application/octet-stream".toMediaType()

        override fun contentLength(): Long = file.sizeBytes ?: -1L

        override fun writeTo(sink: BufferedSink) {
            val input = resolver.openInputStream(Uri.parse(file.reference))
                ?: throw IOException("cannot open ${file.name}")
            input.use { stream ->
                val buffer = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                    total += read
                    onSent(total)
                }
            }
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
    }
}

/**
 * Keeps what another app may legitimately share. This activity is open to every app, so without
 * this one could pass a file:// path or this app's own provider and have its private data sent.
 */
fun acceptableShareUris(uris: List<Uri>, ownProviderAuthority: String): List<Uri> =
    uris.filter { it.scheme == ContentResolver.SCHEME_CONTENT && it.authority != ownProviderAuthority }

fun ContentResolver.toSharedFile(uri: Uri): SharedFile? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
        SharedFile(
            reference = uri.toString(),
            name = cursor.getString(nameColumn).orEmpty().ifBlank { "file" },
            sizeBytes = if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else null
        )
    }
}.getOrNull()
