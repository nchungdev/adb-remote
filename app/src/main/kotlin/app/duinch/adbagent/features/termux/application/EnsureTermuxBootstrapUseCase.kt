package app.duinch.adbagent.features.termux.application

import app.duinch.adbagent.features.termux.domain.TermuxPrefixPaths
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway

class EnsureTermuxBootstrapUseCase(
    private val runtimeGateway: TermuxRuntimeGateway
) {
    suspend operator fun invoke(): Result<TermuxPrefixPaths> {
        return runtimeGateway.ensureBootstrapReady()
    }
}
