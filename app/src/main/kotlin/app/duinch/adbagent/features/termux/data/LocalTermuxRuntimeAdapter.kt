package app.duinch.adbagent.features.termux.data

import android.content.Context
import app.duinch.adbagent.features.termux.domain.BootstrapState
import app.duinch.adbagent.features.termux.domain.ShellCommandResult
import app.duinch.adbagent.features.termux.domain.TermuxPrefixPaths
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class LocalTermuxRuntimeAdapter(
    context: Context
) : TermuxRuntimeGateway {

    private val appContext = context.applicationContext
    private val nativeLibDir = appContext.applicationInfo.nativeLibraryDir
    private val bootstrapState = MutableStateFlow(BootstrapState.NOT_INSTALLED)
    private val consoleLogs = MutableStateFlow(
        listOf("[termux-env] Khởi tạo sandbox Embedded Termux...")
    )

    override fun observeBootstrapState(): Flow<BootstrapState> = bootstrapState.asStateFlow()

    override fun observeConsoleLogs(): Flow<List<String>> = consoleLogs.asStateFlow()

    override fun getPrefixPaths(): TermuxPrefixPaths {
        val root = File(appContext.filesDir, "termux").absolutePath
        val usr = "$root/usr"
        val home = "$root/home"
        val tmp = "$usr/tmp"
        val packagedAdb = File(nativeLibDir, "libadb.so")
        val adbPath = if (packagedAdb.exists()) packagedAdb.absolutePath else "$usr/bin/adb"
        return TermuxPrefixPaths(
            rootDir = root,
            usrPrefixDir = usr,
            homeDir = home,
            tmpDir = tmp,
            adbBinaryPath = adbPath
        )
    }

    override suspend fun ensureBootstrapReady(): Result<TermuxPrefixPaths> = withContext(Dispatchers.IO) {
        runCatching {
            bootstrapState.value = BootstrapState.EXTRACTING
            val paths = getPrefixPaths()
            listOf(
                paths.usrPrefixDir,
                "${paths.usrPrefixDir}/bin",
                "${paths.usrPrefixDir}/lib",
                paths.homeDir,
                "${paths.homeDir}/.android",
                paths.tmpDir
            ).forEach { dir -> File(dir).mkdirs() }

            bootstrapState.value = BootstrapState.READY
            appendLog("[termux-env] Sẵn sàng: PREFIX=${paths.usrPrefixDir}")
            paths
        }.onFailure { err ->
            bootstrapState.value = BootstrapState.FAILED
            appendLog("[termux-env] Lỗi khởi tạo: ${err.message}")
        }
    }

    override suspend fun executeCommand(
        commandLine: String,
        timeoutSeconds: Long
    ): Result<ShellCommandResult> = withContext(Dispatchers.IO) {
        runCatching {
            val paths = getPrefixPaths()
            appendLog("$ $commandLine")
            val startMs = System.currentTimeMillis()
            val pb = ProcessBuilder("/system/bin/sh", "-c", commandLine)
            pb.directory(File(paths.homeDir).apply { mkdirs() })
            val env = pb.environment()
            env["PREFIX"] = paths.usrPrefixDir
            env["HOME"] = paths.homeDir
            env["TMPDIR"] = paths.tmpDir
            env["ANDROID_ADB_SERVER_PORT"] = "5038"
            env["LD_LIBRARY_PATH"] = "$nativeLibDir:${paths.usrPrefixDir}/lib"
            env["PATH"] = "$nativeLibDir:${paths.usrPrefixDir}/bin:/system/bin:/system/xbin"

            val proc = pb.start()
            val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                error("Lệnh quá thời gian chờ (${timeoutSeconds}s)")
            }

            val stdout = proc.inputStream.bufferedReader().readText().trim()
            val stderr = proc.errorStream.bufferedReader().readText().trim()
            val duration = System.currentTimeMillis() - startMs

            if (stdout.isNotEmpty()) appendLog(stdout)
            if (stderr.isNotEmpty()) appendLog("[stderr] $stderr")

            ShellCommandResult(
                exitCode = proc.exitValue(),
                stdout = stdout,
                stderr = stderr,
                durationMs = duration
            )
        }.onFailure { err ->
            appendLog("[error] ${err.message}")
        }
    }

    override fun clearConsoleLogs() {
        consoleLogs.value = listOf("[termux-env] Đã xoá lịch sử console.")
    }

    private fun appendLog(line: String) {
        consoleLogs.update { current ->
            (current + line).takeLast(120)
        }
    }
}
