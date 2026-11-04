package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import app.duinch.remotetunnel.ui.ConnectMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Cards sharing one server; only they compete for "in use". A null server has not answered yet. */
internal data class ServerGroup(val server: ServerIdentity?, val connectionIds: List<String>)

/**
 * Groups keep the order their first card appears in. A card no server has answered yet goes
 * last, on its own: nothing says two such cards lead to one machine.
 */
internal fun groupByServer(connections: List<Pair<String, ServerIdentity?>>): List<ServerGroup> {
    val (known, unknown) = connections.partition { it.second != null }
    val groups = known.groupBy { it.second?.id }.values.map { cards ->
        ServerGroup(cards.first().second, cards.map { it.first })
    }
    return groups + unknown.map { ServerGroup(null, listOf(it.first)) }
}

/** True when every configured connection belongs to the same identified server. */
internal fun allConnectionsBelongToOneServer(connections: List<Pair<String, ServerIdentity?>>): Boolean {
    if (connections.isEmpty()) return false
    val firstServer = connections.first().second ?: return false
    return connections.all { it.second != null && it.second?.id == firstServer.id }
}

internal data class GroupedConnection(
    val id: String,
    val mode: ConnectMode,
    val state: TunnelConnectionState,
    val serverUrl: String
)

/** The connection carrying traffic, or any live one in the moment before the server assigns roles. */
internal fun liveConnection(connections: List<GroupedConnection>): GroupedConnection? =
    connections.firstOrNull { (it.state as? TunnelConnectionState.Connected)?.isStandby == false }
        ?: connections.firstOrNull { it.state is TunnelConnectionState.Connected }

/** One line about the server as a whole, so its cards need not be opened to know the state. */
internal fun serverGroupSummary(connections: List<GroupedConnection>): String {
    val active = connections.firstOrNull { (it.state as? TunnelConnectionState.Connected)?.isStandby == false }
    val status = when {
        active != null -> if (active.mode == ConnectMode.Remote) "Đang dùng từ xa" else "Đang dùng Wi-Fi"
        connections.any { it.state.isInUse() } -> "Đang kết nối..."
        else -> "Chưa kết nối"
    }
    return if (connections.size > 1) "${connections.size} kết nối • $status" else status
}

/** Puts [group] in its new order within the slots it already holds, so other servers stay where they are. */
internal fun mergeGroupOrder(current: List<String>, group: List<String>): List<String> {
    val reordered = group.iterator()
    val merged = current.map { id -> if (id in group && reordered.hasNext()) reordered.next() else id }
    return merged + reordered.asSequence().toList()
}

internal fun TunnelConfigRepository.rememberServer(connectionId: String, server: ServerIdentity) {
    val saved = getConnections().find { it.id == connectionId } ?: return
    if (saved.server != server) upsertConnection(saved.copy(server = server))
}

internal fun applyPrimaryConnectionState(
    uiState: MutableStateFlow<DashboardUiState>,
    repository: TunnelConfigRepository,
    connection: TunnelConnectionState
) {
    val server = (connection as? TunnelConnectionState.Connected)?.server
    if (server != null) repository.rememberServer(PRIMARY_CONNECTION_ID, server)
    uiState.update { it.copy(connectionState = connection, primaryServer = server ?: it.primaryServer) }
}
