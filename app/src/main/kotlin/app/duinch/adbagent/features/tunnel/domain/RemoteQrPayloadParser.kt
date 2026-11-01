package app.duinch.adbagent.features.tunnel.domain

data class RemoteQrPayload(
    val roomCode: String,
    val serverUrl: String? = null,
    val broker: String? = null
)

object RemoteQrPayloadParser {
    fun parse(rawText: String): RemoteQrPayload? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        // 1. Try JSON or key-value format: {"type":"adb-remote","room":"589-214","broker":"broker.emqx.io"}
        val roomMatch = Regex("\"room\"\\s*:\\s*\"([^\"]+)\"").find(text)
        if (roomMatch != null) {
            val room = roomMatch.groupValues[1].trim()
            val server = Regex("\"(?:server|url)\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.trim()
            val broker = Regex("\"broker\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)?.trim()
            return RemoteQrPayload(roomCode = room, serverUrl = server, broker = broker)
        }

        // 2. Try URL format (e.g. http://192.168.1.15:8080/remote or with ?room=)
        if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
            val cleanUrl = text.substringBefore("/remote").substringBefore('?').trimEnd('/')
            val urlRoomMatch = Regex("[?&]room=([a-zA-Z0-9-]+)").find(text)
            val roomParam = urlRoomMatch?.groupValues?.get(1).orEmpty()
            return RemoteQrPayload(
                roomCode = roomParam,
                serverUrl = cleanUrl
            )
        }

        // 3. Raw room code fallback (e.g. 589-214 or 589214)
        val clean = text.filter { it.isLetterOrDigit() || it == '-' }
        if (clean.length in 5..16) {
            return RemoteQrPayload(roomCode = clean)
        }

        return null
    }
}
