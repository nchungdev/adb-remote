package app.duinch.adbagent.features.pairing.data

import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class TermuxLocalAdbPairingAdapter(
    private val termuxRuntime: TermuxRuntimeGateway
) : AdbPairingGateway {

    override suspend fun pairLocalhost(port: Int, sixDigitCode: String): Result<String> {
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val cmd = "\"$adbBin\" pair 127.0.0.1:$port $sixDigitCode"
        return termuxRuntime.executeCommand(cmd, timeoutSeconds = 20L).mapCatching { res ->
            val combined = "${res.stdout}\n${res.stderr}".trim()
            if (!res.isSuccess ||
                combined.contains("Failed", ignoreCase = true) ||
                !combined.contains("Successfully paired", ignoreCase = true)
            ) {
                error(describePairFailure(combined, port))
            }
            combined
        }
    }

    override suspend fun connectLocalhost(port: Int): Result<String> {
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val cmd = "\"$adbBin\" connect 127.0.0.1:$port"
        return termuxRuntime.executeCommand(cmd, timeoutSeconds = 15L).mapCatching { res ->
            val combined = "${res.stdout}\n${res.stderr}".trim()
            val ok = res.isSuccess &&
                (combined.contains("connected to", ignoreCase = true) ||
                    combined.contains("already connected", ignoreCase = true))
            if (!ok) {
                error(withoutDaemonNoise(combined).ifEmpty { "Không thể kết nối ADB tới 127.0.0.1:$port" })
            }
            combined
        }
    }

    override suspend fun enablePersistentTcpip(fromPort: Int, targetTcpPort: Int): Result<Int> {
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val tcpCmd = "\"$adbBin\" -s 127.0.0.1:$fromPort tcpip $targetTcpPort"
        return termuxRuntime.executeCommand(tcpCmd, timeoutSeconds = 15L).mapCatching { res ->
            val combined = "${res.stdout}\n${res.stderr}".trim()
            if (!res.isSuccess && !combined.contains("restarting in TCP mode", ignoreCase = true)) {
                error(withoutDaemonNoise(combined).ifEmpty { "Không thể chuyển sang chế độ TCP $targetTcpPort" })
            }
            delay(1_200L)
            connectLocalhost(targetTcpPort).getOrThrow()
            bringAppToFront(targetTcpPort)
            targetTcpPort
        }
    }

    override suspend fun isLocalPortOpen(port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), PORT_PROBE_TIMEOUT_MS) }
        }.isSuccess
    }

    override suspend fun bringAppToFront(port: Int): Result<Unit> {
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val cmd = "\"$adbBin\" -s 127.0.0.1:$port shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n app.duinch.adbagent/.MainActivity"
        return termuxRuntime.executeCommand(cmd, timeoutSeconds = 5L).map { }
    }
}

private const val PORT_PROBE_TIMEOUT_MS = 500

/** Drops adb's server start-up chatter ("* daemon ..."), which is not an error. */
internal fun withoutDaemonNoise(output: String): String =
    output.lineSequence().filterNot { it.trimStart().startsWith("* ") }.joinToString("\n").trim()

/**
 * adb reports a wrong code and a closed port identically: the server drops the connection
 * and the client prints "protocol fault (couldn't read status message): Success".
 */
internal fun describePairFailure(output: String, port: Int): String {
    val detail = withoutDaemonNoise(output)
    return when {
        detail.isEmpty() -> "Không thể ghép đôi với 127.0.0.1:$port"
        detail.contains("protocol fault") -> "Mã chưa đúng hoặc cổng ghép nối đã đóng"
        else -> detail
    }
}
