package app.duinch.adbagent.features.tunnel.domain

import app.duinch.adbagent.core.model.NetworkType

data class TunnelConfig(
    val serverUrl: String = DEFAULT_TUNNEL_URL,
    val deviceAlias: String = "Android-Device",
    val localAdbPort: Int = 5555,
    val autoReconnectOnNetworkChange: Boolean = true,
    val trustedHomeSsid: String = "",
    val autoSwitchOnHomeWifi: Boolean = true,
    val runInBackground: Boolean = true,
    val remoteRoomCode: String = "",
    val notifyConnectionChange: Boolean = true,
    val themeMode: String = "system"
) {
    fun validate(): Result<TunnelConfig> {
        val raw = serverUrl.trim()
        if (raw.contains("://") && !raw.startsWith("http://") && !raw.startsWith("https://") && !raw.startsWith("ws://") && !raw.startsWith("wss://")) {
            return Result.failure(
                IllegalArgumentException("Endpoint máy chủ bắt buộc dùng giao thức http(s):// hoặc ws(s)://")
            )
        }
        val url = when {
            raw.isEmpty() -> ""
            raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("ws://") || raw.startsWith("wss://") -> raw
            raw.contains("trycloudflare.com") -> "https://$raw"
            else -> "http://$raw"
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
                deviceAlias = deviceAlias.trim()
            )
        )
    }

    companion object {
        const val DEFAULT_TUNNEL_URL = ""
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
