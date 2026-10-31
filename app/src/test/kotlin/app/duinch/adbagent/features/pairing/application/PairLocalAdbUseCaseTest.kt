package app.duinch.adbagent.features.pairing.application

import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairLocalAdbUseCaseTest {

    private class FakeAdbPairingGateway(
        var pairResult: Result<String> = Result.success("Successfully paired"),
        var connectResult: Result<String> = Result.success("connected to 127.0.0.1:41923"),
        var tcpipResult: Result<Int> = Result.success(5555)
    ) : AdbPairingGateway {
        var lastPairPort: Int? = null
        var lastCode: String? = null
        var lastConnectPort: Int? = null
        var lastTcpipFromPort: Int? = null
        var lastDeletedPath: String? = null
        var lastDeletedUri: String? = null

        override suspend fun pairLocalhost(port: Int, sixDigitCode: String): Result<String> {
            lastPairPort = port
            lastCode = sixDigitCode
            return pairResult
        }

        override suspend fun connectLocalhost(port: Int): Result<String> {
            lastConnectPort = port
            return connectResult
        }

        override suspend fun enablePersistentTcpip(fromPort: Int, targetTcpPort: Int): Result<Int> {
            lastTcpipFromPort = fromPort
            return tcpipResult
        }

        override suspend fun deleteScreenshot(port: Int, filePath: String?, uriString: String?): Result<Unit> {
            lastDeletedPath = filePath
            lastDeletedUri = uriString
            return Result.success(Unit)
        }
    }

    @Test
    fun `rejects non-6-digit pairing codes before calling gateway`() = runTest {
        val gateway = FakeAdbPairingGateway()
        val useCase = PairLocalAdbUseCase(gateway)

        val res = useCase(pairingPort = 37192, rawCode = "12a45")

        assertTrue(res.isFailure)
        assertEquals(null, gateway.lastPairPort)
    }

    @Test
    fun `rejects out-of-range pairing ports`() = runTest {
        val gateway = FakeAdbPairingGateway()
        val useCase = PairLocalAdbUseCase(gateway)

        val res = useCase(pairingPort = 70000, rawCode = "123456")

        assertTrue(res.isFailure)
        assertEquals(null, gateway.lastPairPort)
    }

    @Test
    fun `pairs localhost and promotes wireless port to persistent tcpip 5555`() = runTest {
        val gateway = FakeAdbPairingGateway()
        val useCase = PairLocalAdbUseCase(gateway)

        val res = useCase(
            pairingPort = 37192,
            rawCode = " 482910 ",
            connectPortHint = 41923
        )

        assertTrue(res.isSuccess)
        assertEquals(AdbPairingState.PairedAndConnected(5555), res.getOrThrow())
        assertEquals(37192, gateway.lastPairPort)
        assertEquals("482910", gateway.lastCode)
        assertEquals(41923, gateway.lastConnectPort)
        assertEquals(41923, gateway.lastTcpipFromPort)
    }

    @Test
    fun `falls back to wireless connect port when tcpip promotion fails`() = runTest {
        val gateway = FakeAdbPairingGateway(
            tcpipResult = Result.failure(IllegalStateException("unsupported"))
        )
        val useCase = PairLocalAdbUseCase(gateway)

        val res = useCase(pairingPort = 37192, rawCode = "482910", connectPortHint = 41923)

        assertTrue(res.isSuccess)
        assertEquals(AdbPairingState.PairedAndConnected(41923), res.getOrThrow())
    }

    @Test
    fun `propagates failure when adb pair fails`() = runTest {
        val gateway = FakeAdbPairingGateway(
            pairResult = Result.failure(IllegalStateException("Wrong pairing code"))
        )
        val useCase = PairLocalAdbUseCase(gateway)

        val res = useCase(pairingPort = 37192, rawCode = "000000", connectPortHint = 41923)

        assertTrue(res.isFailure)
        assertEquals(null, gateway.lastConnectPort)
    }

    @Test
    fun `deletes screenshot file and uri upon successful pairing and connection`() = runTest {
        val gateway = FakeAdbPairingGateway()
        val useCase = PairLocalAdbUseCase(gateway)

        val res = useCase(
            pairingPort = 37192,
            rawCode = "482910",
            connectPortHint = 41923,
            screenshotFilePath = "/storage/emulated/0/Pictures/Screenshots/Screenshot_123.png",
            screenshotUri = "content://media/external/images/media/123"
        )

        assertTrue(res.isSuccess)
        assertEquals("/storage/emulated/0/Pictures/Screenshots/Screenshot_123.png", gateway.lastDeletedPath)
        assertEquals("content://media/external/images/media/123", gateway.lastDeletedUri)
    }
}
