package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLDecoder
import java.net.URLEncoder

class SharedPrefsTunnelHistoryRepository(
    context: Context
) : TunnelHistoryRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val historyFlow = MutableStateFlow(loadInitialHistory())
    private var lastDiskFlushEpochMs = 0L

    override fun observeHistory(): Flow<List<TunnelHistoryEntry>> = historyFlow.asStateFlow()

    override fun recordSessionStarted(entry: TunnelHistoryEntry) {
        synchronized(lock) {
            val closedPrevious = historyFlow.value.map { existing ->
                if (existing.isActive) {
                    existing.copy(
                        endedAtEpochMs = entry.startedAtEpochMs,
                        endReason = "Phiên mới thay thế"
                    )
                } else {
                    existing
                }
            }
            val updated = (listOf(entry) + closedPrevious).take(MAX_ENTRIES)
            historyFlow.value = updated
            persistLocked(updated)
        }
    }

    override fun updateActiveSession(
        sessionId: String,
        mappedServerPort: Int?,
        bytesBridged: Long,
        networkType: NetworkType
    ) {
        synchronized(lock) {
            var portChanged = false
            val updated = historyFlow.value.map { existing ->
                if (existing.sessionId == sessionId && existing.isActive) {
                    if (mappedServerPort != null && existing.mappedServerPort != mappedServerPort) {
                        portChanged = true
                    }
                    existing.copy(
                        mappedServerPort = mappedServerPort ?: existing.mappedServerPort,
                        bytesBridged = bytesBridged,
                        networkType = networkType
                    )
                } else {
                    existing
                }
            }
            historyFlow.value = updated
            val now = System.currentTimeMillis()
            if (portChanged || (now - lastDiskFlushEpochMs) >= 15_000L) {
                persistLocked(updated)
            }
        }
    }

    override fun recordSessionEnded(
        sessionId: String,
        endedAtEpochMs: Long,
        bytesBridged: Long,
        reason: String
    ) {
        synchronized(lock) {
            val updated = historyFlow.value.map { existing ->
                if (existing.sessionId == sessionId && existing.isActive) {
                    existing.copy(
                        endedAtEpochMs = endedAtEpochMs.coerceAtLeast(existing.startedAtEpochMs),
                        bytesBridged = maxOf(existing.bytesBridged, bytesBridged),
                        endReason = reason
                    )
                } else {
                    existing
                }
            }
            historyFlow.value = updated
            persistLocked(updated)
        }
    }

    override fun clearHistory() {
        synchronized(lock) {
            val activeOnly = historyFlow.value.filter { it.isActive }
            historyFlow.value = activeOnly
            persistLocked(activeOnly)
        }
    }

    private fun loadInitialHistory(): List<TunnelHistoryEntry> {
        val raw = prefs.getString(KEY_HISTORY_LINES, "").orEmpty()
        val now = System.currentTimeMillis()
        return decodeEntries(raw).map { entry ->
            if (entry.isActive) {
                entry.copy(
                    endedAtEpochMs = now.coerceAtLeast(entry.startedAtEpochMs),
                    endReason = "Ứng dụng khởi động lại"
                )
            } else {
                entry
            }
        }
    }

    private fun persistLocked(entries: List<TunnelHistoryEntry>) {
        lastDiskFlushEpochMs = System.currentTimeMillis()
        prefs.edit().putString(KEY_HISTORY_LINES, encodeEntries(entries)).apply()
    }

    companion object {
        private const val PREFS_NAME = "adb_agent_tunnel_history"
        private const val KEY_HISTORY_LINES = "history_v1"
        const val MAX_ENTRIES = 30

        internal fun encodeEntries(entries: List<TunnelHistoryEntry>): String =
            entries.joinToString("\n") { e ->
                val encReason = URLEncoder.encode(e.endReason, "UTF-8")
                listOf(
                    e.sessionId,
                    e.startedAtEpochMs.toString(),
                    e.endedAtEpochMs?.toString().orEmpty(),
                    e.networkType.name,
                    e.localAdbPort.toString(),
                    e.mappedServerPort?.toString().orEmpty(),
                    e.bytesBridged.toString(),
                    encReason
                ).joinToString("|")
            }

        internal fun decodeEntries(serialized: String): List<TunnelHistoryEntry> {
            if (serialized.isBlank()) return emptyList()
            return serialized.lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    runCatching {
                        val parts = line.split("|")
                        if (parts.size < 8) return@runCatching null
                        TunnelHistoryEntry(
                            sessionId = parts[0],
                            startedAtEpochMs = parts[1].toLong(),
                            endedAtEpochMs = parts[2].takeIf { it.isNotEmpty() }?.toLong(),
                            networkType = runCatching { NetworkType.valueOf(parts[3]) }
                                .getOrDefault(NetworkType.UNKNOWN),
                            localAdbPort = parts[4].toInt(),
                            mappedServerPort = parts[5].takeIf { it.isNotEmpty() }?.toInt(),
                            bytesBridged = parts[6].toLong(),
                            endReason = URLDecoder.decode(parts[7], "UTF-8")
                        )
                    }.getOrNull()
                }
                .take(MAX_ENTRIES)
                .toList()
        }
    }
}
