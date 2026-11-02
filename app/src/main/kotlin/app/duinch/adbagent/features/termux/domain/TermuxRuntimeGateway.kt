package app.duinch.adbagent.features.termux.domain

import kotlinx.coroutines.flow.Flow

interface TermuxRuntimeGateway {
    fun observeBootstrapState(): Flow<BootstrapState>
    fun observeConsoleLogs(): Flow<List<String>>
    fun getPrefixPaths(): TermuxPrefixPaths
    suspend fun ensureBootstrapReady(): Result<TermuxPrefixPaths>
    suspend fun executeCommand(commandLine: String, timeoutSeconds: Long = 15L): Result<ShellCommandResult>
    fun clearConsoleLogs()
}
