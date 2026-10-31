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

    fun buildHelloJson(alias: String, localPort: Int): String =
        JSONObject()
            .put("type", "hello")
            .put("alias", alias)
            .put("local_adb_port", localPort)
            .toString()

    fun parseWelcomePort(jsonText: String): Int? = runCatching {
        val obj = JSONObject(jsonText)
        if (obj.optString("type") == "welcome") obj.optInt("mapped_port").takeIf { it > 0 } else null
    }.getOrNull()
}
