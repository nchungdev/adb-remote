package app.duinch.adbagent.features.sharing.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ShareDialog(state: ShareUiState, onIntent: (ShareUiIntent) -> Unit, onClose: () -> Unit) {
    val phase = state.phase
    val canSend = state.files.isNotEmpty() && state.targets.any { it.id == state.selectedTargetId }
    AlertDialog(
        onDismissRequest = { if (phase !is SharePhase.Sending) onClose() },
        title = { Text("Gửi tới máy tính") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    state.files.isEmpty() -> Text("Chỉ gửi được file, nội dung này không có file nào.")
                    state.targets.isEmpty() -> Text("Chưa có máy chủ nào. Hãy mở ADB Remote và kết nối trước.")
                    else -> ShareBody(state, onIntent)
                }
            }
        },
        confirmButton = {
            when (phase) {
                SharePhase.Choosing -> TextButton(onClick = { onIntent(ShareUiIntent.Send) }, enabled = canSend) { Text("Gửi") }
                is SharePhase.Sending -> TextButton(onClick = { onIntent(ShareUiIntent.Cancel) }) { Text("Hủy gửi") }
                is SharePhase.Done -> TextButton(onClick = onClose) { Text("Xong") }
                is SharePhase.Failed -> TextButton(onClick = { onIntent(ShareUiIntent.Send) }) { Text("Thử lại") }
            }
        },
        dismissButton = {
            if (phase is SharePhase.Choosing || phase is SharePhase.Failed) {
                TextButton(onClick = onClose) { Text("Đóng") }
            }
        }
    )
}

@Composable
private fun ShareBody(state: ShareUiState, onIntent: (ShareUiIntent) -> Unit) {
    val first = state.files.first()
    Text(
        text = if (state.files.size == 1) first.name else "${state.files.size} file: ${first.name}...",
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    when (val phase = state.phase) {
        SharePhase.Choosing -> state.targets.forEach { target ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onIntent(ShareUiIntent.SelectTarget(target.id)) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = target.id == state.selectedTargetId,
                    onClick = { onIntent(ShareUiIntent.SelectTarget(target.id)) }
                )
                Text(target.title, modifier = Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        is SharePhase.Sending -> {
            Text("Đang gửi ${phase.fileIndex + 1}/${state.files.size}...", style = MaterialTheme.typography.bodySmall)
            val fraction = phase.fraction
            if (fraction == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
        is SharePhase.Done -> Text(
            "Đã gửi. File trên máy tính: ${phase.savedPath}",
            style = MaterialTheme.typography.bodySmall
        )
        is SharePhase.Failed -> Text(
            phase.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
