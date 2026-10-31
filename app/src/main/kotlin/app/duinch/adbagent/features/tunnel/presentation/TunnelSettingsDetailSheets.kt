package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudflareConfigSheet(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ext = AdbTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Cấu hình Cloudflare Reverse Tunnel",
                style = MaterialTheme.typography.titleLarge
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
                    if (state.hasCloudflareAuth) "Đã đăng nhập Cloudflare Access (Chạm làm mới)"
                    else "Đăng nhập Cloudflare Access (1 chạm)"
                )
            }

            OutlinedTextField(
                value = state.serverUrlInput,
                onValueChange = { onIntent(DashboardUiIntent.UpdateServerUrl(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloudflare Tunnel WSS Endpoint") },
                placeholder = { Text("wss://adb.duinch.app/api/tunnel") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = state.deviceAliasInput,
                onValueChange = { onIntent(DashboardUiIntent.UpdateDeviceAlias(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tên định danh thiết bị (Alias)") },
                placeholder = { Text("samsung-SM-S926B") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )

            OutlinedTextField(
                value = state.localAdbPortInput,
                onValueChange = { onIntent(DashboardUiIntent.UpdateLocalAdbPort(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cổng ADB nội bộ máy (Local Port)") },
                placeholder = { Text("5555") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
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

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    onIntent(DashboardUiIntent.SaveConfig)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Lưu cấu hình")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeWifiConfigSheet(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ext = AdbTheme.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Ưu tiên Wi-Fi nội bộ",
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tự đổi sang nội bộ khi ở Wi-Fi nhà",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Ưu tiên mạng LAN trực tiếp (ping < 2ms, không tốn data)",
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
                OutlinedTextField(
                    value = state.trustedHomeSsidInput,
                    onValueChange = { onIntent(DashboardUiIntent.UpdateTrustedHomeSsid(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên Wi-Fi nhà (SSID)") },
                    placeholder = { Text("Nhập chính xác SSID Wi-Fi nhà") },
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { onIntent(DashboardUiIntent.UseCurrentWifiAsHome) },
                    enabled = state.currentWifiSsid.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(AppIcons.LinkWireless, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (state.currentWifiSsid.isNotBlank()) "Lấy tên Wi-Fi hiện tại: \"${state.currentWifiSsid}\""
                        else "Chưa kết nối mạng Wi-Fi nào"
                    )
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

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Xong")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
