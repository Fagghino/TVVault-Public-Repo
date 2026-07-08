package com.fagghino.tvvault.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.fagghino.tvvault.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mediaItem: MediaItem): Long

    @Query("SELECT * FROM media_items WHERE localId = :localId")
    suspend fun getById(localId: Long): MediaItem?

    @Query("SELECT * FROM media_items WHERE remoteId = :remoteId")
    suspend fun getMediaItemByRemoteId(remoteId: String): MediaItem?

    @Query("SELECT * FROM media_items WHERE provider = :provider AND providerId = :providerId")
    suspend fun getByProvider(provider: String, providerId: String): MediaItem?

    @Query("SELECT * FROM media_items WHERE mediaType = :mediaType AND deleted = 0")
    fun getByMediaType(mediaType: String): Flow<List<MediaItem>>

    @Query("SELECT * FROM media_items")
    suspend fun getAll(): List<MediaItem>

    @Transaction
    @Query("SELECT * FROM media_items WHERE mediaType = :mediaType AND deleted = 0")
    fun getMediaItemsWithState(mediaType: String): Flow<List<MediaItemWithState>>

    @Query("""
        DELETE FROM media_items 
        WHERE localId NOT IN (SELECT mediaItemLocalId FROM user_media_states)
    """)
    suspend fun deleteUnusedMedia()

    @Delete
    suspend fun delete(mediaItem: MediaItem)
}

@Dao
interface UserMediaStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: UserMediaState): Long

    @Query("SELECT * FROM user_media_states WHERE mediaItemLocalId = :mediaItemLocalId")
    suspend fun getByMediaId(mediaItemLocalId: Long): UserMediaState?

    @Query("SELECT * FROM user_media_states WHERE remoteId = :remoteId")
    suspend fun getStateByRemoteId(remoteId: String): UserMediaState?

    @Query("SELECT * FROM user_media_states")
    suspend fun getAll(): List<UserMediaState>

    @Query("SELECT * FROM user_media_states WHERE mediaItemLocalId = :mediaItemLocalId")
    fun observeByMediaId(mediaItemLocalId: Long): Flow<UserMediaState?>

    @Query("""
        SELECT m.* FROM media_items m 
        INNER JOIN user_media_states u ON m.localId = u.mediaItemLocalId 
        WHERE m.mediaType = :mediaType AND u.personalStatus = :status AND m.deleted = 0 AND u.deleted = 0
    """)
    fun getMediaByStatus(mediaType: String, status: String): Flow<List<MediaItem>>

    @Query("""
        SELECT m.* FROM media_items m
        INNER JOIN user_media_states u ON m.localId = u.mediaItemLocalId
        WHERE m.mediaType = :mediaType AND u.favorite = 1 AND m.deleted = 0 AND u.deleted = 0
    """)
    fun getFavorites(mediaType: String): Flow<List<MediaItem>>

    @Query("""
        SELECT m.* FROM media_items m
        INNER JOIN user_media_states u ON m.localId = u.mediaItemLocalId
        WHERE m.mediaType = :mediaType AND u.watchlist = 1 AND m.deleted = 0 AND u.deleted = 0
    """)
    fun getWatchlist(mediaType: String): Flow<List<MediaItem>>
}

@Dao
interface SeasonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(season: Season): Long

    @Query("SELECT * FROM seasons WHERE mediaItemLocalId = :mediaItemLocalId AND deleted = 0 ORDER BY seasonNumber ASC")
    fun getSeasonsForMedia(mediaItemLocalId: Long): Flow<List<Season>>

    @Query("SELECT * FROM seasons WHERE mediaItemLocalId = :mediaItemLocalId AND seasonNumber = :seasonNumber")
    suspend fun getSeasonByNumber(mediaItemLocalId: Long, seasonNumber: Int): Season?

    @Query("SELECT * FROM seasons WHERE remoteId = :remoteId")
    suspend fun getSeasonByRemoteId(remoteId: String): Season?

    @Query("SELECT * FROM seasons")
    suspend fun getAll(): List<Season>
}

@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(episode: Episode): Long

    @Query("SELECT * FROM episodes WHERE localId = :localId")
    suspend fun getById(localId: Long): Episode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(episodes: List<Episode>)

    @Query("SELECT * FROM episodes WHERE mediaItemLocalId = :mediaItemLocalId AND seasonNumber = :seasonNumber AND deleted = 0 ORDER BY episodeNumber ASC")
    fun getEpisodesForSeason(mediaItemLocalId: Long, seasonNumber: Int): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE mediaItemLocalId = :mediaItemLocalId AND seasonNumber = :seasonNumber AND deleted = 0 ORDER BY episodeNumber ASC")
    suspend fun getEpisodesForSeasonDirect(mediaItemLocalId: Long, seasonNumber: Int): List<Episode>

    @Query("SELECT * FROM episodes WHERE mediaItemLocalId = :mediaItemLocalId AND deleted = 0 ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getAllEpisodesForMedia(mediaItemLocalId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE mediaItemLocalId = :mediaItemLocalId AND deleted = 0 ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun getAllEpisodesForMediaDirect(mediaItemLocalId: Long): List<Episode>

    @Query("SELECT COUNT(*) FROM episodes WHERE mediaItemLocalId = :mediaItemLocalId AND deleted = 0")
    suspend fun getEpisodesCountForMedia(mediaItemLocalId: Long): Int

    @Query("SELECT * FROM episodes WHERE mediaItemLocalId = :mediaItemLocalId AND seasonNumber = :seasonNumber AND episodeNumber = :episodeNumber")
    suspend fun getEpisodeByNumber(mediaItemLocalId: Long, seasonNumber: Int, episodeNumber: Int): Episode?

    @Query("SELECT * FROM episodes WHERE remoteId = :remoteId")
    suspend fun getEpisodeByRemoteId(remoteId: String): Episode?

    @Query("SELECT * FROM episodes")
    suspend fun getAll(): List<Episode>

    @Query("""
        SELECT * FROM episodes 
        WHERE mediaItemLocalId = :mediaId AND deleted = 0 AND localId NOT IN (
            SELECT episodeLocalId FROM user_episode_states WHERE watched = 1 AND deleted = 0
        ) 
        ORDER BY seasonNumber ASC, episodeNumber ASC 
        LIMIT 1
    """)
    suspend fun getNextEpisodeToWatch(mediaId: Long): Episode?
}

