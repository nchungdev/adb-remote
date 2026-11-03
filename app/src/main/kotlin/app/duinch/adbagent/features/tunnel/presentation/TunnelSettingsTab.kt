package app.duinch.adbagent.features.tunnel.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.duinch.adbagent.R
import app.duinch.adbagent.core.designsystem.AdbTheme
import app.duinch.adbagent.core.designsystem.AppIcons
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdateBannerCard
import app.duinch.adbagent.features.updater.presentation.UpdaterUiIntent
import app.duinch.adbagent.features.updater.presentation.UpdaterUiState

@Composable
fun TunnelSettingsTab(
    state: DashboardUiState,
    updaterState: UpdaterUiState,
    onIntent: (DashboardUiIntent) -> Unit,
    onUpdaterIntent: (UpdaterUiIntent) -> Unit
) {
    val ext = AdbTheme.colors
    val dim = AdbTheme.dimens
    var showP2PSheet by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }

    if (showP2PSheet) {
        P2PConfigSheet(
            state = state,
            onIntent = onIntent,
            onDismiss = { showP2PSheet = false }
        )
    }

    if (showThemeSheet) {
        ThemeSelectorSheet(
            currentTheme = state.themeMode,
            onSelect = { onIntent(DashboardUiIntent.SelectThemeMode(it)) },
            onDismiss = { showThemeSheet = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(dim.spacingXl)) {
        // Group 1: Đường truyền & Mạng
        Column(verticalArrangement = Arrangement.spacedBy(dim.spacingS)) {
            Text(
                text = stringResource(R.string.settings_group_network),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = dim.spacingXs)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ext.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsNavigationRow(
                        icon = AppIcons.CloudLock,
                        title = stringResource(R.string.settings_p2p_title),
                        subtitle = "${state.serverUrlInput.ifBlank { stringResource(R.string.settings_p2p_unset) }} • Cổng ${state.localAdbPortInput}",
                        onClick = { showP2PSheet = true }
                    )

                    HorizontalDivider(color = ext.cardBorder, thickness = 0.5.dp)

                    SettingsNavigationRow(
                        icon = AppIcons.LinkWireless,
                        title = stringResource(R.string.settings_wireless_pairing_title),
                        subtitle = stringResource(R.string.settings_wireless_pairing_sub),
                        onClick = { onIntent(DashboardUiIntent.SetForceShowPairing(true)) }
                    )
                }
            }
        }

        // Group 2: Dịch vụ & Hệ thống
        Column(verticalArrangement = Arrangement.spacedBy(dim.spacingS)) {
            Text(
                text = stringResource(R.string.settings_group_system),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = dim.spacingXs)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ext.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = AppIcons.ShieldBolt,
                        title = stringResource(R.string.settings_run_in_background_title),
                        subtitle = stringResource(R.string.settings_run_in_background_sub),
                        checked = state.runInBackground,
                        onCheckedChange = { onIntent(DashboardUiIntent.ToggleRunInBackground(it)) }
                    )

                    HorizontalDivider(color = ext.cardBorder, thickness = 0.5.dp)

                    SettingsNavigationRow(
                        icon = AppIcons.Terminal,
                        title = stringResource(R.string.settings_termux_title),
                        subtitle = stringResource(R.string.settings_termux_sub),
                        onClick = { onIntent(DashboardUiIntent.OnTermuxIntent(TermuxUiIntent.OpenConsole)) }
                    )
                }
            }
        }

        // Group 3: Giao diện & Giao tiếp
        Column(verticalArrangement = Arrangement.spacedBy(dim.spacingS)) {
            Text(
                text = stringResource(R.string.settings_group_appearance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = dim.spacingXs)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, ext.cardBorder),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = AppIcons.Activity,
                        title = stringResource(R.string.settings_notify_connection_change_title),
                        subtitle = stringResource(R.string.settings_notify_connection_change_sub),
                        checked = state.notifyConnectionChange,
                        onCheckedChange = { onIntent(DashboardUiIntent.ToggleNotifyConnectionChange(it)) }
                    )

                    HorizontalDivider(color = ext.cardBorder, thickness = 0.5.dp)

                    val themeLabel = when (state.themeMode) {
                        "light" -> stringResource(R.string.settings_theme_light)
                        "dark" -> stringResource(R.string.settings_theme_dark)
                        else -> stringResource(R.string.settings_theme_system)
                    }
                    SettingsNavigationRow(
                        icon = AppIcons.Sliders,
                        title = stringResource(R.string.settings_theme_mode_title),
                        subtitle = themeLabel,
                        onClick = { showThemeSheet = true }
                    )
                }
            }
        }

        // Group 4: Thông tin ứng dụng & Cập nhật
        Column(verticalArrangement = Arrangement.spacedBy(dim.spacingS)) {
            Text(
                text = stringResource(R.string.settings_group_info),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = dim.spacingXs)
            )

            UpdateBannerCard(state = updaterState, onIntent = onUpdaterIntent)
        }
    }
}
