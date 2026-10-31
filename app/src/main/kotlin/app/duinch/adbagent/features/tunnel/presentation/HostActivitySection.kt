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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (commands.isEmpty()) {
            Text(
                text = "Chưa ghi nhận lệnh nào từ máy tính Mac mini. Khi máy tính chạy ADB shell, chụp màn hình hoặc thăm dò specs, tiến trình sẽ xuất hiện ở đây.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            val displayed = if (isExpanded) commands else commands.take(5)
            Column(modifier = Modifier.fillMaxWidth()) {
                for ((idx, cmd) in displayed.withIndex()) {
                    HostCommandRow(cmd)
                    if (idx < displayed.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            if (commands.size > 5) {
                Spacer(modifier = Modifier.height(6.dp))
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = cmd.outputSnippet.isNotBlank()) { expanded = !expanded }
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = cmd.source.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
