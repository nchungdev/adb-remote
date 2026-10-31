package app.duinch.adbagent.features.tunnel.domain

data class HostCommandRecord(
    val id: String,
    val command: String,
    val source: String,
    val status: String,
    val timestampEpochMs: Long,
    val durationMs: Long = 0L,
    val outputSnippet: String = ""
)
