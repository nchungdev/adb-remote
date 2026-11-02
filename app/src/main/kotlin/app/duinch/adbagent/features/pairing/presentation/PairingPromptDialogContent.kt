package app.duinch.adbagent.features.pairing.presentation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PairingPromptCard(
    pairingCode: String,
    activePairPort: Int,
    isSubmitting: Boolean,
    errorMessage: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
@Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, AdbTheme.colors.cardBorder),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.LinkWireless,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Ghép nối không dây",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val portHint = if (activePairPort > 0) "Cổng: $activePairPort" else "Đang dò cổng..."
                        Text(
                            text = "$portHint • Nhìn mã ở hộp thoại bên dưới",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp),
                    enabled = !isSubmitting
                ) {
                    Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 6 Ô hiển thị mã số
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (i in 0 until 6) {
                    val digit = pairingCode.getOrNull(i)?.toString().orEmpty()
                    val isCurrent = i == pairingCode.length && !isSubmitting
                    Surface(
                        modifier = Modifier.size(width = 42.dp, height = 46.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = AdbTheme.colors.surfaceElevated,
                        border = BorderStroke(
                            width = if (isCurrent) 2.dp else 1.dp,
                            color = when {
                                errorMessage != null -> MaterialTheme.colorScheme.error
                                isCurrent -> MaterialTheme.colorScheme.primary
                                digit.isNotEmpty() -> AdbTheme.colors.statusConnected
                                else -> AdbTheme.colors.cardBorder
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = digit,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isSubmitting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "Đang xác thực mã ghép nối...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // Built-in Numpad
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("PASTE", "0", "DEL")
                    )
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (key in row) {
                                CompactKeyButton(
                                    key = key,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (key) {
                                            "DEL" -> {
                                                if (pairingCode.isNotEmpty()) {
                                                    onCodeChange(pairingCode.dropLast(1))
                                                }
                                            }
                                            "PASTE" -> {
                                                val clipText = clipboard.getText()?.text.orEmpty()
                                                val digits = clipText.filter(Char::isDigit).take(6)
                                                if (digits.length == 6) {
                                                    onCodeChange(digits)
                                                    onSubmit(digits)
                                                } else if (digits.isNotEmpty()) {
                                                    onCodeChange(digits)
                                                }
                                            }
                                            else -> {
                                                if (pairingCode.length < 6) {
                                                    val next = pairingCode + key
                                                    onCodeChange(next)
                                                    if (next.length == 6) {
                                                        onSubmit(next)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactKeyButton(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = when (key) {
            "PASTE", "DEL" -> MaterialTheme.colorScheme.surfaceVariant
            else -> AdbTheme.colors.surfaceElevated
        },
        border = BorderStroke(0.5.dp, AdbTheme.colors.cardBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (key) {
                "DEL" -> Text(
                    text = "⌫",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                "PASTE" -> Text(
                    text = "Dán",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                else -> Text(
                    text = key,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
