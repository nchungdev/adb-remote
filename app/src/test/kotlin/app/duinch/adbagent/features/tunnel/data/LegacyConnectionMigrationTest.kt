package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyConnectionMigrationTest {
    @Test
    fun `the old keys become the primary connection`() {
        assertEquals(
            SavedConnection(SavedConnection.PRIMARY_ID, "10.0.0.2:8080", true, "Mac"),
            legacyPrimaryConnection("10.0.0.2:8080", true, "Mac")
        )
    }

    @Test
    fun `no saved address means nothing to bring over`() {
        assertNull(legacyPrimaryConnection("", true, "Mac"))
    }
}
