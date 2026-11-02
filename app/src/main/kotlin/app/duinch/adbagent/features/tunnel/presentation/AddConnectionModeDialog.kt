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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.remotetunnel.ui.ConnectMode

@Composable
fun AddConnectionModeDialog(
    onDismissRequest: () -> Unit,
    onSelectMode: (ConnectMode) -> Unit,
    onScanQrNow: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Thêm Máy Chủ Kết Nối",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Chọn phương thức để thiết lập ngay:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (onScanQrNow != null) {
                    ModeOptionTile(
                        title = "Quét Mã QR Nhanh",
                        description = "Mở camera quét mã QR trên màn hình máy tính để kết nối ngay",
                        icon = AppIcons.QrCode,
                        isHighlighted = true,
                        onClick = onScanQrNow
                    )
                }
                ModeOptionTile(
                    title = "Cùng Mạng Wi-Fi (LAN)",
                    description = "Tự động dò tìm hoặc nhập trực tiếp địa chỉ IP nội bộ",
                    icon = AppIcons.LinkWireless,
                    onClick = { onSelectMode(ConnectMode.Lan) }
                )
                ModeOptionTile(
                    title = "Mạng Từ Xa (Remote URL)",
                    description = "Dán đường dẫn Cloudflare Tunnel hoặc nhập mã phòng P2P",
                    icon = AppIcons.CloudLock,
                    onClick = { onSelectMode(ConnectMode.Remote) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Đóng", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    )
}

@Composable
private fun ModeOptionTile(
    title: String,
    description: String,
    icon: ImageVector,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = if (isHighlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
