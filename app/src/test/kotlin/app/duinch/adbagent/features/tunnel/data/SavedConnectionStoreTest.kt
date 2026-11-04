package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.features.tunnel.data.db.SavedConnectionDao
import app.duinch.adbagent.features.tunnel.data.db.SavedConnectionEntity
import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers

class SavedConnectionStoreTest {
    private class FakeDao : SavedConnectionDao {
        val rows = linkedMapOf<String, SavedConnectionEntity>()
        override suspend fun getAll() = rows.values.sortedBy { it.position }
        override suspend fun upsert(connection: SavedConnectionEntity) { rows[connection.id] = connection }
        override suspend fun delete(id: String) { rows.remove(id) }
    }

    private val dao = FakeDao()

    private fun store() = SavedConnectionStore(dao, { "enc:$it" }, { it.removePrefix("enc:") }, Dispatchers.Unconfined)

    @Test
    fun `a saved connection comes back after a restart, in order`() {
        val first = store()
        first.update("primary") { SavedConnection("primary", "u1", true, "Mac") }
        first.update("slot-2") { SavedConnection("slot-2", "u2") }
        assertEquals(
            listOf(SavedConnection("primary", "u1", true, "Mac"), SavedConnection("slot-2", "u2")),
            store().getAll()
        )
    }

    @Test
    fun `address and name are encrypted on disk`() {
        store().update("a") { SavedConnection("a", "u1", name = "Mac") }
        assertEquals("enc:u1", dao.rows.getValue("a").encryptedServerUrl)
        assertEquals("enc:Mac", dao.rows.getValue("a").encryptedName)
    }

    @Test
    fun `updating keeps the position and removing deletes the row`() {
        val store = store()
        store.update("a") { SavedConnection("a", "u1") }
        store.update("b") { SavedConnection("b", "u2") }
        store.update("a") { it?.copy(wantsConnected = true) }
        assertEquals(listOf("a", "b"), store().getAll().map { it.id })
        assertTrue(store().getAll().first().wantsConnected)
        store.update("a") { null }
        assertNull(dao.rows["a"])
        assertEquals(listOf("b"), store.getAll().map { it.id })
    }

    @Test
    fun `a new connection after a restart goes last`() {
        store().update("a") { SavedConnection("a", "u1") }
        store().update("b") { SavedConnection("b", "u2") }
        assertEquals(listOf("a", "b"), store().getAll().map { it.id })
    }

    @Test
    fun `a name the phone can no longer decrypt is dropped`() {
        dao.rows["a"] = SavedConnectionEntity("a", "enc:u1", false, "enc:", 0)
        assertNull(store().getAll().single().name)
        assertFalse(store().getAll().single().wantsConnected)
    }

    @Test
    fun `an import is on disk when it returns`() {
        store().importBlocking(SavedConnection("primary", "u1", true))
        assertEquals("enc:u1", dao.rows.getValue("primary").encryptedServerUrl)
    }
}
