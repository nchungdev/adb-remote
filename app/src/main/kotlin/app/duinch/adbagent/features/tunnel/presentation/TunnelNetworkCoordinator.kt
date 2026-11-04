package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.data.isLanTargetAddress
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode

/**
 * Checks whether the current device is connected to the same local Wi-Fi subnet as the LAN server.
 * Remote connections (e.g. Cloudflare Quick Tunnel) are independent of local Wi-Fi.
 */
internal fun isLocalWifiNetwork(activeNetwork: NetworkType, localWifiIp: String, serverUrl: String): Boolean {
    val mode = connectModeForServer(serverUrl)
    if (mode == ConnectMode.Remote) return true
    if (activeNetwork != NetworkType.WIFI && activeNetwork != NetworkType.ETHERNET) return false
    val serverHost = serverUrl.substringAfter("://").substringBefore(':').substringBefore('/').trim()
    if (!isLanTargetAddress(serverHost)) return true
    if (localWifiIp.isBlank()) return true
    return serverHost.substringBeforeLast('.') == localWifiIp.substringBeforeLast('.')
}

/**
 * Automatically activates the correct connection path when the network changes:
 * - On local Wi-Fi: prioritizes Wi-Fi/LAN connection.
 * - Outside (Cellular or foreign Wi-Fi): uses Remote connection, while Wi-Fi is marked unavailable.
 */
internal fun coordinateNetworkRouting(
    net: NetworkType,
    localWifiIp: String,
    state: DashboardUiState,
    onConnectSlot: (slotId: String, url: String) -> Unit,
    onConnectPrimary: () -> Unit
) {
    val primaryIsLan = state.primarySlotMode == ConnectMode.Lan
    val onLocalWifi = isLocalWifiNetwork(net, localWifiIp, state.serverUrlInput)

    if (net == NetworkType.CELLULAR || (!onLocalWifi && net != NetworkType.NONE)) {
        // Outside: switch to Remote connection
        if (state.hasPrimarySlot && !primaryIsLan && state.primaryAutoConnect) {
            if (state.connectionState !is TunnelConnectionState.Connected) onConnectPrimary()
        }
        state.extraSlots.forEach { slot ->
            if (slot.mode == ConnectMode.Remote && slot.autoConnect) {
                if (slot.connectionState !is TunnelConnectionState.Connected) {
                    onConnectSlot(slot.id, slot.serverUrlInput)
                }
            }
        }
    } else if (net == NetworkType.WIFI && onLocalWifi) {
        // Back on local Wi-Fi: restore LAN connection
        if (state.hasPrimarySlot && primaryIsLan && state.primaryAutoConnect) {
            if (state.connectionState !is TunnelConnectionState.Connected) onConnectPrimary()
        }
        state.extraSlots.forEach { slot ->
            if (slot.mode == ConnectMode.Lan && slot.autoConnect) {
                if (isLocalWifiNetwork(net, localWifiIp, slot.serverUrlInput)) {
                    if (slot.connectionState !is TunnelConnectionState.Connected) {
                        onConnectSlot(slot.id, slot.serverUrlInput)
                    }
                }
            }
        }
    }
}
