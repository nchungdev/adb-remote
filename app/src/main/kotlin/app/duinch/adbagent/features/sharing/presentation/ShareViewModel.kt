package app.duinch.adbagent.features.sharing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.duinch.adbagent.features.sharing.application.SendSharedFilesUseCase
import app.duinch.adbagent.features.sharing.domain.FileSendException
import app.duinch.adbagent.features.sharing.domain.FileSendFailure
import app.duinch.adbagent.features.sharing.domain.ShareTarget
import app.duinch.adbagent.features.sharing.domain.SharedFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ShareViewModel(
    files: List<SharedFile>,
    targets: List<ShareTarget>,
    private val sendFiles: SendSharedFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ShareUiState(files = files, targets = targets, selectedTargetId = targets.singleOrNull()?.id)
    )
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()
    private var sending: Job? = null

    fun onIntent(intent: ShareUiIntent) {
        when (intent) {
            is ShareUiIntent.SelectTarget -> _uiState.update { it.copy(selectedTargetId = intent.id) }
            ShareUiIntent.Send -> send()
            ShareUiIntent.Cancel -> {
                sending?.cancel()
                _uiState.update { it.copy(phase = SharePhase.Choosing) }
            }
        }
    }

    private fun send() {
        val state = _uiState.value
        val target = state.targets.find { it.id == state.selectedTargetId } ?: return
        _uiState.update { it.copy(phase = SharePhase.Sending(0, null)) }
        sending = viewModelScope.launch {
            val result = sendFiles(target, state.files) { index, sent ->
                val size = state.files[index].sizeBytes
                val fraction = size?.takeIf { it > 0 }?.let { (sent.toFloat() / it).coerceIn(0f, 1f) }
                _uiState.update { it.copy(phase = SharePhase.Sending(index, fraction)) }
            }
            val phase = result.fold(
                onSuccess = { SharePhase.Done(it) },
                onFailure = { SharePhase.Failed(failureMessage(it)) }
            )
            _uiState.update { it.copy(phase = phase) }
        }
    }
}

internal fun failureMessage(error: Throwable): String = when ((error as? FileSendException)?.failure) {
    FileSendFailure.NOT_APPROVED -> "Điện thoại này chưa được duyệt trên máy tính. Hãy kết nối lại và duyệt trên dashboard."
    FileSendFailure.TOO_LARGE -> "File quá lớn cho đường này. Qua tunnel Cloudflare, hãy thử khi cùng mạng Wi-Fi."
    FileSendFailure.OTHER -> "Máy tính từ chối nhận file (${error.message})."
    null -> "Không tới được máy tính. Hãy kiểm tra server đang chạy và mạng của điện thoại."
}
