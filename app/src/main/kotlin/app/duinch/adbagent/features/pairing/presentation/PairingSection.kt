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
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.pairing.domain.AdbPairingState

@Composable
fun PairingSection(
    state: PairingUiState,
    onIntent: (PairingUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    var showManualSplitScreen by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Status Banner Card
        val isPaired = state.pairingState is AdbPairingState.PairedAndConnected
        val isFailed = state.pairingState is AdbPairingState.Failed
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
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Ghép đôi Wireless ADB",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val statusText = when (val ps = state.pairingState) {
                            is AdbPairingState.PairedAndConnected ->
                                "Đã bật cổng cố định ${ps.connectPort} — hoạt động bền bỉ cả trên 4G/5G"
                            is AdbPairingState.Failed -> ps.reason
                            AdbPairingState.PairingInProgress -> "Đang trao đổi khóa RSA và mở cổng 5555..."
                            else -> when {
                                state.discoveredPairingPort != null ->
                                    "Phát hiện cổng Ghép đôi: ${state.discoveredPairingPort}"
                                state.discoveredConnectPort != null ->
                                    "Phát hiện cổng Gỡ lỗi không dây: ${state.discoveredConnectPort}"
                                else ->
                                    "Đang quét mDNS tự động trên mạng Wi-Fi hiện tại"
                            }
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPaired) ext.statusConnected else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = ext.surfaceElevated,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (state.isAccessibilityEnabled) {
                            Text("1. Bấm 'Ghép đôi tự động 1-chạm' bên dưới.", style = MaterialTheme.typography.bodySmall)
                            Text("2. Ứng dụng tự mở Gỡ lỗi không dây, đọc mã và mở cổng 5555.", style = MaterialTheme.typography.bodySmall)
                            Text("3. Hoàn tất tức thì không cần gõ mã hay chia đôi màn hình.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("1. Bật Trợ năng cho ADB Agent (chỉ cần cấp quyền 1 lần duy nhất).", style = MaterialTheme.typography.bodySmall)
                            Text("2. Sau khi bật, tính năng Ghép đôi tự động 1-chạm sẽ sẵn sàng.", style = MaterialTheme.typography.bodySmall)
                            Text("3. Hoặc sử dụng cách Ghép đôi qua thông báo ở nút phụ bên dưới.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.isAccessibilityEnabled) {
                    Button(
                        onClick = { onIntent(PairingUiIntent.StartAutoPairing) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Icon(AppIcons.ShieldBolt, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ghép đôi tự động 1-chạm")
                    }
                } else {
                    Button(
                        onClick = { onIntent(PairingUiIntent.OpenAccessibilitySettings) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                    ) {
                        Icon(AppIcons.Sliders, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bật Trợ năng để Ghép đôi tự động 1-chạm")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onIntent(PairingUiIntent.StartNotificationPairing) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Icon(AppIcons.LinkWireless, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ghép đôi thủ công qua Thông báo")
                }

                if (state.effectiveConnectPort != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onIntent(PairingUiIntent.ActivateTcpip5555) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                    ) {
                        Text("Máy đã ghép đôi trước đó → Kích hoạt cổng 5555")
                    }
                }
            }
        }

        // Progressive Disclosure: Manual Split-Screen Mode
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, ext.cardBorder),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { showManualSplitScreen = !showManualSplitScreen }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nhập mã trực tiếp (Chia đôi màn hình)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Dùng khi bạn mở Cài đặt và ADB Agent song song",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = AppIcons.ChevronDown,
                        contentDescription = if (showManualSplitScreen) "Thu gọn" else "Mở rộng",
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (showManualSplitScreen) 180f else 0f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(visible = showManualSplitScreen) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = state.pairingPortInput,
                                onValueChange = { onIntent(PairingUiIntent.UpdatePairingPort(it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Cổng Pair") },
                                placeholder = { Text(state.discoveredPairingPort?.toString() ?: "46653") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.pairingCodeInput,
                                onValueChange = { onIntent(PairingUiIntent.UpdatePairingCode(it)) },
                                modifier = Modifier.weight(1f),
                                label = { Text("Mã 6 chữ số") },
                                placeholder = { Text("611208") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { if (state.canSubmitPair) onIntent(PairingUiIntent.SubmitPair) }
                                ),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = state.connectPortInput,
                            onValueChange = { onIntent(PairingUiIntent.UpdateConnectPort(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Cổng Connect nội bộ (mDNS tự điền)") },
                            placeholder = { Text(state.discoveredConnectPort?.toString() ?: "38517") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true
                        )

                        Button(
                            onClick = { onIntent(PairingUiIntent.SubmitPair) },
                            enabled = state.canSubmitPair,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Text(
                                if (state.pairingState is AdbPairingState.PairingInProgress) "Đang ghép đôi..."
                                else "Xác nhận Ghép đôi"
                            )
                        }
                    }
                }
            }
        }
    }
}
