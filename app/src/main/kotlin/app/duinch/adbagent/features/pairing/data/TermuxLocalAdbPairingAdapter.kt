package app.duinch.adbagent.features.pairing.data

import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway

class TermuxLocalAdbPairingAdapter(
    private val termuxRuntime: TermuxRuntimeGateway
) : AdbPairingGateway {

    override suspend fun pairLocalhost(port: Int, sixDigitCode: String): Result<String> {
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val cmd = "\"$adbBin\" pair 127.0.0.1:$port $sixDigitCode"
        return termuxRuntime.executeCommand(cmd, timeoutSeconds = 15L).mapCatching { res ->
            val combined = "${res.stdout}\n${res.stderr}".trim()
            if (!res.isSuccess || combined.contains("Failed", ignoreCase = true)) {
                error(combined.ifEmpty { "Không thể ghép đôi với 127.0.0.1:$port" })
            }
            combined.ifEmpty { "Successfully paired to 127.0.0.1:$port" }
        }
    }

    override suspend fun connectLocalhost(port: Int): Result<String> {
        termuxRuntime.ensureBootstrapReady()
        val adbBin = termuxRuntime.getPrefixPaths().adbBinaryPath
        val cmd = "\"$adbBin\" connect 127.0.0.1:$port"
        return termuxRuntime.executeCommand(cmd, timeoutSeconds = 12L).mapCatching { res ->
            val combined = "${res.stdout}\n${res.stderr}".trim()
            if (!res.isSuccess || combined.contains("failed to connect", ignoreCase = true)) {
                error(combined.ifEmpty { "Không thể kết nối ADB tới 127.0.0.1:$port" })
            }
            combined
        }
    }
}
