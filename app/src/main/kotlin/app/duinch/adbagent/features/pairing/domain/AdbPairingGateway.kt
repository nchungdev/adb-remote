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
}
