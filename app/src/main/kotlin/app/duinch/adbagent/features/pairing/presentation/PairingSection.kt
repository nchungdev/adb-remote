package app.duinch.adbagent.features.pairing.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState

@Composable
fun PairingSection(
    state: PairingUiState,
    onIntent: (PairingUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = AdbTheme.colors
    var showManualInput by rememberSaveable { mutableStateOf(false) }
    val isFailed = state.pairingState is AdbPairingState.Failed

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
        val statusText = when (val ps = state.pairingState) {
            is AdbPairingState.Failed -> ps.reason
            is AdbPairingState.PairingInProgress -> "Đang ghép nối..."
            else -> when {
                state.discoveredPairingPort != null -> "Đã tìm thấy cổng ghép nối: ${state.discoveredPairingPort}"
                state.discoveredConnectPort != null -> "Đã phát hiện cổng gỡ lỗi: ${state.discoveredConnectPort}"
                else -> null
            }
        }

        // Center / Main Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (statusText != null) {
                Surface(
                    color = if (isFailed) MaterialTheme.colorScheme.errorContainer else ext.statusConnectedContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFailed) MaterialTheme.colorScheme.error else ext.statusConnected,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Big, clean GIF illustration view
            PairingGifGuide(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Step instructions card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PairingStepRow(step = "1", text = "Bấm nút bên dưới để mở Cài đặt, gạt bật \"Gỡ lỗi không dây\"")
                    PairingStepRow(step = "2", text = "Chạm dòng \"Ghép nối bằng mã ghép nối\" để hiện mã 6 số")
                    PairingStepRow(step = "3", text = "Nhấn Phím Nguồn + Giảm âm lượng để AI tự động ghép đôi")
                }
            }

            AnimatedVisibility(visible = showManualInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state.pairingPortInput,
                        onValueChange = { onIntent(PairingUiIntent.UpdatePairingPort(it)) },
                        label = { Text("Cổng ghép nối") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.pairingCodeInput,
                        onValueChange = { onIntent(PairingUiIntent.UpdatePairingCode(it)) },
                        label = { Text("Mã ghép nối (6 chữ số)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onIntent(PairingUiIntent.SubmitPair) },
                        enabled = state.canSubmitPair,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (state.pairingState is AdbPairingState.PairingInProgress) "Đang ghép nối..."
                            else "Xác nhận ghép nối",
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Bottom Action Area: Primary action docked at the bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onIntent(PairingUiIntent.StartNotificationPairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(AppIcons.LinkWireless, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mở Cài đặt gỡ lỗi",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
            }

            if (state.effectiveConnectPort != null) {
                OutlinedButton(
                    onClick = { onIntent(PairingUiIntent.ActivateTcpip5555) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Bật lại ADB (Cổng ${state.effectiveConnectPort})", maxLines = 1)
                }
            }

            TextButton(
                onClick = { showManualInput = !showManualInput }
            ) {
                Text(
                    text = if (showManualInput) "Ẩn nhập thủ công" else "Nhập mã thủ công (nâng cao)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PairingStepRow(step: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
