package app.duinch.adbagent.features.tunnel.domain

enum class ServerApprovalStatus { APPROVED, PENDING, REJECTED, NOT_REQUESTED }

/** Whether the host owner has approved this device on a server. */
interface ServerApprovalGateway {
    suspend fun checkApproval(serverUrl: String): Result<ServerApprovalStatus>
    suspend fun requestApproval(serverUrl: String, deviceName: String, code: String): Result<Unit>
    suspend fun measureLatencyMs(serverUrl: String): Long?
}

/** Stable per-install credential, sent to servers but never shown to the user. */
fun interface DeviceSecretProvider {
    fun deviceSecret(): String
}
