package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Mirrors the session's byte counter into the Connected state while the session is current. */
internal fun CoroutineScope.publishBridgedBytes(
    bytes: AtomicLong,
    state: MutableStateFlow<TunnelConnectionState>,
    isCurrentSession: () -> Boolean
) {
    launch {
        while (isCurrentSession()) {
            delay(1_000L)
            val current = state.value as? TunnelConnectionState.Connected ?: continue
            if (isCurrentSession()) state.value = current.copy(bytesBridged = bytes.get())
        }
    }
}
