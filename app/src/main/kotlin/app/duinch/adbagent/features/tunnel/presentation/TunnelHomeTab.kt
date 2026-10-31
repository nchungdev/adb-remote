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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
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
            subtitle = "Sẵn sàng mở đường hầm bảo mật về Mac mini",
            tint = MaterialTheme.colorScheme.primary,
            container = MaterialTheme.colorScheme.primaryContainer
        )
        is TunnelConnectionState.Connecting -> StatusVisual(
            badge = "ĐANG KẾT NỐI...",
            subtitle = "Đang thiết lập kênh TLS về Mac mini",
            tint = ext.statusWarning,
            container = ext.statusWarningContainer
        )
        is TunnelConnectionState.Connected -> StatusVisual(
            badge = "ĐANG TRỰC TUYẾN",
            subtitle = conn.mappedServerPort?.let { "127.0.0.1:$it" } ?: "Đã kết nối thông suốt",
            tint = ext.statusConnected,
            container = ext.statusConnectedContainer
        )
        is TunnelConnectionState.Reconnecting -> StatusVisual(
            badge = "ĐANG NỐI LẠI (${conn.attempt})",
            subtitle = "Thử lại sau ${conn.nextRetryDelayMs / 1000}s",
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
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .background(animatedContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.ShieldBolt,
                        contentDescription = null,
                        tint = animatedTint,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(color = animatedContainer, shape = CircleShape) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = animatedTint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                                    .padding(5.dp)
                                    .size(13.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Telemetry 2x2 Grid with Synchronized Tile Heights
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
                            label = "Thời gian",
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
                        .heightIn(min = 52.dp),
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
                        text = if (state.isTunnelActive) "Ngắt kết nối" else "Kết nối Mac mini",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }
            }
        }

        // Active Connection Mode Card (Direct, Wi-Fi Local or Tunnel)
        ActiveConnectionModeCard(
            wifiIp = state.localWifiIp,
            adbPort = state.localAdbPortInput.ifBlank { "5555" },
            isUsbConnected = state.isUsbConnected,
            isAtHomeWifi = state.isAtHomeWifi,
            homeSsid = state.trustedHomeSsidInput.ifBlank { state.currentWifiSsid },
            onCopyCommand = { cmd -> clipboard.setText(AnnotatedString(cmd)) }
        )
    }
}

private data class StatusVisual(
    val badge: String,
    val subtitle: String,
    val tint: androidx.compose.ui.graphics.Color,
    val container: androidx.compose.ui.graphics.Color
)
