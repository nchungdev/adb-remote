package app.duinch.adbagent.features.tunnel.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RemoteQrPayloadParserTest {

    @Test
    fun `parses json payload with room and broker`() {
        val json = """{"type":"adb-remote","room":"589-214","broker":"broker.emqx.io","active":"true"}"""
        val payload = RemoteQrPayloadParser.parse(json)
        assertNotNull(payload)
        assertEquals("589-214", payload.roomCode)
        assertEquals("broker.emqx.io", payload.broker)
    }

    @Test
    fun `parses url with remote path and query room`() {
        val url = "http://192.168.1.15:8080/remote?room=391-002"
        val payload = RemoteQrPayloadParser.parse(url)
        assertNotNull(payload)
        assertEquals("391-002", payload.roomCode)
        assertEquals("http://192.168.1.15:8080", payload.serverUrl)
    }

    @Test
    fun `parses plain room code`() {
        val code = "928-111"
        val payload = RemoteQrPayloadParser.parse(code)
        assertNotNull(payload)
        assertEquals("928-111", payload.roomCode)
    }

    @Test
    fun `returns null for blank input`() {
        val payload = RemoteQrPayloadParser.parse("   ")
        assertNull(payload)
    }
}
