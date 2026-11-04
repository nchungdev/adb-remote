package app.duinch.adbagent.features.tunnel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SavedConnectionDao {
    @Query("SELECT * FROM connections ORDER BY position")
    suspend fun getAll(): List<SavedConnectionEntity>

    @Upsert
    suspend fun upsert(connection: SavedConnectionEntity)

    @Query("DELETE FROM connections WHERE id = :id")
    suspend fun delete(id: String)
}
