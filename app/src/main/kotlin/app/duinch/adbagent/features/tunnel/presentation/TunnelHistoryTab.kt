package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AppIcons
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

    var selectedFilter by remember { mutableStateOf(HistoryTabFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var visibleLimit by remember { mutableIntStateOf(15) }

    val totalBytes = state.history.sumOf { it.bytesBridged }
    val activeSessions = state.history.count { it.isActive }

    val feedItems = remember(selectedFilter, searchQuery, state.hostCommands, state.history) {
        buildHistoryFeed(selectedFilter, searchQuery, state.hostCommands, state.history)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HistoryOverviewCard(
            commandCount = state.hostCommands.size,
            sessionCount = state.history.size,
            activeCount = activeSessions,
            totalBytes = totalBytes,
            hasEntries = state.hostCommands.isNotEmpty() || state.history.isNotEmpty(),
            onClearClick = { confirmClear = true }
        )

        HistoryFilterAndSearchBar(
            selected = selectedFilter,
            onSelect = { selectedFilter = it },
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            termuxCount = state.hostCommands.count { it.source == "Embedded Termux" }
        )

        if (feedItems.isEmpty()) {
            HistoryEmptyView(isSearching = searchQuery.isNotBlank())
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val displayed = feedItems.take(visibleLimit)
                displayed.forEach { item ->
                    when (item) {
                        is HistoryFeedItem.Command -> HistoryCommandCard(cmd = item.record)
                        is HistoryFeedItem.Session -> HistorySessionCard(entry = item.entry, nowEpochMs = nowEpochMs)
                    }
                }

                if (feedItems.size > visibleLimit) {
                    TextButton(
                        onClick = { visibleLimit += 20 },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Xem thêm (${feedItems.size - visibleLimit} mục nữa)")
                    }
                } else if (visibleLimit > 15 && feedItems.size > 15) {
                    TextButton(
                        onClick = { visibleLimit = 15 },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Thu gọn danh sách")
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xoá toàn bộ nhật ký?") },
            text = { Text("Lịch sử các phiên kết nối và danh sách lệnh ADB đã thực thi sẽ bị xoá khỏi bộ nhớ.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onIntent(DashboardUiIntent.ClearHistory)
                        onIntent(DashboardUiIntent.ClearHostCommands)
                    }
                ) {
                    Text("Xoá tất cả", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Hủy") }
            }
        )
    }
}

@Composable
private fun HistoryOverviewCard(
    commandCount: Int,
    sessionCount: Int,
    activeCount: Int,
    totalBytes: Long,
    hasEntries: Boolean,
    onClearClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(34.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(AppIcons.Clock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column {
                        Text("Nhật ký hoạt động", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Lịch sử phiên & lệnh ADB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasEntries) {
                    IconButton(onClick = onClearClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Xoá nhật ký", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricMiniBox(title = "Lệnh máy", value = "$commandCount", modifier = Modifier.weight(1f))
                MetricMiniBox(title = "Phiên nối", value = "$sessionCount", modifier = Modifier.weight(1f))
                MetricMiniBox(title = "Đang nối", value = "$activeCount", modifier = Modifier.weight(1f))
                MetricMiniBox(title = "Lưu lượng", value = formatBytesVi(totalBytes), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HistoryFilterAndSearchBar(
    selected: HistoryTabFilter,
    onSelect: (HistoryTabFilter) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    termuxCount: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HistoryTabFilter.entries.forEach { filter ->
                if (filter == HistoryTabFilter.TERMUX && termuxCount == 0) return@forEach
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelect(filter) },
                    label = { Text(filter.label, fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            placeholder = { Text("Tìm kiếm lệnh, cổng hoặc tiến trình...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, contentDescription = "Xoá tìm", modifier = Modifier.size(16.dp)) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
private fun HistoryEmptyView(isSearching: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Default.Search else AppIcons.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isSearching) "Không tìm thấy kết quả phù hợp" else "Chưa có hoạt động nào",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isSearching) "Hãy thử tìm với từ khoá khác." else "Khi máy chủ gửi lệnh ADB hoặc thiết lập phiên nối, tiến trình sẽ xuất hiện ở đây.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
