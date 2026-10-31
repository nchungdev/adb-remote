package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.tunnel.domain.HostCommandRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HostActivitySection(
    commands: List<HostCommandRecord>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (commands.isNotEmpty()) Color(0xFF22C55E) else Color(0xFF64748B))
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Lệnh & Tiến trình từ máy tính",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = commands.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (commands.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text(
                            text = "Xóa nhật ký",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (commands.isEmpty()) {
                Text(
                    text = "Chưa ghi nhận lệnh nào từ máy tính Mac mini. Khi máy tính chạy ADB shell, chụp màn hình hoặc thăm dò specs, tiến trình sẽ xuất hiện ở đây.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val displayed = if (isExpanded) commands else commands.take(5)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    displayed.forEach { cmd ->
                        HostCommandRow(cmd)
                    }
                }

                if (commands.size > 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (isExpanded) "Thu gọn bớt" else "Xem tất cả (${commands.size} lệnh)",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HostCommandRow(cmd: HostCommandRecord) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(cmd.timestampEpochMs) { timeFormat.format(Date(cmd.timestampEpochMs)) }

    val statusColor = when (cmd.status) {
        "success" -> Color(0xFF22C55E)
        "running" -> Color(0xFF38BDF8)
        else -> Color(0xFFEF4444)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (cmd.outputSnippet.isNotBlank()) expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = cmd.source.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = if (cmd.status == "running") "Đang chạy" else "${cmd.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = cmd.command,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (cmd.outputSnippet.isNotBlank()) {
                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF090D16))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = cmd.outputSnippet,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFA5F3FC)
                            )
                        }
                    }
                }
            }
        }
    }
}
