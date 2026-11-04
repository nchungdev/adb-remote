package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.remotetunnel.ui.LocalServerCardPalette
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.remotetunnel.ui.RemoteTelemetryData

@Composable
fun AdbTelemetryBentoGrid(
    telemetry: RemoteTelemetryData,
    modifier: Modifier = Modifier
) {
    val pingText = if (telemetry.pingMs != null) "${telemetry.pingMs}ms" else "--"
    val uptimeText = formatUptime(telemetry.uptimeSec.coerceAtLeast(0L))

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Hàng 1: PING | UPTIME
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(
                icon = AppIcons.Refresh,
                label = "Ping",
                value = pingText,
                modifier = Modifier.weight(1f)
            )
            MetricBox(
                icon = AppIcons.Clock,
                label = "Uptime",
                value = uptimeText,
                modifier = Modifier.weight(1f)
            )
        }

        // Hàng 2: ĐÃ TRUYỀN | TỐC ĐỘ | CỔNG ADB (khi chưa có địa chỉ trên máy tính)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(
                icon = AppIcons.ArrowUpDown,
                label = "Đã truyền",
                value = formatBytesVi(telemetry.bytesTransferred),
                modifier = Modifier.weight(1f)
            )
            MetricBox(
                icon = AppIcons.Activity,
                label = "Tốc độ",
                value = formatSpeedVi(telemetry.transferSpeedBps),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MetricBox(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalServerCardPalette.current
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette?.accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun formatBytesVi(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

fun formatSpeedVi(bytesPerSec: Long): String = when {
    bytesPerSec <= 0L -> "0 B/s"
    bytesPerSec < 1024L -> "$bytesPerSec B/s"
    bytesPerSec < 1024L * 1024L -> "%.1f KB/s".format(bytesPerSec / 1024.0)
    else -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
}
