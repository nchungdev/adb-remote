package app.duinch.adbagent.features.pairing.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState

@Composable
fun PairingSection(
    state: PairingUiState,
    onIntent: (PairingUiIntent) -> Unit,
    onSwitchToConnect: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = AdbTheme.colors
    var showManualInput by rememberSaveable { mutableStateOf(false) }
    val isFailed = state.pairingState is AdbPairingState.Failed

    Card(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, ext.cardBorder),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            PairingTutorialCarousel()

            Spacer(modifier = Modifier.padding(top = 2.dp))

            Button(
                onClick = { onIntent(PairingUiIntent.StartNotificationPairing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(AppIcons.LinkWireless, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mở Cài đặt gỡ lỗi", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }

            if (state.effectiveConnectPort != null) {
                OutlinedButton(
                    onClick = { onIntent(PairingUiIntent.ActivateTcpip5555) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Bật lại ADB (Cổng ${state.effectiveConnectPort})", maxLines = 1)
                }
            }

            TextButton(
                onClick = { showManualInput = !showManualInput }
            ) {
                Text(
                    text = if (showManualInput) "Ẩn nhập thủ công" else "Nhập mã thủ công (nâng cao)",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            AnimatedVisibility(visible = showManualInput) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
                            .heightIn(min = 46.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            if (state.pairingState is AdbPairingState.PairingInProgress) "Đang ghép nối..."
                            else "Xác nhận ghép nối",
                            maxLines = 1
                        )
                    }
                }
            }

            if (onSwitchToConnect != null) {
                TextButton(
                    onClick = onSwitchToConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Vào màn hình Kết nối",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
