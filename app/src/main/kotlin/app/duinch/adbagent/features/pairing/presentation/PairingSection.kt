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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            Text(
                text = "1. Ghép đôi & Mở cổng ADB nội bộ",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            val subtitle = when (val ps = state.pairingState) {
                is AdbPairingState.PairedAndConnected ->
                    "✅ Đã kích hoạt cổng ADB nội bộ ${ps.connectPort} (giữ sống cả khi chuyển sang 4G/5G)"
                is AdbPairingState.Failed ->
                    "❌ ${ps.reason}"
                else -> when {
                    state.discoveredPairingPort != null ->
                        "Đã thấy cổng Ghép đôi: ${state.discoveredPairingPort}"
                    state.discoveredConnectPort != null ->
                        "Đã thấy cổng Gỡ lỗi không dây: ${state.discoveredConnectPort}"
                    else ->
                        "Đang dò mDNS (_adb-tls-pairing._tcp)..."
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.pairingState is AdbPairingState.PairedAndConnected) {
                    Color(0xFF34D399)
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onIntent(PairingUiIntent.StartNotificationPairing) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ghép đôi lần đầu (Nhập mã trên Thông báo)",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.effectiveConnectPort != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onIntent(PairingUiIntent.ActivateTcpip5555) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Đã ghép đôi trước đó → Bật cổng 5555 cho 4G/5G")
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
                    label = { Text("Mã 6 số (Chia màn hình)") },
                    placeholder = { Text("611208") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onIntent(PairingUiIntent.SubmitPair) },
                enabled = state.canSubmitPair,
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = when (state.pairingState) {
                    is AdbPairingState.PairingInProgress -> "Đang ghép đôi..."
                    else -> "Ghép đôi trực tiếp tại đây"
                }
                Text(label)
            }
        }
    }
}
