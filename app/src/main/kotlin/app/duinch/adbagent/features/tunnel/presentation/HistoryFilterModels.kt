package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.HostCommandRecord
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class HistoryTabFilter(val label: String) {
    ALL("Tất cả"),
    HOST_COMMANDS("Lệnh máy tính"),
    SESSIONS("Phiên kết nối"),
    TERMUX("Lệnh nội bộ")
}

internal sealed interface HistoryFeedItem {
    val timestampEpochMs: Long

    data class Command(val record: HostCommandRecord) : HistoryFeedItem {
        override val timestampEpochMs: Long get() = record.timestampEpochMs
    }

    data class Session(val entry: TunnelHistoryEntry) : HistoryFeedItem {
        override val timestampEpochMs: Long get() = entry.startedAtEpochMs
    }
}

internal fun buildHistoryFeed(
    filter: HistoryTabFilter,
    searchQuery: String,
    commands: List<HostCommandRecord>,
    sessions: List<TunnelHistoryEntry>
): List<HistoryFeedItem> {
    val query = searchQuery.trim().lowercase()

    val hostCmds = commands.filter { it.source != "Embedded Termux" }
    val termuxCmds = commands.filter { it.source == "Embedded Termux" }

    val filteredCommands = when (filter) {
        HistoryTabFilter.ALL -> commands
        HistoryTabFilter.HOST_COMMANDS -> hostCmds
        HistoryTabFilter.TERMUX -> termuxCmds
        HistoryTabFilter.SESSIONS -> emptyList()
    }.filter {
        query.isEmpty() ||
            it.command.lowercase().contains(query) ||
            (it.outputSnippet?.lowercase()?.contains(query) == true) ||
            it.source.lowercase().contains(query)
    }.map { HistoryFeedItem.Command(it) }

    val filteredSessions = when (filter) {
        HistoryTabFilter.ALL, HistoryTabFilter.SESSIONS -> sessions
        HistoryTabFilter.HOST_COMMANDS, HistoryTabFilter.TERMUX -> emptyList()
    }.filter {
        query.isEmpty() ||
            it.networkType.label.lowercase().contains(query) ||
            it.localAdbPort.toString().contains(query) ||
            (it.mappedServerPort?.toString()?.contains(query) == true) ||
            it.endReason.lowercase().contains(query)
    }.map { HistoryFeedItem.Session(it) }

    return (filteredCommands + filteredSessions).sortedByDescending { it.timestampEpochMs }
}

internal fun formatTimestampVi(epochMs: Long): String =
    SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(epochMs))

internal fun formatCommandTimeVi(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))

internal fun formatDurationVi(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
