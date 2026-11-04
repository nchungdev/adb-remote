package app.duinch.adbagent.features.sharing.application

import app.duinch.adbagent.features.sharing.domain.FileSendGateway
import app.duinch.adbagent.features.sharing.domain.ShareTarget
import app.duinch.adbagent.features.sharing.domain.SharedFile
import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SendSharedFilesUseCaseTest {
    private val tunnel = "https://a.trycloudflare.com"
    private val lan = "10.0.0.2:8080"
    private val mac = ServerIdentity("m", "Mac")

    private class FakeGateway(val failOn: String? = null) : FileSendGateway {
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun send(serverUrl: String, file: SharedFile, onBytesSent: (Long) -> Unit): Result<String> {
            if (file.name == failOn) return Result.failure(IllegalStateException("boom"))
            sent += serverUrl to file.name
            onBytesSent(4)
            return Result.success("/saved/${file.name}")
        }
    }

    private fun file(name: String) = SharedFile("content://x/$name", name, 4)

    @Test
    fun `addresses of one machine become one target, LAN first`() {
        val targets = shareTargets(
            listOf(
                SavedConnection("slot-2", tunnel, server = mac),
                SavedConnection("primary", lan, server = mac),
                SavedConnection("slot-3", "10.0.0.9:8080", name = "NAS")
            )
        )
        assertEquals(listOf("Mac", "NAS"), targets.map { it.title })
        assertEquals(listOf(lan, tunnel), targets.first().addresses)
    }

    @Test
    fun `a card with no address is not a target`() {
        assertTrue(shareTargets(listOf(SavedConnection("primary", ""))).isEmpty())
    }

    @Test
    fun `falls back to the tunnel when LAN is out of reach`() = runTest {
        val gateway = FakeGateway()
        val result = SendSharedFilesUseCase(gateway) { it == tunnel }(
            ShareTarget("m", "Mac", listOf(lan, tunnel)), listOf(file("a")), { _, _ -> }
        )
        assertEquals("/saved/a", result.getOrNull())
        assertEquals(listOf(tunnel to "a"), gateway.sent)
    }

    @Test
    fun `no address in reach sends nothing`() = runTest {
        val gateway = FakeGateway()
        val result = SendSharedFilesUseCase(gateway) { false }(
            ShareTarget("m", "Mac", listOf(lan)), listOf(file("a")), { _, _ -> }
        )
        assertTrue(result.isFailure)
        assertTrue(gateway.sent.isEmpty())
    }

    @Test
    fun `stops at the first failed file`() = runTest {
        val gateway = FakeGateway(failOn = "b")
        val result = SendSharedFilesUseCase(gateway) { true }(
            ShareTarget("m", "Mac", listOf(lan)), listOf(file("a"), file("b"), file("c")), { _, _ -> }
        )
        assertTrue(result.isFailure)
        assertEquals(listOf(lan to "a"), gateway.sent)
    }

    @Test
    fun `progress names the file it belongs to`() = runTest {
        val seen = mutableListOf<Pair<Int, Long>>()
        SendSharedFilesUseCase(FakeGateway()) { true }(
            ShareTarget("m", "Mac", listOf(lan)), listOf(file("a"), file("b"))
        ) { index, sent -> seen += index to sent }
        assertEquals(listOf(0 to 4L, 1 to 4L), seen)
    }
}
