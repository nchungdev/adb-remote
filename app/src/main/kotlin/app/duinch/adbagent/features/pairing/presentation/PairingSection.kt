package app.duinch.adbagent.features.pairing.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.features.pairing.domain.AdbPairingState

@Composable
fun PairingSection(
    state: PairingUiState,
    onIntent: (PairingUiIntent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "1. Ghép đôi Wireless ADB",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = when {
                        state.discoveredPairingPort != null ->
                            "Tự động phát hiện cổng ghép đôi: ${state.discoveredPairingPort}"
                        state.discoveredConnectPort != null ->
                            "Đã tìm thấy cổng ADB nội bộ: ${state.discoveredConnectPort}"
                        else ->
                            "Đang lắng nghe mDNS (_adb-tls-pairing._tcp)..."
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(onClick = { onIntent(PairingUiIntent.OpenDeveloperOptions) }) {
                    Text("Mở Cài đặt")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.pairingPortInput,
                    onValueChange = { onIntent(PairingUiIntent.UpdatePairingPort(it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Cổng Pair") },
                    placeholder = { Text(state.discoveredPairingPort?.toString() ?: "46653") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.pairingCodeInput,
                    onValueChange = { onIntent(PairingUiIntent.UpdatePairingCode(it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Mã 6 số") },
                    placeholder = { Text("611208") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.connectPortInput,
                onValueChange = { onIntent(PairingUiIntent.UpdateConnectPort(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Cổng Connect chính (tuỳ chọn, mDNS tự điền)") },
                placeholder = { Text(state.discoveredConnectPort?.toString() ?: "38517") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onIntent(PairingUiIntent.SubmitPair) },
                enabled = state.canSubmitPair,
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = when (state.pairingState) {
                    is AdbPairingState.PairingInProgress -> "Đang ghép đôi..."
                    is AdbPairingState.PairedAndConnected -> "Ghép đôi lại"
                    else -> "Ghép đôi & Kết nối nội bộ"
                }
                Text(label)
            }
        }
    }
}
