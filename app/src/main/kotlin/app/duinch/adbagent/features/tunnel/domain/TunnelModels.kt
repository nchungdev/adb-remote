package app.duinch.adbagent.features.tunnel.domain

import app.duinch.adbagent.core.model.NetworkType

data class TunnelConfig(
    val serverUrl: String = DEFAULT_TUNNEL_URL,
    val deviceAlias: String = "Android-Device",
    val localAdbPort: Int = 5555,
    val tunnelMode: String = MODE_WEBRTC,
    val cfAccessClientId: String = "",
    val cfAccessClientSecret: String = "",
    val cfAuthCookie: String = "",
    val autoReconnectOnNetworkChange: Boolean = true,
    val trustedHomeSsid: String = "",
    val autoSwitchOnHomeWifi: Boolean = true,
    val runInBackground: Boolean = true
) {
    val hasCloudflareAuth: Boolean
        get() = cfAuthCookie.contains("CF_Authorization=") ||
            (cfAccessClientId.isNotBlank() && cfAccessClientSecret.isNotBlank())

    fun validate(): Result<TunnelConfig> {
        val url = serverUrl.trim()
        val isValidProtocol = url.startsWith("wss://") || url.startsWith("https://")
        if (!isValidProtocol) {
            return Result.failure(
                IllegalArgumentException("Endpoint bắt buộc dùng giao thức mã hoá TLS (https:// hoặc wss://)")
            )
        }
        if (deviceAlias.trim().isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Tên định danh thiết bị không được để trống")
            )
        }
        if (localAdbPort !in 1..65535) {
            return Result.failure(
                IllegalArgumentException("Cổng ADB nội bộ phải trong khoảng 1..65535")
            )
        }
        return Result.success(
            copy(
                serverUrl = url,
                deviceAlias = deviceAlias.trim(),
                cfAccessClientId = cfAccessClientId.trim(),
                cfAccessClientSecret = cfAccessClientSecret.trim(),
                cfAuthCookie = cfAuthCookie.trim()
            )
        )
    }

    companion object {
        const val MODE_WEBRTC = "webrtc"
        const val MODE_WEBSOCKET = "websocket"
        const val DEFAULT_TUNNEL_URL = "wss://adb.duinch.app/api/tunnel"
    }
}

sealed interface TunnelConnectionState {
    data object Disconnected : TunnelConnectionState
    data class Connecting(val targetUrl: String) : TunnelConnectionState
    data class Connected(
        val targetUrl: String,
        val localAdbPort: Int,
        val networkType: NetworkType,
        val bytesBridged: Long = 0L,
        val mappedServerPort: Int? = null,
        val connectedAtEpochMs: Long = System.currentTimeMillis()
    ) : TunnelConnectionState
    data class Reconnecting(
        val attempt: Int,
        val nextRetryDelayMs: Long,
        val reason: String
    ) : TunnelConnectionState
    data class Error(val message: String) : TunnelConnectionState
}

data class TunnelHistoryEntry(
    val sessionId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val networkType: NetworkType,
    val localAdbPort: Int,
    val mappedServerPort: Int? = null,
    val bytesBridged: Long = 0L,
    val endReason: String = "Đang trực tuyến"
) {
    val isActive: Boolean
        get() = endedAtEpochMs == null

    fun durationMillis(nowEpochMs: Long): Long =
        ((endedAtEpochMs ?: nowEpochMs) - startedAtEpochMs).coerceAtLeast(0L)
}
