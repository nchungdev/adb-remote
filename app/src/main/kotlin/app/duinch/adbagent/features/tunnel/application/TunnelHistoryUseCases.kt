package app.duinch.adbagent.features.tunnel.application

import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryEntry
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryRepository
import kotlinx.coroutines.flow.Flow

class ObserveTunnelHistoryUseCase(
    private val repository: TunnelHistoryRepository
) {
    operator fun invoke(): Flow<List<TunnelHistoryEntry>> = repository.observeHistory()
}

class ClearTunnelHistoryUseCase(
    private val repository: TunnelHistoryRepository
) {
    operator fun invoke() {
        repository.clearHistory()
    }
}
