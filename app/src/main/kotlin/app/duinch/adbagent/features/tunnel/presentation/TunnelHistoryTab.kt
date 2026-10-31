package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
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
    val isOnline = state.connectionState is TunnelConnectionState.Connected
    val hostGroupTitle = "Mac mini (${state.serverUrlInput.ifBlank { "adb.duinch.app" }.substringAfter("://")})"

    val hostCommands = state.hostCommands.filter { it.source != "Embedded Termux" }
    val termuxCommands = state.hostCommands.filter { it.source == "Embedded Termux" }

    // Mặc định chỉ expand thiết bị đang active; các thiết bị/mục khác mở bằng nút lịch sử
    var isHostExpanded by remember(isOnline) { mutableStateOf(isOnline) }
    var isTermuxExpanded by remember { mutableStateOf(false) }
    var isHistoryExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card 1: Telemetry Summary Card
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
                            text = "Nhật ký kết nối",
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
                        value = formatBytesVi(totalBytes),
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

        // Card 2: Lệnh từ máy tính Mac mini (Mặc định mở khi active)
        CollapsibleHistoryCard(
            title = "Lệnh từ máy tính",
            subtitle = "$hostGroupTitle • ${if (isOnline) "Đang trực tuyến" else "Ngoại tuyến"}",
            badgeCount = hostCommands.size,
            isExpanded = isHostExpanded,
            onToggleExpand = { isHostExpanded = !isHostExpanded },
            statusDotColor = if (isOnline) AdbTheme.colors.statusConnectedContainer else MaterialTheme.colorScheme.surfaceVariant,
            actionButton = if (hostCommands.isNotEmpty()) {
                {
                    TextButton(onClick = { onIntent(DashboardUiIntent.ClearHostCommands) }) {
                        Text(
                            text = "Xóa",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else null
        ) {
            HostActivitySection(
                commands = hostCommands,
                isExpanded = state.showAllHostCommands,
                onToggleExpand = { onIntent(DashboardUiIntent.ToggleHostCommandsExpand) }
            )
        }

        // Card 3: Embedded Termux (nếu có lệnh)
        if (termuxCommands.isNotEmpty()) {
            CollapsibleHistoryCard(
                title = "Thiết bị nội bộ (Termux)",
                subtitle = "Phiên lệnh ADB Shell trực tiếp trên máy",
                badgeCount = termuxCommands.size,
                isExpanded = isTermuxExpanded,
                onToggleExpand = { isTermuxExpanded = !isTermuxExpanded },
                statusDotColor = AdbTheme.colors.statusConnectedContainer
            ) {
                HostActivitySection(
                    commands = termuxCommands,
                    isExpanded = state.showAllHostCommands,
                    onToggleExpand = { onIntent(DashboardUiIntent.ToggleHostCommandsExpand) }
                )
            }
        }

        // Card 4: Lịch sử phiên kết nối (Mặc định thu gọn, mở bằng nút lịch sử)
        CollapsibleHistoryCard(
            title = "Lịch sử phiên kết nối",
            subtitle = "Các phiên kết nối đường hầm đã ghi nhận",
            badgeCount = state.history.size,
            isExpanded = isHistoryExpanded,
            onToggleExpand = { isHistoryExpanded = !isHistoryExpanded },
            leadingIcon = AppIcons.Clock,
            actionButton = if (state.history.any { !it.isActive }) {
                {
                    TextButton(onClick = { onIntent(DashboardUiIntent.ClearHistory) }) {
                        Text(
                            text = "Xóa cũ",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else null
        ) {
            TunnelHistorySection(
                history = state.history,
                showAll = state.showAllHistory,
                nowEpochMs = nowEpochMs,
                onToggleExpand = { onIntent(DashboardUiIntent.ToggleHistoryExpand) },
                onClearHistory = { onIntent(DashboardUiIntent.ClearHistory) }
            )
        }
    }
}

@Composable
private fun CollapsibleHistoryCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeCount: Int? = null,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    statusDotColor: androidx.compose.ui.graphics.Color? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actionButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (statusDotColor != null) {
                        Surface(
                            shape = CircleShape,
                            color = statusDotColor,
                            modifier = Modifier.size(10.dp)
                        ) {}
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (badgeCount != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "$badgeCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    actionButton?.invoke()
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) AppIcons.ChevronDown else AppIcons.ChevronRight,
                            contentDescription = if (isExpanded) "Thu gọn" else "Mở lịch sử",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                    content()
                }
            }
        }
    }
}
