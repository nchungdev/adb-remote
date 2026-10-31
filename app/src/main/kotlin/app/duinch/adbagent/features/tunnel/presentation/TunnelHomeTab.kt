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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.updater.presentation.CompactUpdateAlertBanner
import app.duinch.adbagent.features.updater.presentation.UpdaterUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdaterUiState

@Composable
fun TunnelHomeTab(
    state: DashboardUiState,
    updaterState: UpdaterUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onUpdaterIntent: (UpdaterUiIntent) -> Unit
) {
    val ext = AdbTheme.colors

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
                if (state.connectionState is TunnelConnectionState.Connected) ext.statusConnected else ext.cardBorder
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
                        .size(84.dp)
                        .background(animatedContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ShieldBolt,
                        contentDescription = null,
                        tint = animatedTint,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = animatedContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = animatedTint,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3-Column Bento Telemetry Strip
                val connected = state.connectionState as? TunnelConnectionState.Connected
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryTile(
                        label = "Mạng",
                        value = state.activeNetwork.label,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "Cổng ADB",
                        value = state.localAdbPortInput.ifBlank { "5555" },
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "Lưu lượng",
                        value = formatBytes(connected?.bytesBridged ?: 0L),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

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

        // Quick Readiness Checklist Section
        Text(
            text = "Trạng thái sẵn sàng",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        val adbReady = state.pairing.pairingState is AdbPairingState.PairedAndConnected ||
            state.localAdbPortInput == "5555"
        ReadinessRowCard(
            title = "1. Cổng ADB nội bộ (${state.localAdbPortInput.ifBlank { "5555" }})",
            subtitle = if (state.pairing.pairingState is AdbPairingState.PairedAndConnected) {
                "Đã ghép đôi và cố định cổng 5555 cho 4G/5G"
            } else {
                "Bấm để ghép đôi lần đầu qua Thanh thông báo"
            },
            isReady = adbReady && state.pairing.pairingState is AdbPairingState.PairedAndConnected,
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

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
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
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
private fun ReadinessRowCard(
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

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
