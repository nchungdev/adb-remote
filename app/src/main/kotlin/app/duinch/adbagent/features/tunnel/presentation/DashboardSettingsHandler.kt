package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.core.di.AppContainer
import app.duinch.remotetunnel.ui.ConnectMode
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
        is DashboardUiIntent.UpdateServerUrl -> uiState.update { it.copy(serverUrlInput = intent.value) }
        is DashboardUiIntent.RenameConnection -> renameConnection(uiState, container.tunnelConfigRepository, intent.connectionId, intent.name)
        is DashboardUiIntent.RenameServer -> renameServer(uiState, container.tunnelConfigRepository, intent.serverId, intent.name)
        is DashboardUiIntent.UpdateDeviceAlias -> uiState.update { it.copy(deviceAliasInput = intent.value) }
        is DashboardUiIntent.UpdateLocalAdbPort -> uiState.update { it.copy(localAdbPortInput = intent.value.filter(Char::isDigit).take(5)) }
        is DashboardUiIntent.ToggleAutoConnect -> {
            val saved = container.tunnelConfigRepository.getConnections().find { it.id == intent.connectionId }
            if (saved != null) {
                container.tunnelConfigRepository.upsertConnection(saved.copy(wantsConnected = intent.enabled))
            }
            uiState.update { cur ->
                if (intent.connectionId == PRIMARY_CONNECTION_ID) {
                    cur.copy(primaryAutoConnect = intent.enabled)
                } else {
                    cur.copy(extraSlots = cur.extraSlots.map { if (it.id == intent.connectionId) it.copy(autoConnect = intent.enabled) else it })
                }
            }
            onMessage(if (intent.enabled) "Đã bật tự động kết nối" else "Đã tắt tự động kết nối")
        }
        is DashboardUiIntent.ToggleServerAutoConnect -> {
            intent.connectionIds.forEach { id ->
                val saved = container.tunnelConfigRepository.getConnections().find { it.id == id }
                if (saved != null) {
                    container.tunnelConfigRepository.upsertConnection(saved.copy(wantsConnected = intent.enabled))
                }
                if (id == PRIMARY_CONNECTION_ID) {
                    container.tunnelConfigRepository.setWantsConnected(intent.enabled)
                }
            }
            uiState.update { cur ->
                val ids = intent.connectionIds.toSet()
                cur.copy(
                    primaryAutoConnect = if (PRIMARY_CONNECTION_ID in ids) intent.enabled else cur.primaryAutoConnect,
                    extraSlots = cur.extraSlots.map { slot ->
                        if (slot.id in ids) slot.copy(autoConnect = intent.enabled) else slot
                    }
                )
            }
            onMessage(if (intent.enabled) "Đã bật tự động kết nối máy chủ" else "Đã tắt tự động kết nối máy chủ")
        }
        is DashboardUiIntent.ForceActiveConnection -> {
            applyForceActiveConnection(intent.connectionId, uiState, container.deviceSecret(), onMessage)
        }
        is DashboardUiIntent.ReorderConnections -> {
            val repository = container.tunnelConfigRepository
            val current = uiState.value.connectionOrder.ifEmpty { repository.getConnections().map { it.id } }
            val order = mergeGroupOrder(current, intent.connectionIds)
            val previousTop = current.firstOrNull { it in intent.connectionIds }
            uiState.update { it.copy(connectionOrder = order) }
            repository.reorderConnections(order)
            intent.connectionIds.firstOrNull()?.takeIf { it != previousTop }?.let { topId ->
                applyForceActiveConnection(topId, uiState, container.deviceSecret(), onMessage)
            }
        }
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
        is DashboardUiIntent.ToggleNotifyConnectionChange -> {
            uiState.update { it.copy(notifyConnectionChange = intent.enabled) }
            onSave(true)
        }
        is DashboardUiIntent.SelectThemeMode -> {
            uiState.update { it.copy(themeMode = intent.mode) }
            onSave(true)
        }
        else -> return false
    }
    return true
}

private fun applyForceActiveConnection(
    connectionId: String,
    uiState: MutableStateFlow<DashboardUiState>,
    deviceSecret: String,
    onMessage: (String) -> Unit
) {
    val state = uiState.value
    val (mode, targetUrl) = if (connectionId == PRIMARY_CONNECTION_ID) {
        state.primarySlotMode to state.serverUrlInput
    } else {
        val slot = state.extraSlots.find { it.id == connectionId }
        (slot?.mode ?: ConnectMode.Remote) to (slot?.serverUrlInput.orEmpty())
    }
    val policy = if (mode == ConnectMode.Lan) "lan" else "remote"
    if (targetUrl.isNotBlank()) {
        Thread {
            runCatching {
                val cleanUrl = targetUrl.trimEnd('/')
                val endpoint = if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                    "$cleanUrl/api/tunnel/policy?policy=$policy"
                } else {
                    "http://$cleanUrl/api/tunnel/policy?policy=$policy"
                }
                val conn = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $deviceSecret")
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                val accepted = conn.responseCode in 200..299
                conn.disconnect()
                accepted
            }.getOrDefault(false).let { accepted ->
                // An older server answers 404: the card order moved, but the server still decides.
                onMessage(
                    if (accepted) {
                        "Đã ưu tiên kết nối ${if (mode == ConnectMode.Lan) "Wi-Fi" else "Từ xa"}"
                    } else {
                        "Máy chủ chưa áp dụng được thứ tự ưu tiên này"
                    }
                )
            }
        }.start()
    }
}
