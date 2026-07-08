package com.fagghino.tvvault.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fagghino.tvvault.data.local.dao.*
import com.fagghino.tvvault.data.local.entity.*

@Database(
    entities = [
        MediaItem::class,
        UserMediaState::class,
        Season::class,
        Episode::class,
        UserEpisodeState::class,
        WatchEvent::class,
        ImportJob::class,
        ImportMatchCandidate::class,
        AppSetting::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TVVaultDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun userMediaStateDao(): UserMediaStateDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun userEpisodeStateDao(): UserEpisodeStateDao
    abstract fun watchEventDao(): WatchEventDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun importMatchCandidateDao(): ImportMatchCandidateDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: TVVaultDatabase? = null

        fun getDatabase(context: Context): TVVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TVVaultDatabase::class.java,
                    "tvvault_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val currentTime = System.currentTimeMillis()
                
                // media_items
                db.execSQL("ALTER TABLE media_items ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media_items ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE media_items ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $currentTime")
                
                // user_media_states
                db.execSQL("ALTER TABLE user_media_states ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_media_states ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                
                // seasons
                db.execSQL("ALTER TABLE seasons ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE seasons ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE seasons ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $currentTime")

                // episodes
                db.execSQL("ALTER TABLE episodes ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE episodes ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE episodes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $currentTime")

                // user_episode_states
                db.execSQL("ALTER TABLE user_episode_states ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_episode_states ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")

                // watch_events
                db.execSQL("ALTER TABLE watch_events ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_events ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE watch_events ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT $currentTime")

                // Generate UUIDs for existing rows
                val tables = listOf("media_items", "user_media_states", "seasons", "episodes", "user_episode_states", "watch_events")
                for (table in tables) {
                    val cursor = db.query("SELECT localId FROM $table")
                    while (cursor.moveToNext()) {
                        val localId = cursor.getLong(0)
                        val uuid = java.util.UUID.randomUUID().toString()
                        db.execSQL("UPDATE $table SET remoteId = '$uuid' WHERE localId = $localId")
                    }
                    cursor.close()
                }
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add columns
                db.execSQL("ALTER TABLE user_media_states ADD COLUMN mediaItemRemoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE seasons ADD COLUMN mediaItemRemoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE episodes ADD COLUMN mediaItemRemoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE user_episode_states ADD COLUMN episodeRemoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_events ADD COLUMN mediaItemRemoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE watch_events ADD COLUMN episodeRemoteId TEXT NOT NULL DEFAULT ''")

                // Populate user_media_states
                db.execSQL("""
                    UPDATE user_media_states 
                    SET mediaItemRemoteId = COALESCE(
                        (SELECT remoteId FROM media_items WHERE localId = user_media_states.mediaItemLocalId),
                        ''
                    )
                """)

                // Populate seasons
                db.execSQL("""
                    UPDATE seasons 
                    SET mediaItemRemoteId = COALESCE(
                        (SELECT remoteId FROM media_items WHERE localId = seasons.mediaItemLocalId),
                        ''
                    )
                """)

                // Populate episodes
                db.execSQL("""
                    UPDATE episodes 
                    SET mediaItemRemoteId = COALESCE(
                        (SELECT remoteId FROM media_items WHERE localId = episodes.mediaItemLocalId),
                        ''
                    )
                """)

                // Populate user_episode_states
                db.execSQL("""
                    UPDATE user_episode_states 
                    SET episodeRemoteId = COALESCE(
                        (SELECT remoteId FROM episodes WHERE localId = user_episode_states.episodeLocalId),
                        ''
                    )
                """)

                // Populate watch_events (mediaItemRemoteId)
                db.execSQL("""
                    UPDATE watch_events 
                    SET mediaItemRemoteId = COALESCE(
                        (SELECT remoteId FROM media_items WHERE localId = watch_events.mediaItemLocalId),
                        ''
                    )
                """)

                // Populate watch_events (episodeRemoteId)
                db.execSQL("""
                    UPDATE watch_events 
                    SET episodeRemoteId = COALESCE(
                        (SELECT remoteId FROM episodes WHERE localId = watch_events.episodeLocalId),
                        ''
                    )
                """)
            }
        }
    }
}
