package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import app.duinch.remotetunnel.ui.LocalServerCardPalette
import app.duinch.remotetunnel.ui.ServerCardTone
import app.duinch.remotetunnel.ui.serverCardPalette

/** What the add dialog is for: another connection to [server], or a new machine when null. */
internal data class AddConnectionRequest(val server: ServerIdentity?)

/** The machine as a frame around its connections: they belong to it and compete only with each other. */
@Composable
internal fun ServerGroupCard(
    server: ServerIdentity?,
    summary: String,
    autoConnect: Boolean = true,
    onToggleAutoConnect: ((Boolean) -> Unit)? = null,
    onAddConnection: (() -> Unit)?,
    onRenameServer: ((String) -> Unit)? = null,
    onDisconnectServer: (() -> Unit)? = null,
    onDeleteServer: (() -> Unit)? = null,
    metrics: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Monitor,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server?.name?.ifBlank { null } ?: if (server == null) "Máy chủ mới" else "Máy chủ",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                onAddConnection?.let {
                    IconButton(onClick = it, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Thêm kết nối",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                ServerGroupMoreMenu(
                    autoConnect = autoConnect,
                    onToggleAutoConnect = onToggleAutoConnect,
                    onRename = onRenameServer?.let { { renaming = true } },
                    onDisconnect = onDisconnectServer,
                    onDelete = onDeleteServer?.let { { confirmDelete = true } }
                )
            }
            metrics()
            content()
        }
    }

    if (renaming) {
        RenameServerDialog(
            currentName = server?.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                onRenameServer?.invoke(it)
            }
        )
    }

    if (confirmDelete) {
        val serverTitle = server?.name?.ifBlank { null } ?: "máy chủ này"
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xoá máy chủ?") },
            text = { Text("Tất cả kết nối thuộc \"$serverTitle\" sẽ bị xoá khỏi danh sách.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteServer?.invoke()
                }) {
                    Text("Xoá", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun ServerGroupMoreMenu(
    autoConnect: Boolean,
    onToggleAutoConnect: ((Boolean) -> Unit)?,
    onRename: (() -> Unit)?,
    onDisconnect: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    if (onRename == null && onDisconnect == null && onDelete == null && onToggleAutoConnect == null) return
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Tùy chọn máy chủ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            onToggleAutoConnect?.let { onToggle ->
                DropdownMenuItem(
                    text = { Text("Tự động kết nối") },
                    leadingIcon = {
                        Icon(
                            imageVector = AppIcons.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        Switch(
                            checked = autoConnect,
                            onCheckedChange = null
                        )
                    },
                    onClick = { onToggle(!autoConnect) }
                )
            }
            onRename?.let {
                DropdownMenuItem(
                    text = { Text("Đổi tên máy chủ") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { expanded = false; it() }
                )
            }
            onDisconnect?.let {
                DropdownMenuItem(
                    text = { Text("Ngắt kết nối") },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { expanded = false; it() }
                )
            }
            onDelete?.let {
                DropdownMenuItem(
                    text = { Text("Xoá máy chủ", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { expanded = false; it() }
                )
            }
        }
    }
}

@Composable
private fun RenameServerDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Đổi tên máy chủ") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_SERVER_NAME_LENGTH) },
                singleLine = true,
                placeholder = { Text("Ví dụ: Mac mini M1") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("Lưu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

/** Numbers belong to the machine: they describe whichever connection is carrying its traffic. */
@Composable
internal fun ServerGroupMetrics(
    connection: GroupedConnection,
    latencyMs: Long?,
    isPaired: Boolean,
    onOpenPairing: () -> Unit
) {
    val telemetry = rememberServerSlotTelemetry(connection.state, connection.mode, connection.serverUrl, latencyMs)
    CompositionLocalProvider(LocalServerCardPalette provides serverCardPalette(ServerCardTone.Running)) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WirelessAdbStatusRow(isPaired = isPaired, onOpenPairing = onOpenPairing)
            AdbTelemetryBentoGrid(telemetry = telemetry)
        }
    }
}
