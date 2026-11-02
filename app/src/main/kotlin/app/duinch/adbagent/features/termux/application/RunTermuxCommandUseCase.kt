package app.duinch.adbagent.features.termux.application

import app.duinch.adbagent.features.termux.domain.BootstrapState
import app.duinch.adbagent.features.termux.domain.ShellCommandResult
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway
import kotlinx.coroutines.flow.first

class RunTermuxCommandUseCase(
    private val runtimeGateway: TermuxRuntimeGateway
) {
    suspend operator fun invoke(rawCommand: String): Result<ShellCommandResult> {
        val trimmed = rawCommand.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Lệnh không được để trống"))
        }

        val state = runtimeGateway.observeBootstrapState().first()
        if (state != BootstrapState.READY) {
            val initResult = runtimeGateway.ensureBootstrapReady()
            if (initResult.isFailure) {
                return Result.failure(
                    IllegalStateException("Môi trường Termux chưa sẵn sàng: ${initResult.exceptionOrNull()?.message}")
                )
            }
        }

        return runtimeGateway.executeCommand(trimmed)
    }
}
