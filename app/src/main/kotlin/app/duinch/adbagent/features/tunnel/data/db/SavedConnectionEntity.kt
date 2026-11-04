package app.duinch.adbagent.features.tunnel.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Address and name are stored encrypted: they say which machines this phone can reach. */
@Entity(tableName = "connections")
data class SavedConnectionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_url") val encryptedServerUrl: String,
    @ColumnInfo(name = "wants_connected") val wantsConnected: Boolean,
    @ColumnInfo(name = "name") val encryptedName: String?,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    @ColumnInfo(name = "server_name") val encryptedServerName: String? = null
)
