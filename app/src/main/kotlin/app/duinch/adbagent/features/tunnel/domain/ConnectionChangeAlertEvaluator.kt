package app.duinch.adbagent.features.tunnel.domain

import app.duinch.adbagent.core.model.NetworkType

object ConnectionChangeAlertEvaluator {

    fun evaluateServerStateChange(
        previous: TunnelConnectionState?,
        current: TunnelConnectionState
    ): String? {
        if (previous == null) return null

        return when {
            current is TunnelConnectionState.Connected && previous !is TunnelConnectionState.Connected ->
                "Máy chủ ADB Remote đã kết nối"
            current is TunnelConnectionState.Disconnected &&
                (previous is TunnelConnectionState.Connected || previous is TunnelConnectionState.Connecting || previous is TunnelConnectionState.Reconnecting) ->
                "Đã ngắt kết nối với máy chủ ADB Remote"
            current is TunnelConnectionState.Reconnecting && previous !is TunnelConnectionState.Reconnecting ->
                "Mất kết nối máy chủ, đang tự động kết nối lại..."
            current is TunnelConnectionState.Error && previous !is TunnelConnectionState.Error ->
                "Lỗi kết nối máy chủ: ${current.message}"
            else -> null
        }
    }

    fun evaluateUsbChange(
        previous: Boolean?,
        current: Boolean
    ): String? {
        if (previous == null || previous == current) return null

        return if (current) {
            "Đã kết nối cáp USB (ADB phần cứng sẵn sàng)"
        } else {
            "Đã ngắt kết nối cáp USB"
        }
    }

    fun evaluateNetworkChange(
        previous: NetworkType?,
        current: NetworkType
    ): String? {
        if (previous == null || previous == current) return null

        return when {
            current == NetworkType.NONE ->
                "Mất kết nối mạng Internet / Wi-Fi"
            current == NetworkType.WIFI && previous == NetworkType.CELLULAR ->
                "Đã chuyển sang mạng Wi-Fi"
            current == NetworkType.WIFI && previous == NetworkType.NONE ->
                "Đã kết nối mạng Wi-Fi"
            current == NetworkType.CELLULAR && previous == NetworkType.WIFI ->
                "Mất Wi-Fi, đã chuyển sang Dữ liệu di động"
            current == NetworkType.CELLULAR && previous == NetworkType.NONE ->
                "Đã kết nối Dữ liệu di động"
            current == NetworkType.ETHERNET ->
                "Đã kết nối mạng có dây (Ethernet)"
            else -> null
        }
    }
}
