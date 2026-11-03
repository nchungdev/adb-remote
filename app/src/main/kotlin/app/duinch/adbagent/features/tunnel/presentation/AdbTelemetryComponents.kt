package app.duinch.adbagent.features.tunnel.presentation

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
    isUsbConnected: Boolean,
    isPaired: Boolean,
    activePort: Int,
    telemetry: RemoteTelemetryData,
    modifier: Modifier = Modifier
) {
    val pingText = if (telemetry.pingMs != null) "${telemetry.pingMs}ms" else "<2ms"
    val uptimeText = if (telemetry.uptimeSec > 0) {
        val h = telemetry.uptimeSec / 3600
        val m = (telemetry.uptimeSec % 3600) / 60
        val s = telemetry.uptimeSec % 60
        "%02d:%02d:%02d".format(h, m, s)
    } else {
        "00:00:00"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Hàng 1: KÊNH (1.2f) | PING (1f) | UPTIME (1.1f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val channelLabel = when {
                isUsbConnected && isPaired -> "USB & Wi-Fi"
                isUsbConnected -> "Cáp USB"
                isPaired -> "Wi-Fi"
                else -> "Chưa bật"
            }
            MetricBox(
                icon = if (isUsbConnected && !isPaired) AppIcons.Usb else AppIcons.LinkWireless,
                label = "KÊNH",
                value = channelLabel,
                modifier = Modifier.weight(1.2f)
            )
            MetricBox(
                icon = AppIcons.Refresh,
                label = "PING",
                value = pingText,
                modifier = Modifier.weight(1f)
            )
            MetricBox(
                icon = AppIcons.Clock,
                label = "UPTIME",
                value = uptimeText,
                modifier = Modifier.weight(1.1f)
            )
        }

        // Hàng 2: ĐÃ TRUYỀN (1.2f) | TỐC ĐỘ (1f) | CỔNG ADB (1.1f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(
                icon = AppIcons.ArrowUpDown,
                label = "ĐÃ TRUYỀN",
                value = formatBytesVi(telemetry.bytesTransferred),
                modifier = Modifier.weight(1.2f)
            )
            MetricBox(
                icon = AppIcons.Activity,
                label = "TỐC ĐỘ",
                value = formatSpeedVi(telemetry.transferSpeedBps),
                modifier = Modifier.weight(1f)
            )
            val adbPortValue = when {
                isPaired -> activePort.toString()
                isUsbConnected -> "5555"
                else -> "--"
            }
            MetricBox(
                icon = AppIcons.Terminal,
                label = "CỔNG ADB",
                value = adbPortValue,
                modifier = Modifier.weight(1.1f)
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
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
