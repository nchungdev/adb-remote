package app.duinch.adbagent.features.pairing.domain

import kotlinx.coroutines.flow.Flow

interface AdbDiscoveryGateway {
    fun observeDiscoveredPorts(): Flow<DiscoveredAdbPorts>
    fun startDiscovery()
    fun stopDiscovery()
}

interface AdbPairingGateway {
    suspend fun pairLocalhost(port: Int, sixDigitCode: String): Result<String>
    suspend fun connectLocalhost(port: Int): Result<String>
    suspend fun enablePersistentTcpip(fromPort: Int, targetTcpPort: Int = 5555): Result<Int>
    suspend fun bringAppToFront(port: Int = 5555): Result<Unit> = Result.success(Unit)
    suspend fun deleteScreenshot(port: Int = 5555, filePath: String? = null, uriString: String? = null): Result<Unit> = Result.success(Unit)
}

interface PairingNotificationGateway {
    fun observeNotificationPairing(): Flow<AdbPairingState>
    fun startInteractivePairing()
    fun reportPairingState(state: AdbPairingState)
}
