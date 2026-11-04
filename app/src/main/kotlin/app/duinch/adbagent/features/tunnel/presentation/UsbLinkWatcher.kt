package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.ServerApprovalGateway
import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val USB_LINK_CHECK_INTERVAL_MS = 5_000L

internal data class UsbLinkResult(val linkedServerId: String?, val checked: Boolean)

/** Answers by server ID. A null answer is a server that could not tell. */
internal fun resolveUsbLink(answers: List<Pair<String, Boolean?>>) = UsbLinkResult(
    linkedServerId = answers.firstOrNull { it.second == true }?.first,
    checked = answers.any { it.second != null }
)

/** Each connected server, reached through any one of its live connections. */
internal fun usbLinkTargets(state: DashboardUiState): Map<ServerIdentity, String> = buildList {
    val primary = state.connectionState as? TunnelConnectionState.Connected
    primary?.server?.let { add(it to state.serverUrlInput) }
    state.extraSlots.forEach { slot ->
        (slot.connectionState as? TunnelConnectionState.Connected)?.server?.let { add(it to slot.serverUrlInput) }
    }
}.distinctBy { it.first.id }.toMap()

/** Only a server can tell where the cable goes, so ask each one while a cable is plugged in. */
internal fun CoroutineScope.launchUsbLinkChecks(
    gateway: ServerApprovalGateway,
    uiState: MutableStateFlow<DashboardUiState>
) = launch {
    while (isActive) {
        val state = uiState.value
        val result = if (state.isUsbConnected) {
            resolveUsbLink(
                usbLinkTargets(state).map { (server, url) ->
                    server.id to gateway.isUsbLinked(url, state.deviceAliasInput)
                }
            )
        } else {
            UsbLinkResult(linkedServerId = null, checked = false)
        }
        uiState.update { it.copy(usbLinkedServerId = result.linkedServerId, usbLinkChecked = result.checked) }
        delay(USB_LINK_CHECK_INTERVAL_MS)
    }
}
