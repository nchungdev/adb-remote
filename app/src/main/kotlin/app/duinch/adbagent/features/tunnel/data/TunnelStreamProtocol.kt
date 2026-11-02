package app.duinch.adbagent.features.tunnel.data

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.nio.ByteBuffer

object TunnelStreamProtocol {

    const val FRAME_OPEN: Byte = 0x01
    const val FRAME_DATA: Byte = 0x02
    const val FRAME_CLOSE: Byte = 0x03

    sealed interface Frame {
        data class Open(val streamId: Int) : Frame
        data class Data(val streamId: Int, val payload: ByteArray) : Frame
        data class Close(val streamId: Int) : Frame
    }

    fun encodeData(streamId: Int, src: ByteArray, length: Int): ByteString {
        val buf = ByteBuffer.allocate(5 + length)
        buf.put(FRAME_DATA)
        buf.putInt(streamId)
        buf.put(src, 0, length)
        return buf.array().toByteString()
    }

    fun encodeClose(streamId: Int): ByteString {
        val buf = ByteBuffer.allocate(5)
        buf.put(FRAME_CLOSE)
        buf.putInt(streamId)
        return buf.array().toByteString()
    }

    fun decode(bytes: ByteString): Frame? {
        if (bytes.size < 5) return null
        val raw = bytes.toByteArray()
        val buf = ByteBuffer.wrap(raw)
        val type = buf.get()
        val streamId = buf.int
        return when (type) {
            FRAME_OPEN -> Frame.Open(streamId)
            FRAME_DATA -> Frame.Data(streamId, raw.copyOfRange(5, raw.size))
            FRAME_CLOSE -> Frame.Close(streamId)
            else -> null
        }
    }

    fun buildHelloJson(
        alias: String,
        localPort: Int,
        wifiIp: String = "",
        wifiSsid: String = ""
    ): String =
        JSONObject()
            .put("type", "hello")
            .put("alias", alias)
            .put("local_adb_port", localPort)
            .apply {
                if (wifiIp.isNotBlank()) put("local_wifi_ip", wifiIp)
                if (wifiSsid.isNotBlank()) put("wifi_ssid", wifiSsid)
            }
            .toString()

    data class WelcomeServerInfo(
        val mappedPort: Int?,
        val serverLanIp: String?,
        val serverSsid: String?
    )

    fun parseWelcomeServerInfo(jsonText: String): WelcomeServerInfo? = runCatching {
        val obj = JSONObject(jsonText)
        if (obj.optString("type") == "welcome") {
            WelcomeServerInfo(
                mappedPort = obj.optInt("mapped_port").takeIf { it > 0 },
                serverLanIp = obj.optString("server_lan_ip").takeIf { it.isNotBlank() },
                serverSsid = obj.optString("server_ssid").takeIf { it.isNotBlank() }
            )
        } else null
    }.getOrNull()

    fun parseWelcomePort(jsonText: String): Int? = runCatching {
        val obj = JSONObject(jsonText)
        if (obj.optString("type") == "welcome") obj.optInt("mapped_port").takeIf { it > 0 } else null
    }.getOrNull()

    fun parseCommandEvent(jsonText: String): app.duinch.adbagent.features.tunnel.domain.HostCommandRecord? = runCatching {
        val obj = JSONObject(jsonText)
        if (obj.optString("type") == "command_event") {
            val ev = obj.optJSONObject("event") ?: return@runCatching null
            app.duinch.adbagent.features.tunnel.domain.HostCommandRecord(
                id = ev.optString("id", java.util.UUID.randomUUID().toString().take(8)),
                command = ev.optString("command"),
                source = ev.optString("source", "server"),
                status = ev.optString("status", "success"),
                timestampEpochMs = ev.optLong("start_time", System.currentTimeMillis()),
                durationMs = ev.optLong("duration_ms", 0L),
                outputSnippet = ev.optString("output")
            )
        } else null
    }.getOrNull()

    fun parseAdbOpenPacket(payload: ByteArray): String? {
        if (payload.size < 24) return null
        // Little-endian A_OPEN is ASCII "OPEN": 0x4f, 0x50, 0x45, 0x4e
        if (payload[0] != 0x4f.toByte() || payload[1] != 0x50.toByte() ||
            payload[2] != 0x45.toByte() || payload[3] != 0x4e.toByte()
        ) {
            return null
        }
        val len = ByteBuffer.wrap(payload, 12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        if (len in 1..2048 && payload.size >= 24 + len) {
            val cmd = String(payload, 24, len, Charsets.UTF_8).trimEnd('\u0000')
            if (cmd.isNotBlank()) return cmd
        }
        return null
    }
}
