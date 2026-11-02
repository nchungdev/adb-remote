package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.remotetunnel.ui.RemoteTelemetryData

@Composable
fun AdbDeviceStatusCard(
    isUsbConnected: Boolean,
    pairingState: AdbPairingState,
    telemetry: RemoteTelemetryData,
    onOpenPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPaired = pairingState is AdbPairingState.PairedAndConnected
    val activePort = (pairingState as? AdbPairingState.PairedAndConnected)?.connectPort ?: 5555

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Trạng Thái ADB Thiết Bị",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdbStatusBadge(
                    isUsbConnected = isUsbConnected,
                    isPaired = isPaired
                )
            }

            when {
                isUsbConnected -> {
                    DeviceStatusRow(
                        icon = AppIcons.Usb,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Cáp USB đã kết nối phần cứng",
                        subtitle = "Quyền ADB đã sẵn sàng qua cáp máy tính"
                    )
                }
                isPaired -> {
                    DeviceStatusRow(
                        icon = AppIcons.LinkWireless,
                        iconTint = Color(0xFF2E7D32),
                        title = "ADB Không dây đã bật",
                        subtitle = "Cổng kết nối ADB: $activePort (Sẵn sàng nhận lệnh)"
                    )
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenPairing)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DeviceStatusRow(
                            icon = AppIcons.LinkWireless,
                            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                            title = "Chưa bật ADB không dây",
                            subtitle = "Cần cắm cáp USB hoặc ghép đôi không dây",
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "Ghép đôi",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Gộp 3 ô Bento Telemetry (KÊNH, PING, UPTIME) vào chung Card khi có kết nối phần cứng/ADB
            if (isUsbConnected || isPaired) {
                Spacer(modifier = Modifier.height(2.dp))
                val pingText = if (telemetry.pingMs != null) "${telemetry.pingMs}ms" else "<2ms"
                val uptimeText = if (telemetry.uptimeSec > 0) {
                    val h = telemetry.uptimeSec / 3600
                    val m = (telemetry.uptimeSec % 3600) / 60
                    val s = telemetry.uptimeSec % 60
                    "%02d:%02d:%02d".format(h, m, s)
                } else {
                    "00:00:00"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        icon = AppIcons.Usb,
                        label = "KÊNH",
                        value = if (isUsbConnected) "Cáp USB" else "Không dây",
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
            }
        }
    }
}

@Composable
private fun MetricBox(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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

@Composable
private fun AdbStatusBadge(
    isUsbConnected: Boolean,
    isPaired: Boolean
) {
    val (label, bg, fg) = when {
        isUsbConnected -> Triple("USB", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        isPaired -> Triple("Port 5555", Color(0xFFE8F5E9), Color(0xFF2E7D32))
        else -> Triple("Chưa Bật", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg
        )
    }
}

@Composable
private fun DeviceStatusRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = iconTint.copy(alpha = 0.12f),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
