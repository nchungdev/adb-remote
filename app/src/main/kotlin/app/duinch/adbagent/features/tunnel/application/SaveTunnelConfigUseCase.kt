package app.duinch.adbagent.features.tunnel.application

import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository

class SaveTunnelConfigUseCase(
    private val repository: TunnelConfigRepository
) {
    operator fun invoke(draft: TunnelConfig): Result<TunnelConfig> {
        return draft.validate().onSuccess { validConfig ->
            repository.saveConfig(validConfig)
        }
    }
}
