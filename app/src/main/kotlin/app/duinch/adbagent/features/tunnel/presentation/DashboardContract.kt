package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.pairing.presentation.PairingUiState
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.termux.presentation.TermuxUiState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry

enum class AppTab(val title: String) {
    CONNECT("Kết nối"),
    HISTORY("Lịch sử"),
    PAIRING("Ghép đôi"),
    SETTINGS("Thiết lập")
}

data class DashboardUiState(
    val selectedTab: AppTab = AppTab.CONNECT,
    val serverUrlInput: String = "",
    val deviceAliasInput: String = "",
    val localAdbPortInput: String = "5555",
    val cfClientIdInput: String = "",
    val cfClientSecretInput: String = "",
    val cfAuthCookie: String = "",
    val autoReconnect: Boolean = true,
    val runInBackground: Boolean = true,
    val trustedHomeSsidInput: String = "",
    val autoSwitchOnHomeWifi: Boolean = true,
    val currentWifiSsid: String = "",
    val showAdvancedAuth: Boolean = false,
    val showCloudflareLoginDialog: Boolean = false,
    val activeNetwork: NetworkType = NetworkType.UNKNOWN,
    val localWifiIp: String = "",
    val isUsbConnected: Boolean = false,
    val connectionState: TunnelConnectionState = TunnelConnectionState.Disconnected,
    val history: List<TunnelHistoryEntry> = emptyList(),
    val showAllHistory: Boolean = false,
    val hostCommands: List<app.duinch.adbagent.features.tunnel.domain.HostCommandRecord> = emptyList(),
    val showAllHostCommands: Boolean = false,
    val pairing: PairingUiState = PairingUiState(),
    val termux: TermuxUiState = TermuxUiState()
) {
    val isTunnelActive: Boolean
        get() = connectionState is TunnelConnectionState.Connected ||
            connectionState is TunnelConnectionState.Connecting ||
            connectionState is TunnelConnectionState.Reconnecting

    val hasCloudflareAuth: Boolean
        get() = cfAuthCookie.contains("CF_Authorization=") ||
            (cfClientIdInput.isNotBlank() && cfClientSecretInput.isNotBlank())

    val isAtHomeWifi: Boolean
        get() = autoSwitchOnHomeWifi &&
            activeNetwork == NetworkType.WIFI &&
            trustedHomeSsidInput.isNotBlank() &&
            currentWifiSsid.equals(trustedHomeSsidInput.trim(), ignoreCase = true)
}

sealed interface DashboardUiIntent {
    data class SelectTab(val tab: AppTab) : DashboardUiIntent
    data class UpdateServerUrl(val value: String) : DashboardUiIntent
    data class UpdateDeviceAlias(val value: String) : DashboardUiIntent
    data class UpdateLocalAdbPort(val value: String) : DashboardUiIntent
    data class UpdateCfClientId(val value: String) : DashboardUiIntent
    data class UpdateCfClientSecret(val value: String) : DashboardUiIntent
    data class ToggleAutoReconnect(val enabled: Boolean) : DashboardUiIntent
    data class ToggleRunInBackground(val enabled: Boolean) : DashboardUiIntent
    data class UpdateTrustedHomeSsid(val value: String) : DashboardUiIntent
    data class ToggleAutoSwitchHomeWifi(val enabled: Boolean) : DashboardUiIntent
    data object UseCurrentWifiAsHome : DashboardUiIntent
    data object ToggleAdvancedAuth : DashboardUiIntent
    data object OpenCloudflareLogin : DashboardUiIntent
    data object CloseCloudflareLogin : DashboardUiIntent
    data class OnCloudflareCookieCaptured(val cookieHeader: String) : DashboardUiIntent
    data object SaveConfig : DashboardUiIntent
    data object ToggleTunnel : DashboardUiIntent
    data object ToggleHistoryExpand : DashboardUiIntent
    data object ClearHistory : DashboardUiIntent
    data object ToggleHostCommandsExpand : DashboardUiIntent
    data object ClearHostCommands : DashboardUiIntent
    data class OnPairingIntent(val intent: PairingUiIntent) : DashboardUiIntent
    data class OnTermuxIntent(val intent: TermuxUiIntent) : DashboardUiIntent
}

sealed interface DashboardUiEffect {
    data class ShowMessage(val text: String) : DashboardUiEffect
    data object LaunchDeveloperSettings : DashboardUiEffect
    data object OpenAccessibilitySettings : DashboardUiEffect
}
