package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.features.tunnel.domain.DeviceSecretProvider
import app.duinch.adbagent.features.tunnel.domain.ServerApprovalGateway
import app.duinch.adbagent.features.tunnel.domain.ServerApprovalStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class OkHttpServerApprovalAdapter(
    private val secrets: DeviceSecretProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : ServerApprovalGateway {

    override suspend fun checkApproval(serverUrl: String): Result<ServerApprovalStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val request = authorized("${resolveServerBaseUrl(serverUrl)}/api/pair/check").get().build()
            client.newCall(request).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                when (JSONObject(resp.body?.string().orEmpty()).optString("status")) {
                    "approved" -> ServerApprovalStatus.APPROVED
                    "pending" -> ServerApprovalStatus.PENDING
                    "rejected" -> ServerApprovalStatus.REJECTED
                    else -> ServerApprovalStatus.NOT_REQUESTED
                }
            }
        }
    }

    override suspend fun requestApproval(serverUrl: String, deviceName: String, code: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().put("device_name", deviceName).put("code", code).toString()
                val request = authorized("${resolveServerBaseUrl(serverUrl)}/api/pair/request")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    check(resp.isSuccessful) { "HTTP ${resp.code}" }
                }
            }
        }

    override suspend fun measureLatencyMs(serverUrl: String): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("${resolveServerBaseUrl(serverUrl)}/healthz").head().build()
            val begin = System.nanoTime()
            client.newCall(request).execute().use { resp ->
                check(resp.isSuccessful)
            }
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin)
        }.getOrNull()
    }

    private fun authorized(url: String) =
        Request.Builder().url(url).header("Authorization", "Bearer ${secrets.deviceSecret()}")
}
