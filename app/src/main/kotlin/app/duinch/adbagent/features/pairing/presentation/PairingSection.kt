package app.duinch.adbagent.features.pairing.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
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
    var showManualSplitScreen by rememberSaveable { mutableStateOf(false) }
    val isPaired = state.pairingState is AdbPairingState.PairedAndConnected
    val isFailed = state.pairingState is AdbPairingState.Failed

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Flat Single-Layer Card: Không lồng thêm Surface/Card bên trong
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(
                1.dp,
                when {
                    isPaired -> ext.statusConnected
                    isFailed -> MaterialTheme.colorScheme.error
                    else -> ext.cardBorder
                }
            ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isPaired -> ext.statusConnectedContainer
                            isFailed -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isPaired -> AppIcons.CheckCircle
                                    isFailed -> AppIcons.AlertCircle
                                    else -> AppIcons.LinkWireless
                                },
                                contentDescription = null,
                                tint = when {
                                    isPaired -> ext.statusConnected
                                    isFailed -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ghép nối không dây",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusText = when (val ps = state.pairingState) {
                            is AdbPairingState.PairedAndConnected ->
                                "Đã sẵn sàng kết nối Wi-Fi & 4G/5G"
                            is AdbPairingState.Failed -> ps.reason
                            AdbPairingState.PairingInProgress -> "Đang ghép nối..."
                            else -> when {
                                state.discoveredPairingPort != null -> "Tìm thấy cổng ghép nối: ${state.discoveredPairingPort}"
                                state.discoveredConnectPort != null -> "Tìm thấy cổng gỡ lỗi: ${state.discoveredConnectPort}"
                                else -> "Cài đặt 1 lần duy nhất để kết nối không dây"
                            }
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPaired) ext.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Hướng dẫn hiển thị trực tiếp phẳng trên Card
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "1. Bấm nút bên dưới để mở Gỡ lỗi không dây.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2. Chọn \"Ghép nối bằng mã ghép nối\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3. Hộp thoại nhập mã sẽ tự động hiện lên.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { onIntent(PairingUiIntent.StartNotificationPairing) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                ) {
                    Icon(AppIcons.LinkWireless, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ghép nối nhanh", maxLines = 1)
                }

                if (state.effectiveConnectPort != null) {
                    OutlinedButton(
                        onClick = { onIntent(PairingUiIntent.ActivateTcpip5555) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                    ) {
                        Text("Bật lại ADB", maxLines = 1)
                    }
                }

                // Progressive disclosure cho trường hợp muốn gõ mã thủ công
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clickable(role = Role.Button) { showManualSplitScreen = !showManualSplitScreen }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nhập thủ công (nâng cao)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = AppIcons.ChevronDown,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (showManualSplitScreen) 180f else 0f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showManualSplitScreen) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.pairingPortInput,
                                onValueChange = { onIntent(PairingUiIntent.UpdatePairingPort(it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Cổng Pair", maxLines = 1) },
                                placeholder = { Text(state.discoveredPairingPort?.toString() ?: "46653") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.pairingCodeInput,
                                onValueChange = { onIntent(PairingUiIntent.UpdatePairingCode(it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Mã 6 số", maxLines = 1) },
                                placeholder = { Text("611208") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (state.canSubmitPair) onIntent(PairingUiIntent.SubmitPair) }),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = state.connectPortInput,
                            onValueChange = { onIntent(PairingUiIntent.UpdateConnectPort(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Cổng Connect", maxLines = 1) },
                            placeholder = { Text(state.discoveredConnectPort?.toString() ?: "38517") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            singleLine = true
                        )

                        Button(
                            onClick = { onIntent(PairingUiIntent.SubmitPair) },
                            enabled = state.canSubmitPair,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 46.dp)
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
                            text = "Đã có ADB trên máy? Vào màn hình Kết nối",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}
