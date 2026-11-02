package app.duinch.adbagent.features.tunnel.application

import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.flow.first

class ToggleReverseTunnelUseCase(
    private val configRepository: TunnelConfigRepository,
    private val tunnelGateway: ReverseTunnelGateway
) {
    suspend operator fun invoke(overrideConfig: TunnelConfig? = null): Result<Boolean> {
        val currentState = tunnelGateway.observeConnectionState().first()
        val isActive = currentState is TunnelConnectionState.Connected ||
            currentState is TunnelConnectionState.Connecting ||
            currentState is TunnelConnectionState.Reconnecting

        if (isActive) {
            tunnelGateway.disconnect()
            return Result.success(false)
        }

        val candidate = overrideConfig ?: configRepository.getConfig()
        val validConfig = candidate.validate().getOrElse { err ->
            return Result.failure(err)
        }
        configRepository.saveConfig(validConfig)
        return tunnelGateway.connect(validConfig).map { true }
    }
}
