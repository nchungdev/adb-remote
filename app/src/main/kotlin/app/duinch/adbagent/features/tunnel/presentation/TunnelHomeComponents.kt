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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons

@Composable
internal fun TelemetryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    Surface(
        modifier = modifier.heightIn(min = 64.dp),
        color = AdbTheme.colors.surfaceElevated,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ActiveConnectionModeCard(
    wifiIp: String,
    adbPort: String,
    isUsbConnected: Boolean,
    isAtHomeWifi: Boolean,
    homeSsid: String,
    onCopyCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = AdbTheme.colors
    val hasWifi = wifiIp.isNotBlank()
    val localConnectCmd = if (hasWifi) "adb connect $wifiIp:$adbPort" else ""

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isUsbConnected || isAtHomeWifi -> ext.statusConnectedContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isUsbConnected -> AppIcons.Usb
                                isAtHomeWifi -> AppIcons.LinkWireless
                                else -> AppIcons.ShieldBolt
                            },
                            contentDescription = null,
                            tint = when {
                                isUsbConnected || isAtHomeWifi -> ext.statusConnected
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isUsbConnected -> "Đang nhận cáp USB trực tiếp"
                            isAtHomeWifi -> "Ưu tiên Wi-Fi nội bộ (${homeSsid.ifBlank { "Nhà" }})"
                            hasWifi -> "Mạng Wi-Fi: $wifiIp"
                            else -> "Cloudflare Zero-Trust Tunnel"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isUsbConnected -> "Máy tính kết nối trực tiếp qua USB, không cần mở port"
                            isAtHomeWifi -> "Đang kết nối cùng Wi-Fi nhà • Ping < 2ms, không tốn data"
                            hasWifi -> "Có thể kết nối trực tiếp qua IP nội bộ cùng mạng"
                            else -> "Đường hầm từ xa bảo mật qua Cloudflare Edge"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (hasWifi) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ext.terminalSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onCopyCommand(localConnectCmd) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = localConnectCmd,
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.terminalText,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = AppIcons.Copy,
                            contentDescription = "Sao chép lệnh",
                            tint = ext.terminalText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
