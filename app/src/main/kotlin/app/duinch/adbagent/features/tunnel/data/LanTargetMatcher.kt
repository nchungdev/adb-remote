package app.duinch.adbagent.features.tunnel.data

/** Checks if a server target address points to a private local LAN network. */
internal fun isLanTargetAddress(serverUrl: String): Boolean {
    val host = serverUrl.substringAfter("://").substringBefore(':').substringBefore('/').trim()
    if (host.endsWith("trycloudflare.com")) return false
    if (host == "localhost" || host == "127.0.0.1") return true
    if (host.startsWith("192.168.") || host.startsWith("10.")) return true
    if (host.startsWith("172.")) {
        val second = host.substringAfter('.').substringBefore('.').toIntOrNull() ?: return false
        return second in 16..31
    }
    return false
}
