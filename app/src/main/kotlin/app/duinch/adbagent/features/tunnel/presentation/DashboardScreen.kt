package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.features.pairing.presentation.PairingSection
import app.duinch.adbagent.features.termux.presentation.TermuxConsoleSheet
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (DashboardUiIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ADB Agent", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Embedded Termux • Cloudflare Reverse Tunnel",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = { onIntent(DashboardUiIntent.OnTermuxIntent(TermuxUiIntent.OpenConsole)) },
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("Terminal")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TunnelHeroCard(state = state, onIntent = onIntent)

            PairingSection(
                state = state.pairing,
                onIntent = { onIntent(DashboardUiIntent.OnPairingIntent(it)) }
            )

            TunnelConfigCard(state = state, onIntent = onIntent)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    TermuxConsoleSheet(
        state = state.termux,
        onIntent = { onIntent(DashboardUiIntent.OnTermuxIntent(it)) }
    )

    CloudflareAuthDialog(
        visible = state.showCloudflareLoginDialog,
        wssUrl = state.serverUrlInput,
        onCookieCaptured = { onIntent(DashboardUiIntent.OnCloudflareCookieCaptured(it)) },
        onDismiss = { onIntent(DashboardUiIntent.CloseCloudflareLogin) }
    )
}

@Composable
private fun TunnelHeroCard(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit
) {
    val (statusTitle, statusDesc, accentColor) = when (val conn = state.connectionState) {
        TunnelConnectionState.Disconnected -> Triple(
            "CHƯA KẾT NỐI",
            "Sẵn sàng mở đường hầm bảo mật về Mac mini",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        is TunnelConnectionState.Connecting -> Triple(
            "ĐANG KẾT NỐI...",
            conn.targetUrl,
            Color(0xFFFBBF24)
        )
        is TunnelConnectionState.Connected -> {
            val macPort = conn.mappedServerPort?.let { "Mac 127.0.0.1:$it • " }.orEmpty()
            Triple(
                "ĐÃ KẾT NỐI VỀ MAC MINI",
                "${macPort}ADB ${conn.localAdbPort} • ${conn.networkType.label} • ${conn.bytesBridged} B",
                Color(0xFF34D399)
            )
        }
        is TunnelConnectionState.Reconnecting -> Triple(
            "ĐANG TỰ NỐI LẠI (Lần ${conn.attempt})",
            "${conn.reason} • Thử lại sau ${conn.nextRetryDelayMs / 1000}s",
            Color(0xFFFBBF24)
        )
        is TunnelConnectionState.Error -> Triple(
            "MẤT KẾT NỐI",
            conn.message,
            MaterialTheme.colorScheme.error
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
                Text(
                    text = state.activeNetwork.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onIntent(DashboardUiIntent.ToggleTunnel) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (state.isTunnelActive) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                }
            ) {
                Text(
                    text = if (state.isTunnelActive) "Ngắt đường hầm" else "Bật kết nối tự động về Mac mini",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TunnelConfigCard(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "2. Cấu hình Cloudflare Reverse Tunnel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                onClick = { onIntent(DashboardUiIntent.OpenCloudflareLogin) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.hasCloudflareAuth) "✅ Đã xác thực Cloudflare (Bấm để đăng nhập lại)"
                    else "🔐 Đăng nhập Cloudflare Access (1 chạm)"
                )
            }

            OutlinedTextField(
                value = state.serverUrlInput,
                onValueChange = { onIntent(DashboardUiIntent.UpdateServerUrl(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cloudflare Tunnel WSS Endpoint") },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.deviceAliasInput,
                    onValueChange = { onIntent(DashboardUiIntent.UpdateDeviceAlias(it)) },
                    modifier = Modifier.weight(1.4f),
                    label = { Text("Tên máy") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.localAdbPortInput,
                    onValueChange = { onIntent(DashboardUiIntent.UpdateLocalAdbPort(it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Cổng ADB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tự kết nối lại khi đổi Wi-Fi / 4G",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = state.autoReconnect,
                    onCheckedChange = { onIntent(DashboardUiIntent.ToggleAutoReconnect(it)) }
                )
            }

            TextButton(onClick = { onIntent(DashboardUiIntent.ToggleAdvancedAuth) }) {
                Text(
                    if (state.showAdvancedAuth) "Ẩn Service Token nâng cao"
                    else "Hoặc dùng Service Token nâng cao (Tuỳ chọn)"
                )
            }

            if (state.showAdvancedAuth) {
                OutlinedTextField(
                    value = state.cfClientIdInput,
                    onValueChange = { onIntent(DashboardUiIntent.UpdateCfClientId(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CF-Access-Client-Id") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.cfClientSecretInput,
                    onValueChange = { onIntent(DashboardUiIntent.UpdateCfClientSecret(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CF-Access-Client-Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }

            OutlinedButton(
                onClick = { onIntent(DashboardUiIntent.SaveConfig) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lưu cấu hình")
            }
        }
    }
}
