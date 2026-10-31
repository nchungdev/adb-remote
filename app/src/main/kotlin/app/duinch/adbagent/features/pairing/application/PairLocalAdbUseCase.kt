package app.duinch.adbagent.features.pairing.application

import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.domain.PairingCodeValidator

class PairLocalAdbUseCase(
    private val pairingGateway: AdbPairingGateway
) {
    suspend operator fun invoke(
        pairingPort: Int,
        rawCode: String,
        connectPortHint: Int? = null
    ): Result<AdbPairingState.PairedAndConnected> {
        val code = rawCode.trim()
        if (!PairingCodeValidator.isValid(code)) {
            return Result.failure(IllegalArgumentException("Mã ghép nối phải gồm đúng 6 chữ số"))
        }
        if (!PairingCodeValidator.isValidPort(pairingPort)) {
            return Result.failure(IllegalArgumentException("Cổng ghép nối không hợp lệ: $pairingPort"))
        }

        val pairResult = pairingGateway.pairLocalhost(pairingPort, code)
        if (pairResult.isFailure) {
            return Result.failure(
                pairResult.exceptionOrNull() ?: IllegalStateException("Ghép đôi ADB thất bại")
            )
        }

        val initialConnectPort = connectPortHint?.takeIf { PairingCodeValidator.isValidPort(it) } ?: 5555
        val connectResult = pairingGateway.connectLocalhost(initialConnectPort)
        if (connectResult.isFailure) {
            return Result.failure(
                connectResult.exceptionOrNull()
                    ?: IllegalStateException("Đã ghép đôi nhưng chưa kết nối được cổng $initialConnectPort")
            )
        }

        val finalPort = if (initialConnectPort != 5555) {
            pairingGateway.enablePersistentTcpip(fromPort = initialConnectPort, targetTcpPort = 5555)
                .getOrDefault(initialConnectPort)
        } else {
            5555
        }

        return Result.success(AdbPairingState.PairedAndConnected(finalPort))
    }
}
