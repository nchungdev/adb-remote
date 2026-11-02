package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal fun handleDashboardSettingsIntent(
    intent: DashboardUiIntent,
    uiState: MutableStateFlow<DashboardUiState>,
    container: AppContainer,
    onSave: (Boolean) -> Unit,
    onMessage: (String) -> Unit
): Boolean {
    when (intent) {
        is DashboardUiIntent.SelectTab -> uiState.update { it.copy(selectedTab = intent.tab) }
        is DashboardUiIntent.SelectConnectionMode -> uiState.update { it.copy(selectedMode = intent.mode) }
        is DashboardUiIntent.UpdateServerUrl -> uiState.update { it.copy(serverUrlInput = intent.value) }
        is DashboardUiIntent.UpdateRemoteRoomCode -> uiState.update { it.copy(remoteRoomCode = intent.value.trim()) }
        is DashboardUiIntent.UpdateDeviceAlias -> uiState.update { it.copy(deviceAliasInput = intent.value) }
        is DashboardUiIntent.UpdateLocalAdbPort -> uiState.update { it.copy(localAdbPortInput = intent.value.filter(Char::isDigit).take(5)) }
        is DashboardUiIntent.ToggleAutoReconnect -> uiState.update { it.copy(autoReconnect = intent.enabled) }
        is DashboardUiIntent.ToggleRunInBackground -> {
            uiState.update { it.copy(runInBackground = intent.enabled) }
            onSave(true)
            val msg = if (intent.enabled) {
                app.duinch.adbagent.features.tunnel.data.TunnelForegroundService.start(container.appContext)
                "Đã bật dịch vụ chạy ngầm trên thanh thông báo"
            } else {
                if (!uiState.value.isTunnelActive) app.duinch.adbagent.features.tunnel.data.TunnelForegroundService.stop(container.appContext)
                "Đã tắt dịch vụ chạy ngầm"
            }
            onMessage(msg)
        }
        is DashboardUiIntent.UpdateTrustedHomeSsid -> uiState.update { it.copy(trustedHomeSsidInput = intent.value) }
        is DashboardUiIntent.ToggleAutoSwitchHomeWifi -> uiState.update { it.copy(autoSwitchOnHomeWifi = intent.enabled) }
        is DashboardUiIntent.ToggleNotifyConnectionChange -> {
            uiState.update { it.copy(notifyConnectionChange = intent.enabled) }
            onSave(true)
        }
        is DashboardUiIntent.SelectThemeMode -> {
            uiState.update { it.copy(themeMode = intent.mode) }
            onSave(true)
        }
        DashboardUiIntent.UseCurrentWifiAsHome -> {
            val currentSsid = container.reverseTunnelGateway.getCurrentWifiSsid().orEmpty()
            if (currentSsid.isNotBlank()) {
                uiState.update { it.copy(trustedHomeSsidInput = currentSsid) }
                onSave(true)
                onMessage("Đã đặt Wi-Fi '$currentSsid' làm Wi-Fi nhà")
            } else {
                onMessage("Chưa phát hiện tên Wi-Fi hiện tại")
            }
        }
        else -> return false
    }
    return true
}
