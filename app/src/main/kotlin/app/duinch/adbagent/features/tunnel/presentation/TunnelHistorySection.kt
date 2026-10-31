package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TunnelHistorySection(
    history: List<TunnelHistoryEntry>,
    showAll: Boolean,
    nowEpochMs: Long,
    onToggleExpand: () -> Unit,
    onClearHistory: () -> Unit
) {
    val ext = AdbTheme.colors
    val hasCompletedItems = history.any { !it.isActive }
    val visibleEntries = if (showAll) history else history.take(5)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppIcons.Clock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Lịch sử kết nối (${history.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hasCompletedItems) {
            TextButton(onClick = onClearHistory) {
                Text(
                    text = "Xóa cũ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Chưa có phiên kết nối nào được ghi nhận",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                visibleEntries.forEachIndexed { idx, entry ->
                    HistoryEntryRow(entry = entry, nowEpochMs = nowEpochMs)
                    if (idx < visibleEntries.lastIndex) {
                        HorizontalDivider(color = ext.cardBorder.copy(alpha = 0.6f))
                    }
                }
                if (history.size > 5) {
                    HorizontalDivider(color = ext.cardBorder.copy(alpha = 0.6f))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = onToggleExpand) {
                            Text(
                                text = if (showAll) "Thu gọn danh sách" else "Xem tất cả (${history.size} phiên)",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(
    entry: TunnelHistoryEntry,
    nowEpochMs: Long
) {
    val ext = AdbTheme.colors
    val durationText = formatDurationVi(entry.durationMillis(nowEpochMs))
    val routeText = buildString {
        append(entry.networkType.label)
        append(" • :${entry.localAdbPort}")
        entry.mappedServerPort?.let { append(" → Mac :$it") }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (entry.isActive) ext.statusConnected else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routeText,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatTimestampVi(entry.startedAtEpochMs)} • ${entry.endReason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Surface(
                color = if (entry.isActive) ext.statusConnectedContainer else ext.surfaceElevated,
                shape = CircleShape
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (entry.isActive) ext.statusConnected else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = formatBytesVi(entry.bytesBridged),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun formatDurationVi(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

internal fun formatBytesVi(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatTimestampVi(epochMs: Long): String =
    SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
