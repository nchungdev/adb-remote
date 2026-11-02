package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.features.tunnel.domain.HostActivityRepository
import app.duinch.adbagent.features.tunnel.domain.HostCommandRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

class InMemoryHostActivityRepository(
    private val maxCapacity: Int = 50
) : HostActivityRepository {

    private val deque = ConcurrentLinkedDeque<HostCommandRecord>()
    private val commandsFlow = MutableStateFlow<List<HostCommandRecord>>(emptyList())

    override fun observeCommands(): Flow<List<HostCommandRecord>> = commandsFlow.asStateFlow()

    override fun recordCommand(record: HostCommandRecord) {
        val existingIndex = deque.indexOfFirst { it.id == record.id }
        if (existingIndex >= 0) {
            val list = deque.toMutableList()
            list[existingIndex] = record
            deque.clear()
            deque.addAll(list)
        } else {
            deque.addFirst(record)
            while (deque.size > maxCapacity) {
                deque.pollLast()
            }
        }
        commandsFlow.value = deque.toList()
    }

    override fun clearCommands() {
        deque.clear()
        commandsFlow.value = emptyList()
    }
}
