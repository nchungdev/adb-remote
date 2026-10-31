package app.duinch.adbagent.features.updater.presentation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.updater.domain.UpdateStatus

@Composable
fun CompactUpdateAlertBanner(
    state: UpdaterUiState,
    onIntent: (UpdaterUiIntent) -> Unit
) {
    val active = state.status is UpdateStatus.Available ||
        state.status is UpdateStatus.Downloading ||
        state.status is UpdateStatus.ReadyToInstall
    if (!active) return

    UpdateBannerCard(state = state, onIntent = onIntent)
}

@Composable
fun UpdateBannerCard(
    state: UpdaterUiState,
    onIntent: (UpdaterUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    val isHighlight = state.status is UpdateStatus.Available || state.status is UpdateStatus.ReadyToInstall
    val borderColor = if (isHighlight) ext.statusConnected else ext.cardBorder

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (state.status is UpdateStatus.UpToDate) {
                        AppIcons.CheckCircle
                    } else {
                        AppIcons.Download
                    },
                    contentDescription = null,
                    tint = if (isHighlight || state.status is UpdateStatus.UpToDate) {
                        ext.statusConnected
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADB Agent",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "v${state.currentVersionName} (Build ${state.currentVersionCode})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val subtitle = when (val s = state.status) {
                        UpdateStatus.Idle -> "Cập nhật trực tiếp qua Cloudflare OTA"
                        UpdateStatus.Checking -> "Đang kiểm tra bản phát hành mới..."
                        is UpdateStatus.UpToDate -> "Đang ở phiên bản mới nhất"
                        is UpdateStatus.Available -> "Có bản mới v${s.release.versionName}: ${s.release.changelog}"
                        is UpdateStatus.Downloading -> "Đang tải gói cài đặt... ${s.progressPercent}%"
                        is UpdateStatus.ReadyToInstall ->
                            if (s.needsInstallPermission) "Cần cấp quyền cài đặt APK để hoàn tất"
                            else "Đã tải xong v${s.release.versionName} — sẵn sàng cài đặt"
                        UpdateStatus.AuthRequired -> "Đăng nhập Cloudflare trước khi kiểm tra OTA"
                        is UpdateStatus.Failed -> s.reason
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.status is UpdateStatus.Available) {
                            ext.statusConnected
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (state.status !is UpdateStatus.Available &&
                state.status !is UpdateStatus.Downloading &&
                state.status !is UpdateStatus.ReadyToInstall
            ) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { onIntent(UpdaterUiIntent.CheckForUpdate) },
                    enabled = state.status !is UpdateStatus.Checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.status is UpdateStatus.Checking) "Đang kiểm tra..." else "Kiểm tra bản cập nhật mới")
                }
            }

            when (val s = state.status) {
                is UpdateStatus.Available -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onIntent(UpdaterUiIntent.StartUpdate) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tải & Cập nhật lên v${s.release.versionName}")
                    }
                }
                is UpdateStatus.Downloading -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { s.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
                is UpdateStatus.ReadyToInstall -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (s.needsInstallPermission) {
                                onIntent(UpdaterUiIntent.GrantInstallPermission)
                            } else {
                                onIntent(UpdaterUiIntent.InstallDownloadedApk)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            if (s.needsInstallPermission) "Mở Cài đặt cấp quyền Cài APK"
                            else "Cài đặt ngay bản v${s.release.versionName}"
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
