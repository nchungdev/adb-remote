package app.duinch.adbagent.features.tunnel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebRtcSignalingClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun sendSignal(
        endpointUrl: String,
        room: String,
        peer: String,
        type: String,
        payload: JSONObject,
        authToken: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sep = if (endpointUrl.contains("?")) "&" else "?"
            val url = "$endpointUrl${sep}room=$room&peer=$peer"
            val bodyObj = JSONObject().apply {
                put("type", type)
                put("payload", payload)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
            if (authToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $authToken")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Signaling POST failed: HTTP ${resp.code}")
                }
            }
        }
    }

    suspend fun pollSignal(
        endpointUrl: String,
        room: String,
        peer: String,
        authToken: String = ""
    ): Result<JSONObject?> = withContext(Dispatchers.IO) {
        runCatching {
            val sep = if (endpointUrl.contains("?")) "&" else "?"
            val url = "$endpointUrl${sep}room=$room&peer=$peer"
            val reqBuilder = Request.Builder().url(url).get()
            if (authToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $authToken")
            }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (resp.code == 204) return@runCatching null
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Signaling GET failed: HTTP ${resp.code}")
                }
                val bodyStr = resp.body?.string().orEmpty()
                if (bodyStr.isBlank()) null else JSONObject(bodyStr)
            }
        }
    }
}
