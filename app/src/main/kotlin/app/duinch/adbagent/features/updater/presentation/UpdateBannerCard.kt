package app.duinch.adbagent.features.updater.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.features.updater.domain.UpdateStatus

@Composable
fun UpdateBannerCard(
    state: UpdaterUiState,
    onIntent: (UpdaterUiIntent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phiên bản v${state.currentVersionName} (${state.currentVersionCode})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = when (val s = state.status) {
                        UpdateStatus.Idle -> "Cập nhật tự động qua Cloudflare OTA"
                        UpdateStatus.Checking -> "Đang kiểm tra phiên bản mới..."
                        is UpdateStatus.UpToDate -> "✅ Đang ở phiên bản mới nhất"
                        is UpdateStatus.Available -> "🚀 Có bản mới v${s.release.versionName}: ${s.release.changelog}"
                        is UpdateStatus.Downloading -> "Đang tải gói APK... ${s.progressPercent}%"
                        is UpdateStatus.ReadyToInstall ->
                            if (s.needsInstallPermission) "Cấp quyền 'Cài đặt ứng dụng không xác định' rồi bấm Cài đặt"
                            else "Đã tải xong v${s.release.versionName} — sẵn sàng cài đặt"
                        UpdateStatus.AuthRequired -> "Đăng nhập Cloudflare để kiểm tra cập nhật tự động"
                        is UpdateStatus.Failed -> "Lỗi: ${s.reason}"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.status is UpdateStatus.Available) {
                            Color(0xFF34D399)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (state.status !is UpdateStatus.Available &&
                    state.status !is UpdateStatus.Downloading &&
                    state.status !is UpdateStatus.ReadyToInstall
                ) {
                    OutlinedButton(
                        onClick = { onIntent(UpdaterUiIntent.CheckForUpdate) },
                        enabled = state.status !is UpdateStatus.Checking
                    ) {
                        Text(if (state.status is UpdateStatus.Checking) "..." else "Kiểm tra")
                    }
                }
            }

            when (val s = state.status) {
                is UpdateStatus.Available -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { onIntent(UpdaterUiIntent.StartUpdate) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("⬇ Tải & Cập nhật ngay lên v${s.release.versionName}", fontWeight = FontWeight.Bold)
                    }
                }
                is UpdateStatus.Downloading -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { s.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is UpdateStatus.ReadyToInstall -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (s.needsInstallPermission) {
                                onIntent(UpdaterUiIntent.GrantInstallPermission)
                            } else {
                                onIntent(UpdaterUiIntent.InstallDownloadedApk)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (s.needsInstallPermission) "Mở Cài đặt cấp quyền Cài APK"
                            else "Cài đặt bản cập nhật v${s.release.versionName}"
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
