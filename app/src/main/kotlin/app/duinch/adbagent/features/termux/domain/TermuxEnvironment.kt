package app.duinch.adbagent.features.termux.domain

enum class BootstrapState {
    NOT_INSTALLED,
    EXTRACTING,
    READY,
    FAILED
}

data class TermuxPrefixPaths(
    val rootDir: String,
    val usrPrefixDir: String,
    val homeDir: String,
    val tmpDir: String,
    val adbBinaryPath: String
)

data class ShellCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
) {
    val isSuccess: Boolean get() = exitCode == 0
}
