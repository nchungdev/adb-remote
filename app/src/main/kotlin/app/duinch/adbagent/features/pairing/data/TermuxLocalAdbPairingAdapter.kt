package app.duinch.adbagent.features.pairing.data

import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway
import kotlinx.coroutines.delay

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
                error(combined.ifEmpty { "Không thể ghép đôi với 127.0.0.1:$port" })
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
                error(combined.ifEmpty { "Không thể kết nối ADB tới 127.0.0.1:$port" })
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
                error(combined.ifEmpty { "Không thể chuyển sang chế độ TCP $targetTcpPort" })
            }
            delay(1_200L)
            connectLocalhost(targetTcpPort).getOrThrow()
            bringAppToFront(targetTcpPort)
            targetTcpPort
        }
    }

    override suspend fun bringAppToFront(port: Int): Result<Unit> {
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val cmd = "\"$adbBin\" -s 127.0.0.1:$port shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n app.duinch.adbagent/.MainActivity"
        return termuxRuntime.executeCommand(cmd, timeoutSeconds = 5L).map { }
    }

    override suspend fun deleteScreenshot(port: Int, filePath: String?, uriString: String?): Result<Unit> {
        if (filePath.isNullOrBlank() && uriString.isNullOrBlank()) return Result.success(Unit)
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val cmds = mutableListOf<String>()
        if (!filePath.isNullOrBlank()) {
            cmds.add("rm -f \"$filePath\"")
        }
        if (!uriString.isNullOrBlank()) {
            cmds.add("content delete --uri \"$uriString\"")
        }
        val shellCmd = cmds.joinToString(" ; ")
        val cmd = "\"$adbBin\" -s 127.0.0.1:$port shell \"$shellCmd\""
        return termuxRuntime.executeCommand(cmd, timeoutSeconds = 5L).map { }
    }
}
