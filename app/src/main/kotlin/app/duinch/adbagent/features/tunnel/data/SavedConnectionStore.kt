package app.duinch.adbagent.features.tunnel.data

import android.util.Log
import app.duinch.adbagent.features.tunnel.data.db.SavedConnectionDao
import app.duinch.adbagent.features.tunnel.data.db.SavedConnectionEntity
import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import app.duinch.adbagent.features.tunnel.domain.ServerIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Callers read connections synchronously, some on the main thread, so they read this memory copy.
 * It is loaded once at startup; each change then updates it at once and reaches the database in order.
 */
internal class SavedConnectionStore(
    private val dao: SavedConnectionDao,
    private val encrypt: (String) -> String,
    private val decrypt: (String) -> String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val positions = mutableMapOf<String, Int>()
    private var nextPosition = 0
    private var connections: List<SavedConnection>

    init {
        val rows = runBlocking(dispatcher) { dao.getAll() }
        rows.forEach { positions[it.id] = it.position }
        nextPosition = (rows.maxOfOrNull { it.position } ?: -1) + 1
        connections = rows.map { it.toDomain() }
        CoroutineScope(SupervisorJob() + dispatcher).launch {
            for (write in writes) {
                runCatching { write() }.onFailure { Log.e(TAG, "Could not save connection", it) }
            }
        }
    }

    @Synchronized
    fun getAll(): List<SavedConnection> = connections

    @Synchronized
    fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            positions[id] = index
            val conn = connections.find { it.id == id }
            if (conn != null) {
                val entity = conn.toEntity(index)
                writes.trySend { dao.upsert(entity) }
            }
        }
        connections = connections.sortedBy { positions[it.id] ?: Int.MAX_VALUE }
    }

    /** Changes one connection atomically: null from [transform] removes it. */
    @Synchronized
    fun update(id: String, transform: (SavedConnection?) -> SavedConnection?) {
        val current = connections.find { it.id == id }
        val next = transform(current)
        when {
            next != null -> save(next, current != null)
            current != null -> remove(id)
        }
    }

    /** Returns once [connection] is on disk, so its old copy can be deleted safely. */
    fun importBlocking(connection: SavedConnection) = runBlocking {
        val done = CompletableDeferred<Unit>()
        update(connection.id) { connection }
        writes.send { done.complete(Unit) }
        done.await()
    }

    private fun save(connection: SavedConnection, exists: Boolean) {
        connections = if (exists) {
            connections.map { if (it.id == connection.id) connection else it }
        } else {
            connections + connection
        }
        val entity = connection.toEntity(positions.getOrPut(connection.id) { nextPosition++ })
        writes.trySend { dao.upsert(entity) }
    }

    private fun remove(id: String) {
        connections = connections.filterNot { it.id == id }
        positions.remove(id)
        writes.trySend { dao.delete(id) }
    }

    private fun SavedConnectionEntity.toDomain() = SavedConnection(
        id = id,
        serverUrl = decrypt(encryptedServerUrl),
        wantsConnected = wantsConnected,
        name = encryptedName?.let(decrypt)?.ifBlank { null },
        server = serverId?.let { ServerIdentity(it, encryptedServerName?.let(decrypt).orEmpty()) }
    )

    private fun SavedConnection.toEntity(position: Int) = SavedConnectionEntity(
        id = id,
        encryptedServerUrl = encrypt(serverUrl),
        wantsConnected = wantsConnected,
        encryptedName = name?.let(encrypt),
        position = position,
        serverId = server?.id,
        encryptedServerName = server?.name?.let(encrypt)
    )

    private companion object {
        const val TAG = "SavedConnectionStore"
    }
}