@Dao
interface UserEpisodeStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: UserEpisodeState): Long

    @Query("SELECT * FROM user_episode_states WHERE episodeLocalId = :episodeLocalId")
    suspend fun getByEpisodeId(episodeLocalId: Long): UserEpisodeState?

    @Query("SELECT * FROM user_episode_states WHERE remoteId = :remoteId")
    suspend fun getStateByRemoteId(remoteId: String): UserEpisodeState?

    @Query("SELECT * FROM user_episode_states WHERE episodeLocalId IN (:episodeIds)")
    suspend fun getStatesByEpisodeIds(episodeIds: List<Long>): List<UserEpisodeState>

    @Query("""
        SELECT u.* FROM user_episode_states u 
        INNER JOIN episodes e ON u.episodeLocalId = e.localId 
        WHERE e.mediaItemLocalId = :mediaItemLocalId AND u.watched = 1 AND u.deleted = 0 AND e.deleted = 0
    """)
    fun getWatchedEpisodesForMedia(mediaItemLocalId: Long): Flow<List<UserEpisodeState>>

    @Query("""
        SELECT u.* FROM user_episode_states u 
        INNER JOIN episodes e ON u.episodeLocalId = e.localId 
        WHERE e.mediaItemLocalId = :mediaItemLocalId AND u.watched = 1 AND u.deleted = 0 AND e.deleted = 0
    """)
    suspend fun getWatchedEpisodesForMediaDirect(mediaItemLocalId: Long): List<UserEpisodeState>

    @Query("""
        SELECT COUNT(u.localId) FROM user_episode_states u 
        INNER JOIN episodes e ON u.episodeLocalId = e.localId 
        WHERE e.mediaItemLocalId = :mediaItemLocalId AND u.watched = 1 AND u.deleted = 0 AND e.deleted = 0
    """)
    suspend fun getWatchedEpisodesCountForMedia(mediaItemLocalId: Long): Int

    @Query("""
        SELECT COUNT(u.localId) FROM user_episode_states u
        INNER JOIN episodes e ON u.episodeLocalId = e.localId
        INNER JOIN media_items m ON e.mediaItemLocalId = m.localId
        WHERE u.watched = 1 AND u.deleted = 0 AND e.deleted = 0 AND m.deleted = 0
    """)
    fun getGlobalWatchedEpisodesCount(): Flow<Int>

    @Query("SELECT * FROM user_episode_states")
    suspend fun getAll(): List<UserEpisodeState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(states: List<UserEpisodeState>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchEvents(events: List<WatchEvent>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchEvent(event: WatchEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMediaState(state: UserMediaState)

    @Transaction
    suspend fun updateSeasonWatchedTransaction(
        states: List<UserEpisodeState>,
        events: List<WatchEvent>,
        mediaState: UserMediaState?
    ) {
        insertOrUpdateAll(states)
        if (events.isNotEmpty()) {
            insertWatchEvents(events)
        }
        if (mediaState != null) {
            insertOrUpdateMediaState(mediaState)
        }
    }

    @Transaction
    suspend fun updateEpisodeWatchedTransaction(
        state: UserEpisodeState,
        event: WatchEvent?,
        mediaState: UserMediaState?
    ) {
        insertOrUpdate(state)
        if (event != null) {
            insertWatchEvent(event)
        }
        if (mediaState != null) {
            insertOrUpdateMediaState(mediaState)
        }
    }
}

@Dao
interface WatchEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: WatchEvent): Long

    @Query("SELECT * FROM watch_events ORDER BY watchedAt DESC")
    fun getAllEvents(): Flow<List<WatchEvent>>

    @Query("SELECT * FROM watch_events WHERE remoteId = :remoteId")
    suspend fun getWatchEventByRemoteId(remoteId: String): WatchEvent?

    @Query("SELECT * FROM watch_events WHERE mediaItemLocalId = :mediaItemLocalId ORDER BY watchedAt DESC")
    fun getEventsForMedia(mediaItemLocalId: Long): Flow<List<WatchEvent>>

    @Query("SELECT * FROM watch_events")
    suspend fun getAll(): List<WatchEvent>

    @Delete
    suspend fun delete(event: WatchEvent)
}

@Dao
interface ImportJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: ImportJob): Long

    @Query("SELECT * FROM import_jobs ORDER BY startedAt DESC")
    fun getAllJobs(): Flow<List<ImportJob>>

    @Query("SELECT * FROM import_jobs WHERE localId = :jobId")
    suspend fun getById(jobId: Long): ImportJob?
}

@Dao
interface ImportMatchCandidateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(candidate: ImportMatchCandidate): Long

    @Query("SELECT * FROM import_match_candidates WHERE importJobId = :jobId")
    fun getCandidatesForJob(jobId: Long): Flow<List<ImportMatchCandidate>>

    @Query("SELECT * FROM import_match_candidates WHERE localId = :id")
    suspend fun getById(id: Long): ImportMatchCandidate?

    @Delete
    suspend fun delete(candidate: ImportMatchCandidate)
}

@Dao
interface AppSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: AppSetting)

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSetting>
}
