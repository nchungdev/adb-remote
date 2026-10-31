package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.pairing.presentation.PairingUiIntent
import app.duinch.adbagent.features.pairing.presentation.PairingUiState
import app.duinch.adbagent.features.termux.presentation.TermuxUiIntent
import app.duinch.adbagent.features.termux.presentation.TermuxUiState
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState

data class DashboardUiState(
    val serverUrlInput: String = "",
    val deviceAliasInput: String = "",
    val localAdbPortInput: String = "5555",
    val cfClientIdInput: String = "",
    val cfClientSecretInput: String = "",
    val autoReconnect: Boolean = true,
    val showAdvancedAuth: Boolean = false,
    val activeNetwork: NetworkType = NetworkType.UNKNOWN,
    val connectionState: TunnelConnectionState = TunnelConnectionState.Disconnected,
    val pairing: PairingUiState = PairingUiState(),
    val termux: TermuxUiState = TermuxUiState()
) {
    val isTunnelActive: Boolean
        get() = connectionState is TunnelConnectionState.Connected ||
            connectionState is TunnelConnectionState.Connecting ||
            connectionState is TunnelConnectionState.Reconnecting
}

sealed interface DashboardUiIntent {
    data class UpdateServerUrl(val value: String) : DashboardUiIntent
    data class UpdateDeviceAlias(val value: String) : DashboardUiIntent
    data class UpdateLocalAdbPort(val value: String) : DashboardUiIntent
    data class UpdateCfClientId(val value: String) : DashboardUiIntent
    data class UpdateCfClientSecret(val value: String) : DashboardUiIntent
    data class ToggleAutoReconnect(val enabled: Boolean) : DashboardUiIntent
    data object ToggleAdvancedAuth : DashboardUiIntent
    data object SaveConfig : DashboardUiIntent
    data object ToggleTunnel : DashboardUiIntent
    data class OnPairingIntent(val intent: PairingUiIntent) : DashboardUiIntent
    data class OnTermuxIntent(val intent: TermuxUiIntent) : DashboardUiIntent
}

sealed interface DashboardUiEffect {
    data class ShowMessage(val text: String) : DashboardUiEffect
    data object LaunchDeveloperSettings : DashboardUiEffect
}
