package app.duinch.adbagent.features.sharing.presentation

import app.duinch.adbagent.features.sharing.domain.ShareTarget
import app.duinch.adbagent.features.sharing.domain.SharedFile

sealed interface SharePhase {
    data object Choosing : SharePhase
    data class Sending(val fileIndex: Int, val fraction: Float?) : SharePhase
    data class Done(val savedPath: String) : SharePhase
    data class Failed(val message: String) : SharePhase
}

data class ShareUiState(
    val files: List<SharedFile> = emptyList(),
    val targets: List<ShareTarget> = emptyList(),
    val selectedTargetId: String? = null,
    val phase: SharePhase = SharePhase.Choosing
)

sealed interface ShareUiIntent {
    data class SelectTarget(val id: String) : ShareUiIntent
    data object Send : ShareUiIntent
    data object Cancel : ShareUiIntent
}
