package app.duinch.adbagent.features.tunnel.presentation

import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

class ServerNamesTest {
    private class FakeRepo : TunnelConfigRepository {
        val connections = linkedMapOf<String, SavedConnection>()
        override fun getConfig() = TunnelConfig()
        override fun saveConfig(config: TunnelConfig) = Unit
        override fun getConnections() = connections.values.toList()
        override fun upsertConnection(connection: SavedConnection) { connections[connection.id] = connection }
        override fun removeConnection(id: String) { connections.remove(id) }
    }

    private val repo = FakeRepo()

    @Test
    fun `renaming the primary card saves it on that connection`() {
        repo.upsertConnection(SavedConnection(PRIMARY_CONNECTION_ID, "10.0.0.2:8080"))
        val state = MutableStateFlow(DashboardUiState(serverUrlInput = "10.0.0.2:8080"))
        renameConnection(state, repo, PRIMARY_CONNECTION_ID, "  Mac văn phòng ")
        assertEquals("Mac văn phòng", state.value.primaryDisplayName)
        assertEquals("Mac văn phòng", repo.connections[PRIMARY_CONNECTION_ID]?.name)
    }

    @Test
    fun `renaming an extra card only touches that card`() {
        repo.upsertConnection(SavedConnection("a", "u1"))
        repo.upsertConnection(SavedConnection("b", "u2"))
        val state = MutableStateFlow(
            DashboardUiState(extraSlots = listOf(ServerConnectionSlot("a", serverUrlInput = "u1"), ServerConnectionSlot("b", serverUrlInput = "u2")))
        )
        renameConnection(state, repo, "b", "Nhà")
        assertNull(state.value.extraSlots[0].displayName)
        assertEquals("Nhà", state.value.extraSlots[1].displayName)
        assertNull(repo.connections["a"]?.name)
        assertEquals("Nhà", repo.connections["b"]?.name)
    }

    @Test
    fun `a blank name clears it`() {
        repo.upsertConnection(SavedConnection(PRIMARY_CONNECTION_ID, "u", name = "Cũ"))
        val state = MutableStateFlow(DashboardUiState(primaryDisplayName = "Cũ", serverUrlInput = "u"))
        renameConnection(state, repo, PRIMARY_CONNECTION_ID, "   ")
        assertNull(state.value.primaryDisplayName)
        assertNull(repo.connections[PRIMARY_CONNECTION_ID]?.name)
    }

    @Test
    fun `a named card keeps its name and wanted flag when the tunnel URL changes`() {
        repo.upsertConnection(SavedConnection(PRIMARY_CONNECTION_ID, "https://old.trycloudflare.com", wantsConnected = true, name = "Mac"))
        savePrimaryTarget(repo, "https://new.trycloudflare.com", "Mac")
        val saved = repo.connections.getValue(PRIMARY_CONNECTION_ID)
        assertEquals("https://new.trycloudflare.com", saved.serverUrl)
        assertEquals("Mac", saved.name)
        assertTrue(saved.wantsConnected)
    }

    @Test
    fun `a first connect keeps a name given before there was an address`() {
        savePrimaryTarget(repo, "u", "Mac")
        val saved = repo.connections.getValue(PRIMARY_CONNECTION_ID)
        assertEquals("Mac", saved.name)
        assertFalse(saved.wantsConnected)
    }

    @Test
    fun `renaming a server updates all connections belonging to that server`() {
        val server = app.duinch.adbagent.features.tunnel.domain.ServerIdentity("srv-1", "Old Name")
        repo.upsertConnection(SavedConnection(PRIMARY_CONNECTION_ID, "10.0.0.2:8080", server = server))
        repo.upsertConnection(SavedConnection("slot-2", "https://srv.trycloudflare.com", server = server))
        val state = MutableStateFlow(
            DashboardUiState(
                primaryServer = server,
                extraSlots = listOf(ServerConnectionSlot("slot-2", server = server))
            )
        )
        renameServer(state, repo, "srv-1", "Mac Studio M2")
        assertEquals("Mac Studio M2", state.value.primaryServer?.name)
        assertEquals("Mac Studio M2", state.value.extraSlots[0].server?.name)
        assertEquals("Mac Studio M2", repo.connections[PRIMARY_CONNECTION_ID]?.server?.name)
        assertEquals("Mac Studio M2", repo.connections["slot-2"]?.server?.name)
    }
}
