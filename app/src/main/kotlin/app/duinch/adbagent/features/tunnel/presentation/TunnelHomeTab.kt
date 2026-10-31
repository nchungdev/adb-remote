package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.updater.presentation.CompactUpdateAlertBanner
import app.duinch.adbagent.features.updater.presentation.UpdaterUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdaterUiState
import kotlinx.coroutines.delay

@Composable
fun TunnelHomeTab(
    state: DashboardUiState,
    updaterState: UpdaterUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onUpdaterIntent: (UpdaterUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    val clipboard = LocalClipboardManager.current
    val connected = state.connectionState as? TunnelConnectionState.Connected

    val nowEpochMs by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = connected?.connectedAtEpochMs
    ) {
        if (connected != null) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    val (badgeLabel, subtitle, orbTint, orbContainer) = when (val conn = state.connectionState) {
        TunnelConnectionState.Disconnected -> StatusVisual(
            badge = "CHƯA KẾT NỐI",
            subtitle = "Sẵn sàng mở đường hầm bảo mật về Mac mini (0 port mở)",
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer
        )
        is TunnelConnectionState.Connecting -> StatusVisual(
            badge = "ĐANG KẾT NỐI...",
            subtitle = "Đang thiết lập kênh TLS về adb.duinch.app",
            tint = ext.statusWarning,
            container = ext.statusWarningContainer
        )
        is TunnelConnectionState.Connected -> StatusVisual(
            badge = "ĐANG TRỰC TUYẾN",
            subtitle = conn.mappedServerPort?.let { "Mac mini đã nhận thiết bị tại 127.0.0.1:$it" }
                ?: "Đã kết nối thông suốt về Mac mini",
            tint = ext.statusConnected,
            container = ext.statusConnectedContainer
        )
        is TunnelConnectionState.Reconnecting -> StatusVisual(
            badge = "ĐANG TỰ NỐI LẠI (LẦN ${conn.attempt})",
            subtitle = "${conn.reason} • Thử lại sau ${conn.nextRetryDelayMs / 1000}s",
            tint = ext.statusWarning,
            container = ext.statusWarningContainer
        )
        is TunnelConnectionState.Error -> StatusVisual(
            badge = "GIÁN ĐOẠN KẾT NỐI",
            subtitle = conn.message,
            tint = MaterialTheme.colorScheme.error,
            container = MaterialTheme.colorScheme.errorContainer
        )
    }

    val animatedContainer by animateColorAsState(targetValue = orbContainer, label = "orbContainer")
    val animatedTint by animateColorAsState(targetValue = orbTint, label = "orbTint")

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CompactUpdateAlertBanner(state = updaterState, onIntent = onUpdaterIntent)

        // Hero Connection Switchboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(
                1.dp,
                if (connected != null) ext.statusConnected else ext.cardBorder
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(animatedContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ShieldBolt,
                        contentDescription = null,
                        tint = animatedTint,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(color = animatedContainer, shape = CircleShape) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = animatedTint,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    val mappedAddr = connected?.mappedServerPort?.let { "127.0.0.1:$it" }
                    if (mappedAddr != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = ext.surfaceElevated,
                            shape = CircleShape,
                            modifier = Modifier.clickable(role = Role.Button) {
                                clipboard.setText(AnnotatedString(mappedAddr))
                            }
                        ) {
                            Icon(
                                imageVector = AppIcons.Copy,
                                contentDescription = "Sao chép $mappedAddr",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 2x2 Bento Telemetry Grid (Uptime, Network, Port, Traffic)
                val uptimeText = if (connected != null) {
                    formatDurationVi((nowEpochMs - connected.connectedAtEpochMs).coerceAtLeast(0L))
                } else {
                    "--:--"
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryTile(
                            label = "Đã kết nối",
                            value = uptimeText,
                            monospace = true,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            label = "Mạng",
                            value = state.activeNetwork.label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryTile(
                            label = "Cổng ADB",
                            value = state.localAdbPortInput.ifBlank { "5555" },
                            monospace = true,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            label = "Lưu lượng",
                            value = formatBytesVi(connected?.bytesBridged ?: 0L),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { onIntent(DashboardUiIntent.ToggleTunnel) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (state.isTunnelActive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                ) {
                    Text(
                        text = if (state.isTunnelActive) "Ngắt kết nối đường hầm" else "Bật kết nối về Mac mini",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // 3 Connection Modes: USB, Wi-Fi Local, Cloudflare Tunnel
        ConnectionModesSection(
            wifiIp = state.localWifiIp,
            adbPort = state.localAdbPortInput.ifBlank { "5555" },
            isUsbConnected = state.isUsbConnected,
            isAtHomeWifi = state.isAtHomeWifi,
            homeSsid = state.trustedHomeSsidInput.ifBlank { state.currentWifiSsid },
            onCopyCommand = { cmd -> clipboard.setText(AnnotatedString(cmd)) }
        )

        // Quick Readiness Checklist Section
        Text(
            text = "Trạng thái sẵn sàng",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        val isTunnelOnline = state.connectionState is TunnelConnectionState.Connected
        val locallyPaired = state.pairing.pairingState is AdbPairingState.PairedAndConnected
        val portDisplay = state.localAdbPortInput.ifBlank { "5555" }
        val (adbSub, adbOk) = when {
            locallyPaired -> "Đã ghép đôi nội bộ & cố định cổng 5555 cho 4G/5G" to true
            isTunnelOnline && portDisplay == "5555" ->
                "Đang hoạt động qua cổng 5555 (hỗ trợ cả Wi-Fi & 4G/5G)" to true
            isTunnelOnline ->
                "Đang thông suốt về Mac mini qua cổng $portDisplay (Không cần ghép đôi lại)" to true
            else ->
                "Tùy chọn cho 4G/5G & Terminal nội bộ (không bắt buộc nếu Mac đã nhận máy)" to false
        }

        ReadinessRowCard(
            title = "1. Cổng ADB trên máy ($portDisplay)",
            subtitle = adbSub,
            isReady = adbOk,
            onClick = { onIntent(DashboardUiIntent.SelectTab(AppTab.PAIRING)) }
        )

        ReadinessRowCard(
            title = "2. Xác thực Cloudflare Access",
            subtitle = if (state.hasCloudflareAuth) {
                "Đã lưu phiên xác thực an toàn cho adb.duinch.app"
            } else {
                "Bấm để đăng nhập 1 chạm trước khi mở đường hầm"
            },
            isReady = state.hasCloudflareAuth,
            onClick = { onIntent(DashboardUiIntent.OpenCloudflareLogin) }
        )
    }
}

private data class StatusVisual(
    val badge: String,
    val subtitle: String,
    val tint: androidx.compose.ui.graphics.Color,
    val container: androidx.compose.ui.graphics.Color
)
