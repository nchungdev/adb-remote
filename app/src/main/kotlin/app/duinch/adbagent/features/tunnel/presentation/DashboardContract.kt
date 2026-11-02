package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.pairing.presentation.PairingUiState
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.termux.presentation.TermuxUiState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import app.duinch.remotetunnel.ui.ConnectMode

enum class AppTab(val title: String) {
    CONNECT("Kết nối"),
    HISTORY("Nhật ký"),
    SETTINGS("Cài đặt")
}

enum class AdbConnectionMode(val label: String) {
    USB("Cáp USB"),
    LAN("Mạng nội bộ"),
    REMOTE("Mạng từ xa")
}

data class ServerConnectionSlot(
    val id: String,
    val mode: ConnectMode = ConnectMode.Remote,
    val serverUrlInput: String = "",
    val remoteRoomCode: String = "",
    val connectionState: TunnelConnectionState = TunnelConnectionState.Disconnected
)

data class DashboardUiState(
    val selectedTab: AppTab = AppTab.CONNECT,
    val serverUrlInput: String = "",
    val deviceAliasInput: String = "",
    val localAdbPortInput: String = "5555",
    val autoReconnect: Boolean = true,
    val runInBackground: Boolean = true,
    val trustedHomeSsidInput: String = "",
    val autoSwitchOnHomeWifi: Boolean = true,
    val currentWifiSsid: String = "",
    val activeNetwork: NetworkType = NetworkType.UNKNOWN,
    val localWifiIp: String = "",
    val isUsbConnected: Boolean = false,
    val selectedMode: AdbConnectionMode = AdbConnectionMode.REMOTE,
    val forceShowPairing: Boolean = false,
    val connectionState: TunnelConnectionState = TunnelConnectionState.Disconnected,
    val history: List<TunnelHistoryEntry> = emptyList(),
    val showAllHistory: Boolean = false,
    val hostCommands: List<app.duinch.adbagent.features.tunnel.domain.HostCommandRecord> = emptyList(),
    val showAllHostCommands: Boolean = false,
    val pairing: PairingUiState = PairingUiState(),
    val termux: TermuxUiState = TermuxUiState(),
    val remoteRoomCode: String = "",
    val serverQuickTunnelUrl: String = "",
    val isServerRemoteActive: Boolean = false,
    val recentTunnelUrls: List<String> = emptyList(),
    val usbConnectedAtEpochMs: Long = 0L,
    val activeOnboardingStep: OnboardingStep? = null,
    val notifyConnectionChange: Boolean = true,
    val themeMode: String = "system",
    val extraSlots: List<ServerConnectionSlot> = emptyList()
) {
    val isTunnelActive: Boolean
        get() = connectionState is TunnelConnectionState.Connected ||
            connectionState is TunnelConnectionState.Connecting ||
            connectionState is TunnelConnectionState.Reconnecting ||
            extraSlots.any {
                it.connectionState is TunnelConnectionState.Connected ||
                    it.connectionState is TunnelConnectionState.Connecting ||
                    it.connectionState is TunnelConnectionState.Reconnecting
            }

    val isAtHomeWifi: Boolean
        get() = autoSwitchOnHomeWifi &&
            activeNetwork == NetworkType.WIFI &&
            trustedHomeSsidInput.isNotBlank() &&
            currentWifiSsid.equals(trustedHomeSsidInput.trim(), ignoreCase = true)
}

sealed interface DashboardUiIntent {
    data class SelectTab(val tab: AppTab) : DashboardUiIntent
    data class SelectConnectionMode(val mode: AdbConnectionMode) : DashboardUiIntent
    data class UpdateServerUrl(val value: String) : DashboardUiIntent
    data class UpdateRemoteRoomCode(val value: String) : DashboardUiIntent
    data class UpdateDeviceAlias(val value: String) : DashboardUiIntent
    data class UpdateLocalAdbPort(val value: String) : DashboardUiIntent
    data class ToggleAutoReconnect(val enabled: Boolean) : DashboardUiIntent
    data class ToggleRunInBackground(val enabled: Boolean) : DashboardUiIntent
    data class UpdateTrustedHomeSsid(val value: String) : DashboardUiIntent
    data class ToggleAutoSwitchHomeWifi(val enabled: Boolean) : DashboardUiIntent
    data object UseCurrentWifiAsHome : DashboardUiIntent
    data class ToggleNotifyConnectionChange(val enabled: Boolean) : DashboardUiIntent
    data class SelectThemeMode(val mode: String) : DashboardUiIntent
    data object SaveConfig : DashboardUiIntent
    data object ToggleTunnel : DashboardUiIntent
    data object ToggleHistoryExpand : DashboardUiIntent
    data object ClearHistory : DashboardUiIntent
    data object ToggleHostCommandsExpand : DashboardUiIntent
    data object ClearHostCommands : DashboardUiIntent
    data class SetForceShowPairing(val force: Boolean) : DashboardUiIntent
    data object ScanQrCode : DashboardUiIntent
    data class ApplyQrRemoteConfig(val roomCode: String, val serverUrl: String?) : DashboardUiIntent
    data class OnServerRemoteTunnelPushed(val url: String, val roomCode: String, val active: Boolean) : DashboardUiIntent
    data class OnPairingIntent(val intent: PairingUiIntent) : DashboardUiIntent
    data class OnTermuxIntent(val intent: TermuxUiIntent) : DashboardUiIntent
    data object NextOnboardingStep : DashboardUiIntent
    data object DismissOnboarding : DashboardUiIntent
    data object ShowOnboarding : DashboardUiIntent
    data class AddConnectionSlot(val mode: ConnectMode = ConnectMode.Remote) : DashboardUiIntent
    data class RemoveConnectionSlot(val slotId: String) : DashboardUiIntent
    data class ConnectExtraSlot(val slotId: String, val roomCode: String, val serverUrl: String) : DashboardUiIntent
    data class DisconnectExtraSlot(val slotId: String) : DashboardUiIntent
    data class ScanQrForSlot(val slotId: String) : DashboardUiIntent
}

sealed interface DashboardUiEffect {
    data class ShowMessage(val text: String) : DashboardUiEffect
    data object LaunchDeveloperSettings : DashboardUiEffect
    data object LaunchQrScanner : DashboardUiEffect
}
