package app.duinch.adbagent.features.termux.application

import app.duinch.adbagent.features.termux.domain.BootstrapState
import app.duinch.adbagent.features.termux.domain.ShellCommandResult
import app.duinch.adbagent.features.termux.domain.TermuxPrefixPaths
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunTermuxCommandUseCaseTest {

    private class FakeTermuxRuntimeGateway(
        initialState: BootstrapState = BootstrapState.NOT_INSTALLED
    ) : TermuxRuntimeGateway {
        val state = MutableStateFlow(initialState)
        val logs = MutableStateFlow<List<String>>(emptyList())
        var bootstrapCallCount = 0
        var executedCommands = mutableListOf<String>()

        override fun observeBootstrapState(): Flow<BootstrapState> = state
        override fun observeConsoleLogs(): Flow<List<String>> = logs

        override fun getPrefixPaths() = TermuxPrefixPaths(
            rootDir = "/data/user/0/app.duinch.adbagent/files/termux",
            usrPrefixDir = "/data/user/0/app.duinch.adbagent/files/termux/usr",
            homeDir = "/data/user/0/app.duinch.adbagent/files/termux/home",
            tmpDir = "/data/user/0/app.duinch.adbagent/files/termux/usr/tmp",
            adbBinaryPath = "/data/app/lib/arm64/libadb.so"
        )

        override suspend fun ensureBootstrapReady(): Result<TermuxPrefixPaths> {
            bootstrapCallCount += 1
            state.value = BootstrapState.READY
            return Result.success(getPrefixPaths())
        }

        override suspend fun executeCommand(
            commandLine: String,
            timeoutSeconds: Long
        ): Result<ShellCommandResult> {
            executedCommands += commandLine
            return Result.success(
                ShellCommandResult(
                    exitCode = 0,
                    stdout = "Android Debug Bridge version 1.0.41",
                    stderr = "",
                    durationMs = 14L
                )
            )
        }

        override fun clearConsoleLogs() {
            logs.value = emptyList()
        }
    }

    @Test
    fun `rejects blank command input`() = runTest {
        val gateway = FakeTermuxRuntimeGateway(BootstrapState.READY)
        val useCase = RunTermuxCommandUseCase(gateway)

        val result = useCase("   ")

        assertTrue(result.isFailure)
        assertTrue(gateway.executedCommands.isEmpty())
    }

    @Test
    fun `bootstraps environment automatically when not installed before executing command`() = runTest {
        val gateway = FakeTermuxRuntimeGateway(BootstrapState.NOT_INSTALLED)
        val useCase = RunTermuxCommandUseCase(gateway)

        val result = useCase("  adb devices  ")

        assertTrue(result.isSuccess)
        assertEquals(1, gateway.bootstrapCallCount)
        assertEquals(listOf("adb devices"), gateway.executedCommands)
        assertEquals(0, result.getOrThrow().exitCode)
    }
}
