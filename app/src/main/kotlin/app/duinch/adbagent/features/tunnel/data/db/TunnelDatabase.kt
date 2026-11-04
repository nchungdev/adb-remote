package app.duinch.adbagent.features.tunnel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [SavedConnectionEntity::class], version = 2)
abstract class TunnelDatabase : RoomDatabase() {
    abstract fun connections(): SavedConnectionDao

    companion object {
        fun create(context: Context): TunnelDatabase =
            Room.databaseBuilder(context.applicationContext, TunnelDatabase::class.java, "tunnel.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE connections ADD COLUMN server_id TEXT")
                db.execSQL("ALTER TABLE connections ADD COLUMN server_name TEXT")
            }
        }
    }
}
