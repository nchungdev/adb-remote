package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal const val MAX_SERVER_NAME_LENGTH = 40

/** A blank name clears it, and the card goes back to showing the address. */
internal fun renameConnection(
    uiState: MutableStateFlow<DashboardUiState>,
    repository: TunnelConfigRepository,
    connectionId: String,
    rawName: String
) {
    val name = rawName.trim().take(MAX_SERVER_NAME_LENGTH).ifBlank { null }
    repository.getConnections().find { it.id == connectionId }?.let { repository.upsertConnection(it.copy(name = name)) }
    uiState.update { cur ->
        if (connectionId == PRIMARY_CONNECTION_ID) {
            cur.copy(primaryDisplayName = name)
        } else {
            cur.copy(extraSlots = cur.extraSlots.map { if (it.id == connectionId) it.copy(displayName = name) else it })
        }
    }
}

/** The name belongs to the card, so it stays when the address changes, as a Quick Tunnel URL does on every start. */
internal fun savePrimaryTarget(repository: TunnelConfigRepository, server: String, name: String?) {
    val existing = repository.getConnections().find { it.id == PRIMARY_CONNECTION_ID }
    repository.upsertConnection(
        existing?.copy(serverUrl = server, name = name) ?: SavedConnection(PRIMARY_CONNECTION_ID, server, name = name)
    )
}

/** Renaming a server updates the identity on all connections that belong to it. */
internal fun renameServer(
    uiState: MutableStateFlow<DashboardUiState>,
    repository: TunnelConfigRepository,
    serverId: String,
    rawName: String
) {
    val name = rawName.trim().take(MAX_SERVER_NAME_LENGTH).ifBlank { null } ?: return
    repository.getConnections().filter { it.server?.id == serverId }.forEach {
        val updated = it.server?.copy(name = name) ?: app.duinch.adbagent.features.tunnel.domain.ServerIdentity(serverId, name)
        repository.upsertConnection(it.copy(server = updated))
    }
    uiState.update { cur ->
        val updatedPrimary = if (cur.primaryServer?.id == serverId) cur.primaryServer.copy(name = name) else cur.primaryServer
        val updatedSlots = cur.extraSlots.map { slot ->
            if (slot.server?.id == serverId) slot.copy(server = slot.server.copy(name = name)) else slot
        }
        cur.copy(primaryServer = updatedPrimary, extraSlots = updatedSlots)
    }
}
