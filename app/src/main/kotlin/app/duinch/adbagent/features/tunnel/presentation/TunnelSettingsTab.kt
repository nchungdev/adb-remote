package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // OTA Update Card
        UpdateBannerCard(state = updaterState, onIntent = onUpdaterIntent)

        // Embedded Termux Console Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, ext.cardBorder),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button) {
                        onIntent(DashboardUiIntent.OnTermuxIntent(TermuxUiIntent.OpenConsole))
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mở Embedded Termux Console", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Chạy lệnh POSIX shell & chẩn đoán ADB trực tiếp trên máy",
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

        // Smart Local Wi-Fi Handover Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, ext.cardBorder),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tự đổi sang nội bộ khi ở Wi-Fi nhà", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Ưu tiên mạng LAN trực tiếp (ping < 2ms, 0 tốn data)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.autoSwitchOnHomeWifi,
                        onCheckedChange = { onIntent(DashboardUiIntent.ToggleAutoSwitchHomeWifi(it)) }
                    )
                }

                if (state.autoSwitchOnHomeWifi) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.trustedHomeSsidInput,
                            onValueChange = { onIntent(DashboardUiIntent.UpdateTrustedHomeSsid(it)) },
                            modifier = Modifier.weight(1f),
                            label = { Text("Tên Wi-Fi nhà (SSID)") },
                            placeholder = { Text("Nhập tên Wi-Fi") },
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = { onIntent(DashboardUiIntent.UseCurrentWifiAsHome) },
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text("Lấy Wi-Fi này")
                        }
                    }
                    if (state.currentWifiSsid.isNotBlank()) {
                        Text(
                            text = if (state.isAtHomeWifi) "Đang kết nối đúng Wi-Fi nhà: ${state.currentWifiSsid}"
                            else "Wi-Fi hiện tại: ${state.currentWifiSsid}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.isAtHomeWifi) ext.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Cloudflare Tunnel Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, ext.cardBorder),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Cấu hình Cloudflare Reverse Tunnel",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedButton(
                    onClick = { onIntent(DashboardUiIntent.OpenCloudflareLogin) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = if (state.hasCloudflareAuth) AppIcons.CheckCircle else AppIcons.CloudLock,
                        contentDescription = null,
                        tint = if (state.hasCloudflareAuth) ext.statusConnected else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.hasCloudflareAuth) "Đã đăng nhập Cloudflare (Bấm để làm mới)"
                        else "Đăng nhập Cloudflare Access (1 chạm)"
                    )
                }

                OutlinedTextField(
                    value = state.serverUrlInput,
                    onValueChange = { onIntent(DashboardUiIntent.UpdateServerUrl(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cloudflare Tunnel WSS Endpoint") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.deviceAliasInput,
                        onValueChange = { onIntent(DashboardUiIntent.UpdateDeviceAlias(it)) },
                        modifier = Modifier.weight(1.4f),
                        label = { Text("Tên định danh máy") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.localAdbPortInput,
                        onValueChange = { onIntent(DashboardUiIntent.UpdateLocalAdbPort(it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Cổng ADB") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tự nối lại khi chuyển Wi-Fi / 4G", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Duy trì kết nối ngầm bằng Exponential Backoff",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.autoReconnect,
                        onCheckedChange = { onIntent(DashboardUiIntent.ToggleAutoReconnect(it)) }
                    )
                }

                // Progressive Disclosure for Service Token
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button) { onIntent(DashboardUiIntent.ToggleAdvancedAuth) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Service Token nâng cao (Tuỳ chọn)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = AppIcons.ChevronDown,
                        contentDescription = if (state.showAdvancedAuth) "Thu gọn" else "Mở rộng",
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (state.showAdvancedAuth) 180f else 0f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = state.showAdvancedAuth) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = state.cfClientIdInput,
                            onValueChange = { onIntent(DashboardUiIntent.UpdateCfClientId(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("CF-Access-Client-Id") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.cfClientSecretInput,
                            onValueChange = { onIntent(DashboardUiIntent.UpdateCfClientSecret(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("CF-Access-Client-Secret") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onIntent(DashboardUiIntent.SaveConfig) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Lưu cấu hình")
                }
            }
        }
    }
}
