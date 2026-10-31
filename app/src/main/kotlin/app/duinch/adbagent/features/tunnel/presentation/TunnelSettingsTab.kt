package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdateBannerCard
import app.duinch.adbagent.features.updater.presentation.UpdaterUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdaterUiState

@Composable
fun TunnelSettingsTab(
    state: DashboardUiState,
    updaterState: UpdaterUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onUpdaterIntent: (UpdaterUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    var showP2PSheet by rememberSaveable { mutableStateOf(false) }
    var showHomeWifiSheet by rememberSaveable { mutableStateOf(false) }

    if (showP2PSheet) {
        P2PConfigSheet(
            state = state,
            onIntent = onIntent,
            onDismiss = { showP2PSheet = false }
        )
    }

    if (showHomeWifiSheet) {
        HomeWifiConfigSheet(
            state = state,
            onIntent = onIntent,
            onDismiss = { showHomeWifiSheet = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Group 1: Đường truyền & Mạng
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "ĐƯỜNG TRUYỀN & MẠNG",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ext.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsNavigationRow(
                        icon = AppIcons.CloudLock,
                        title = "Kết nối Máy tính (P2P WebRTC)",
                        subtitle = "${state.serverUrlInput.ifBlank { "Chưa đặt URL" }} • Cổng ${state.localAdbPortInput}",
                        onClick = { showP2PSheet = true }
                    )

                    HorizontalDivider(color = ext.cardBorder, thickness = 0.5.dp)

                    SettingsNavigationRow(
                        icon = AppIcons.LinkWireless,
                        title = "Ưu tiên Wi-Fi nội bộ",
                        subtitle = if (state.autoSwitchOnHomeWifi) {
                            if (state.trustedHomeSsidInput.isNotBlank()) "SSID: ${state.trustedHomeSsidInput}"
                            else "Đang bật • Chưa đặt SSID"
                        } else "Tắt (luôn duy trì P2P WebRTC)",
                        onClick = { showHomeWifiSheet = true }
                    )
                }
            }
        }

        // Group 2: Dịch vụ & Hệ thống
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "DỊCH VỤ & HỆ THỐNG",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ext.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = AppIcons.ShieldBolt,
                        title = "Duy trì chạy ngầm",
                        subtitle = "Giữ tiến trình trên thông báo để nhận lệnh liên tục",
                        checked = state.runInBackground,
                        onCheckedChange = { onIntent(DashboardUiIntent.ToggleRunInBackground(it)) }
                    )

                    HorizontalDivider(color = ext.cardBorder, thickness = 0.5.dp)

                    SettingsSwitchRow(
                        icon = AppIcons.Refresh,
                        title = "Tự động kết nối lại",
                        subtitle = "Duy trì kết nối ngầm khi mạng thay đổi (Backoff)",
                        checked = state.autoReconnect,
                        onCheckedChange = { onIntent(DashboardUiIntent.ToggleAutoReconnect(it)) }
                    )

                    HorizontalDivider(color = ext.cardBorder, thickness = 0.5.dp)

                    SettingsNavigationRow(
                        icon = AppIcons.Terminal,
                        title = "Embedded Termux Console",
                        subtitle = "Mở shell nội bộ & chẩn đoán ADB trên máy",
                        onClick = { onIntent(DashboardUiIntent.OnTermuxIntent(TermuxUiIntent.OpenConsole)) }
                    )
                }
            }
        }

        // Group 3: Thông tin ứng dụng & Cập nhật (Ở cuối màn hình)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "THÔNG TIN & CẬP NHẬT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            UpdateBannerCard(state = updaterState, onIntent = onUpdaterIntent)
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
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

@Composable
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
