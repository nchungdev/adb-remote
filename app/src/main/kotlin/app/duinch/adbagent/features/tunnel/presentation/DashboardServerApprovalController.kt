package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.ServerApprovalGateway
import app.duinch.adbagent.features.tunnel.domain.ServerApprovalStatus
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Runs a connection only after the host owner approved this device on that server. */
internal class DashboardServerApprovalController(
    private val gateway: ServerApprovalGateway,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<DashboardUiState>,
    private val effects: MutableSharedFlow<DashboardUiEffect>,
    private val pollIntervalMs: Long = 2_000L,
    private val timeoutMs: Long = 170_000L,
    private val newCode: () -> String = ::randomPairingCode
) {
    private var job: Job? = null

    fun connectWhenApproved(serverUrl: String, deviceName: String, connect: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            // Unreachable or older servers fall through: the tunnel reports its own failure and retries.
            val status = gateway.checkApproval(serverUrl).getOrNull()
            if (status == null || status == ServerApprovalStatus.APPROVED || awaitApproval(serverUrl, deviceName)) {
                connect()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        uiState.update { it.copy(serverApprovalCode = null) }
    }

    private suspend fun awaitApproval(serverUrl: String, deviceName: String): Boolean {
        val code = newCode()
        gateway.requestApproval(serverUrl, deviceName, code).onFailure {
            effects.emit(DashboardUiEffect.ShowMessage("Không gửi được yêu cầu ghép nối tới máy chủ"))
            return false
        }
        uiState.update { it.copy(serverApprovalCode = code) }
        var waitedMs = 0L
        while (waitedMs < timeoutMs) {
            delay(pollIntervalMs)
            waitedMs += pollIntervalMs
            when (gateway.checkApproval(serverUrl).getOrNull()) {
                ServerApprovalStatus.APPROVED -> return finish(approved = true, message = null)
                ServerApprovalStatus.REJECTED -> return finish(approved = false, message = "Máy tính đã từ chối ghép nối")
                else -> Unit
            }
        }
        return finish(approved = false, message = "Hết thời gian chờ duyệt ghép nối")
    }

    private suspend fun finish(approved: Boolean, message: String?): Boolean {
        uiState.update { it.copy(serverApprovalCode = null) }
        message?.let { effects.emit(DashboardUiEffect.ShowMessage(it)) }
        return approved
    }
}

private fun randomPairingCode(): String {
    val random = SecureRandom()
    return "%03d-%03d".format(random.nextInt(1000), random.nextInt(1000))
}
