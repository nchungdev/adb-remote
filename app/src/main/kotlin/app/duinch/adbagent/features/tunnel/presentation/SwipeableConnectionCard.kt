package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState

internal fun TunnelConnectionState.isInUse(): Boolean =
    this is TunnelConnectionState.Connected ||
        this is TunnelConnectionState.Connecting ||
        this is TunnelConnectionState.Reconnecting

/**
 * Swipe right connects or disconnects, swipe left deletes after a confirmation. Actions fire
 * without dismissing, so the card always springs back and reflects the resulting state.
 */
@Composable
internal fun SwipeableConnectionCard(
    isInUse: Boolean,
    canConnect: Boolean,
    onToggleConnection: () -> Unit,
    onDelete: () -> Unit,
    displayName: String?,
    onRename: (String) -> Unit,
    content: @Composable (openRename: () -> Unit) -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val currentToggle by rememberUpdatedState(onToggleConnection)
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> currentToggle()
                SwipeToDismissBoxValue.EndToStart -> confirmDelete = true
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = isInUse || canConnect,
        backgroundContent = {
            when (swipe.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeAction(
                    icon = if (isInUse) Icons.Default.Close else Icons.Default.PlayArrow,
                    label = if (isInUse) "Ngắt kết nối" else "Kết nối",
                    color = if (isInUse) Color(0xFFB45309) else Color(0xFF047857),
                    alignment = Alignment.CenterStart
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeAction(
                    icon = Icons.Default.Delete,
                    label = "Xoá",
                    color = MaterialTheme.colorScheme.error,
                    alignment = Alignment.CenterEnd
                )
                SwipeToDismissBoxValue.Settled -> Unit
            }
        }
    ) {
        content { renaming = true }
    }

    if (renaming) {
        RenameConnectionDialog(
            currentName = displayName.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                onRename(it)
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xoá kết nối này?") },
            text = { Text(if (isInUse) "Kết nối đang chạy sẽ bị ngắt." else "Địa chỉ máy chủ sẽ bị xoá khỏi danh sách.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Xoá", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } }
        )
    }
}

@Composable
private fun SwipeAction(icon: ImageVector, label: String, color: Color, alignment: Alignment) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp),
        contentAlignment = alignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RenameConnectionDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên máy chủ") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_SERVER_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text("Ví dụ: Mac văn phòng") },
                supportingText = { Text("Để trống để hiện lại địa chỉ") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}
