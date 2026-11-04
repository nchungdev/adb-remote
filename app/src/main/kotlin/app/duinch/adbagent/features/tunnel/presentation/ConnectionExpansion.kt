package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState

internal const val PRIMARY_CONNECTION_ID = SavedConnection.PRIMARY_ID
private const val NONE_EXPANDED = ""

/** The connection in use: one being set up, then the path serving ADB. */
internal fun activeConnectionId(
    connections: List<Pair<String, TunnelConnectionState>>
): String? = connections.firstOrNull { (_, state) ->
    state is TunnelConnectionState.Connecting || state is TunnelConnectionState.Reconnecting
}?.first
    ?: connections.firstOrNull { (_, state) -> state is TunnelConnectionState.Connected && !state.isStandby }?.first
    ?: connections.firstOrNull { (_, state) -> state is TunnelConnectionState.Connected }?.first

/** Nothing in use: the most recently added card stays open so it can be filled in. */
internal fun resolveExpandedConnection(userChoice: String?, activeId: String?, ids: List<String>): String? = when {
    userChoice == NONE_EXPANDED -> null
    userChoice != null && userChoice in ids -> userChoice
    activeId != null -> activeId
    else -> ids.lastOrNull()
}

/** Shows one connection card at a time. A tap overrides until the active connection changes. */
internal class ConnectionExpansionState {
    var userChoice by mutableStateOf<String?>(null)

    fun toggle(id: String, currentlyExpanded: String?) {
        userChoice = if (currentlyExpanded == id) NONE_EXPANDED else id
    }
}

@Composable
internal fun rememberConnectionExpansion(activeId: String?, ids: List<String>): ConnectionExpansionState {
    val expansion = remember { ConnectionExpansionState() }
    val knownCount = remember { mutableStateOf(ids.size) }
    LaunchedEffect(activeId) { expansion.userChoice = null }
    LaunchedEffect(ids.size) {
        if (ids.size > knownCount.value) expansion.userChoice = ids.lastOrNull()
        knownCount.value = ids.size
    }
    return expansion
}
