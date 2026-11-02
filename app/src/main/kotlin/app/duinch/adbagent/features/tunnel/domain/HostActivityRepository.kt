package app.duinch.adbagent.features.tunnel.domain

import kotlinx.coroutines.flow.Flow

interface HostActivityRepository {
    fun observeCommands(): Flow<List<HostCommandRecord>>
    fun recordCommand(record: HostCommandRecord)
    fun clearCommands()
}
