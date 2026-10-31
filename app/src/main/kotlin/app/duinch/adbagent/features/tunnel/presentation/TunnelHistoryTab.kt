package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.tunnel.domain.HostCommandRecord
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import kotlinx.coroutines.delay

@Composable
fun TunnelHistoryTab(
    state: DashboardUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val nowEpochMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000L)
            value = System.currentTimeMillis()
        }
    }

    val totalBytes = state.history.sumOf { it.bytesBridged }
    val activeSessions = state.history.count { it.isActive }
    val hostGroupTitle = "Mac mini (${state.serverUrlInput.ifBlank { "adb.duinch.app" }.substringAfter("://")})"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Telemetry Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = AppIcons.Clock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "Thống kê lịch sử",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (state.history.isNotEmpty() || state.hostCommands.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                onIntent(DashboardUiIntent.ClearHistory)
                                onIntent(DashboardUiIntent.ClearHostCommands)
                            }
                        ) {
                            Text(
                                text = "Xóa tất cả",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricMiniBox(
                        title = "Tổng phiên",
                        value = "${state.history.size}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniBox(
                        title = "Đang nối",
                        value = "$activeSessions",
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniBox(
                        title = "Lưu lượng",
                        value = formatBytes(totalBytes),
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniBox(
                        title = "Lệnh máy",
                        value = "${state.hostCommands.size}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Group 1: Remote Host Device (Mac mini / adb.duinch.app)
        DeviceHistoryCard(
            deviceTitle = hostGroupTitle,
            deviceSubtitle = "Đường hầm Cloudflare Tunnel • Cổng 15555",
            isOnline = state.connectionState is TunnelConnectionState.Connected,
            history = state.history,
            commands = state.hostCommands.filter { it.source != "Embedded Termux" },
            isHistoryExpanded = state.showAllHistory,
            isCommandsExpanded = state.showAllHostCommands,
            nowEpochMs = nowEpochMs,
            onToggleHistoryExpand = { onIntent(DashboardUiIntent.ToggleHistoryExpand) },
            onToggleCommandsExpand = { onIntent(DashboardUiIntent.ToggleHostCommandsExpand) },
            onClearHistory = { onIntent(DashboardUiIntent.ClearHistory) },
            onClearCommands = { onIntent(DashboardUiIntent.ClearHostCommands) }
        )

        // Group 2: Local ADB / Embedded Termux (if commands exist)
        val termuxCommands = state.hostCommands.filter { it.source == "Embedded Termux" }
        if (termuxCommands.isNotEmpty()) {
            DeviceHistoryCard(
                deviceTitle = "Thiết bị nội bộ (Embedded Termux)",
                deviceSubtitle = "Phiên lệnh ADB Shell trực tiếp trên điện thoại",
                isOnline = true,
                history = emptyList(),
                commands = termuxCommands,
                isHistoryExpanded = false,
                isCommandsExpanded = state.showAllHostCommands,
                nowEpochMs = nowEpochMs,
                onToggleHistoryExpand = {},
                onToggleCommandsExpand = { onIntent(DashboardUiIntent.ToggleHostCommandsExpand) },
                onClearHistory = {},
                onClearCommands = { onIntent(DashboardUiIntent.ClearHostCommands) }
            )
        }
    }
}

@Composable
private fun DeviceHistoryCard(
    deviceTitle: String,
    deviceSubtitle: String,
    isOnline: Boolean,
    history: List<TunnelHistoryEntry>,
    commands: List<HostCommandRecord>,
    isHistoryExpanded: Boolean,
    isCommandsExpanded: Boolean,
    nowEpochMs: Long,
    onToggleHistoryExpand: () -> Unit,
    onToggleCommandsExpand: () -> Unit,
    onClearHistory: () -> Unit,
    onClearCommands: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Device Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isOnline) AdbTheme.colors.statusConnectedContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Text(
                            text = deviceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = deviceSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOnline) AdbTheme.colors.statusConnectedContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isOnline) "TRỰC TUYẾN" else "NGOẠI TUYẾN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) AdbTheme.colors.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Embedded Host Commands for this device
            HostActivitySection(
                commands = commands,
                isExpanded = isCommandsExpanded,
                onToggleExpand = onToggleCommandsExpand,
                onClear = onClearCommands
            )

            // Embedded Tunnel Sessions for this device
            if (history.isNotEmpty()) {
                TunnelHistorySection(
                    history = history,
                    showAll = isHistoryExpanded,
                    nowEpochMs = nowEpochMs,
                    onToggleExpand = onToggleHistoryExpand,
                    onClearHistory = onClearHistory
                )
            }
        }
    }
}

@Composable
private fun MetricMiniBox(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
