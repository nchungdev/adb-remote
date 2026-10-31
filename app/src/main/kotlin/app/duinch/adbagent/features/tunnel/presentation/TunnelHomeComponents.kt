package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons

@Composable
internal fun ConnectionModesSection(
    wifiIp: String,
    adbPort: String,
    isUsbConnected: Boolean,
    isAtHomeWifi: Boolean = false,
    homeSsid: String = "",
    onCopyCommand: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, AdbTheme.colors.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Các phương thức kết nối ADB",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 1. USB Direct
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isUsbConnected) AdbTheme.colors.statusConnectedContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.Usb,
                            contentDescription = null,
                            tint = if (isUsbConnected) AdbTheme.colors.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "1. Cáp USB (Direct Cable)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (isUsbConnected) "Đang cắm cáp USB vào máy tính (Đã nhận trực tiếp)" else "Chưa cắm cáp USB (Tự động nhận khi cắm)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUsbConnected) AdbTheme.colors.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Wi-Fi Local LAN
            val hasWifi = wifiIp.isNotBlank()
            val localConnectCmd = if (hasWifi) "adb connect $wifiIp:$adbPort" else ""
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (hasWifi) AdbTheme.colors.statusConnectedContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.LinkWireless,
                            contentDescription = null,
                            tint = if (hasWifi) AdbTheme.colors.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAtHomeWifi) "2. Không dây cùng Wi-Fi Local (Ưu tiên)" else "2. Không dây cùng Wi-Fi Local",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = when {
                            isAtHomeWifi -> "Đang ở Wi-Fi nhà ($homeSsid) • IP: $wifiIp • Cổng: $adbPort (Ping < 2ms)"
                            hasWifi -> "IP: $wifiIp • Cổng: $adbPort (Ping < 2ms)"
                            else -> "Chưa kết nối Wi-Fi nội bộ"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAtHomeWifi) AdbTheme.colors.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasWifi) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AdbTheme.colors.terminalSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCopyCommand(localConnectCmd) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = localConnectCmd,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AdbTheme.colors.terminalText,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = AppIcons.Copy,
                                    contentDescription = "Sao chép lệnh",
                                    tint = AdbTheme.colors.terminalText,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Remote Tunnel
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.ShieldBolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "3. Đường hầm từ xa (Cloudflare)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (isAtHomeWifi) {
                            "Đang ở Wi-Fi nhà: Tự ưu tiên mạng nội bộ LAN, đường hầm ở chế độ chờ."
                        } else {
                            "Dùng khi ra ngoài, khác mạng Wi-Fi hoặc 4G/5G qua nút bật bên dưới."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun TelemetryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = AdbTheme.colors.surfaceElevated,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = if (monospace) FontFamily.Monospace else null
            )
        }
    }
}

@Composable
internal fun ReadinessRowCard(
    title: String,
    subtitle: String,
    isReady: Boolean,
    onClick: () -> Unit
) {
    val ext = AdbTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isReady) AppIcons.CheckCircle else AppIcons.AlertCircle,
                contentDescription = null,
                tint = if (isReady) ext.statusConnected else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
