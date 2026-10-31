package app.duinch.adbagent.features.pairing.application

import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.domain.PairingCodeValidator

class ActivateTcpipModeUseCase(
    private val pairingGateway: AdbPairingGateway
) {
    suspend operator fun invoke(wirelessConnectPort: Int): Result<AdbPairingState.PairedAndConnected> {
        if (!PairingCodeValidator.isValidPort(wirelessConnectPort)) {
            return Result.failure(IllegalArgumentException("Chưa phát hiện cổng Gỡ lỗi không dây hợp lệ"))
        }
        val connectRes = pairingGateway.connectLocalhost(wirelessConnectPort)
        if (connectRes.isFailure) {
            return Result.failure(
                connectRes.exceptionOrNull()
                    ?: IllegalStateException("Chưa thể nối cổng $wirelessConnectPort (hãy ghép đôi lần đầu)")
            )
        }
        return pairingGateway.enablePersistentTcpip(wirelessConnectPort, 5555)
            .map { port -> AdbPairingState.PairedAndConnected(port) }
    }
}
