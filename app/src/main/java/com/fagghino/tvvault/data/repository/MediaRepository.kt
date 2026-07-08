package com.fagghino.tvvault.data.repository

import com.fagghino.tvvault.data.local.dao.*
import com.fagghino.tvvault.data.local.entity.*
import com.fagghino.tvvault.data.remote.TmdbService
import com.fagghino.tvvault.data.remote.dto.TmdbSeasonDto
import com.fagghino.tvvault.data.remote.dto.TmdbTvShowDetailDto
import com.fagghino.tvvault.data.sync.FirestoreSyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import android.util.Log
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MediaRepository(
    private val mediaItemDao: MediaItemDao,
    private val userMediaStateDao: UserMediaStateDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val userEpisodeStateDao: UserEpisodeStateDao,
    private val watchEventDao: WatchEventDao,
    private val importJobDao: ImportJobDao,
    private val importMatchCandidateDao: ImportMatchCandidateDao,
    private val appSettingDao: AppSettingDao,
    private val tmdbService: TmdbService,
    private val syncEngine: FirestoreSyncEngine
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Getter DAOs for TvTimeImporter
    fun getImportJobDao(): ImportJobDao = importJobDao
    fun getImportMatchCandidateDao(): ImportMatchCandidateDao = importMatchCandidateDao

    // Shows and Movies getters
    fun observeShows(): Flow<List<MediaItem>> = mediaItemDao.getByMediaType("tv")
    fun observeMovies(): Flow<List<MediaItem>> = mediaItemDao.getByMediaType("movie")
    fun observeShowsWithState(): Flow<List<MediaItemWithState>> = mediaItemDao.getMediaItemsWithState("tv")

    // State observers
    fun observeMediaState(mediaId: Long): Flow<UserMediaState?> = userMediaStateDao.observeByMediaId(mediaId)

    // Watchlist & Favorites
    fun observeWatchlist(mediaType: String): Flow<List<MediaItem>> = userMediaStateDao.getWatchlist(mediaType)
    fun observeFavorites(mediaType: String): Flow<List<MediaItem>> = userMediaStateDao.getFavorites(mediaType)

    // Status queries
    fun observeMediaByStatus(mediaType: String, status: String): Flow<List<MediaItem>> = 
        userMediaStateDao.getMediaByStatus(mediaType, status)

    // CRUD Ops
    suspend fun saveMediaItem(mediaItem: MediaItem): Long {
        val id = mediaItemDao.insert(mediaItem)
        repositoryScope.launch {
            mediaItemDao.getById(id)?.let { syncEngine.pushMediaItem(it) }
        }
        return id
    }

    suspend fun updateMediaState(state: UserMediaState): Long {
        val parentItem = mediaItemDao.getById(state.mediaItemLocalId)
        val updatedState = state.copy(
            mediaItemRemoteId = parentItem?.remoteId ?: state.mediaItemRemoteId
        )
        val id = userMediaStateDao.insertOrUpdate(updatedState)
        repositoryScope.launch {
            userMediaStateDao.getByMediaId(updatedState.mediaItemLocalId)?.let { syncEngine.pushUserMediaState(it) }
        }
        return id
    }

    suspend fun getMediaItemByProvider(providerId: String): MediaItem? {
        return mediaItemDao.getByProvider("tmdb", providerId)
    }

    suspend fun getMediaItemById(id: Long): MediaItem? {
        return mediaItemDao.getById(id)
    }

    // Episodes & Seasons
    fun observeSeasons(mediaId: Long): Flow<List<Season>> = seasonDao.getSeasonsForMedia(mediaId)
    fun observeEpisodes(mediaId: Long, seasonNumber: Int): Flow<List<Episode>> = episodeDao.getEpisodesForSeason(mediaId, seasonNumber)
    fun observeAllEpisodes(mediaId: Long): Flow<List<Episode>> = episodeDao.getAllEpisodesForMedia(mediaId)
    fun observeWatchedEpisodes(mediaId: Long): Flow<List<UserEpisodeState>> = userEpisodeStateDao.getWatchedEpisodesForMedia(mediaId)
    fun observeGlobalWatchedEpisodesCount(): Flow<Int> = userEpisodeStateDao.getGlobalWatchedEpisodesCount()

    suspend fun saveSeason(season: Season): Long {
        val parentItem = mediaItemDao.getById(season.mediaItemLocalId)
        val updatedSeason = season.copy(
            mediaItemRemoteId = parentItem?.remoteId ?: season.mediaItemRemoteId
        )
        val id = seasonDao.insert(updatedSeason)
        repositoryScope.launch {
            seasonDao.getSeasonByNumber(updatedSeason.mediaItemLocalId, updatedSeason.seasonNumber)?.let { syncEngine.pushSeason(it) }
        }
        return id
    }
    
    suspend fun saveEpisodes(episodes: List<Episode>) {
        if (episodes.isEmpty()) return
        val parentItem = mediaItemDao.getById(episodes.first().mediaItemLocalId)
        val updatedEpisodes = episodes.map { ep ->
            ep.copy(mediaItemRemoteId = parentItem?.remoteId ?: ep.mediaItemRemoteId)
        }
        episodeDao.insertAll(updatedEpisodes)
        repositoryScope.launch {
            updatedEpisodes.forEach { ep ->
                syncEngine.pushEpisode(ep)
            }
        }
    }

    // Settings
    suspend fun saveSetting(key: String, value: String) {
        appSettingDao.insert(AppSetting(key, value))
    }

    suspend fun getSetting(key: String): String? {
        return appSettingDao.getValue(key)
    }

    // TMDb live calls (used by ViewModel for upcoming episodes)
    suspend fun getTvShowDetails(tmdbId: Int): TmdbTvShowDetailDto? {
        return try { tmdbService.getTvShowDetails(tmdbId) } catch (e: Exception) { null }
    }

    suspend fun getSeasonWithAirDates(tmdbId: Int, seasonNumber: Int): TmdbSeasonDto? {
        return try { tmdbService.getSeasonWithAirDates(tmdbId, seasonNumber) } catch (e: Exception) { null }
    }

    // TMDb Online Integration
    suspend fun searchTvShowsOnline(query: String): List<MediaItem> {
        return try {
            val response = tmdbService.searchTvShows(query)
            response.results.map { dto ->
                MediaItem(
                    provider = "tmdb",
                    providerId = dto.id.toString(),
                    title = dto.name,
                    originalTitle = dto.originalName ?: dto.name,
                    overview = dto.overview ?: "",
                    releaseDate = dto.firstAirDate,
                    posterPath = dto.posterPath,
                    backdropPath = dto.backdropPath,
                    mediaType = "tv",
                    genreIds = dto.genreIds.joinToString(",")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchMoviesOnline(query: String): List<MediaItem> {
        return try {
            val response = tmdbService.searchMovies(query)
            response.results.map { dto ->
                MediaItem(
                    provider = "tmdb",
                    providerId = dto.id.toString(),
                    title = dto.title,
                    originalTitle = dto.originalTitle ?: dto.title,
                    overview = dto.overview ?: "",
                    releaseDate = dto.releaseDate,
                    posterPath = dto.posterPath,
                    backdropPath = dto.backdropPath,
                    mediaType = "movie",
                    genreIds = dto.genreIds.joinToString(",")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addMediaToLibrary(item: MediaItem) {
        val existing = mediaItemDao.getByProvider(item.provider, item.providerId)
        val localId = if (existing != null) {
            existing.localId
        } else {
            val newId = mediaItemDao.insert(item)
            repositoryScope.launch {
                mediaItemDao.getById(newId)?.let { syncEngine.pushMediaItem(it) }
            }
            newId
        }
        
        val state = userMediaStateDao.getByMediaId(localId)
        if (state == null) {
            val newState = UserMediaState(
                mediaItemLocalId = localId,
                mediaItemRemoteId = existing?.remoteId ?: item.remoteId,
                personalStatus = "watchlist" // Always "Non iniziata" until user starts watching
            )
            userMediaStateDao.insertOrUpdate(newState)
            repositoryScope.launch {
                syncEngine.pushUserMediaState(newState)
            }
        }

        if (item.mediaType == "tv") {
            try {
                // Se la serie esiste già nel database locale, verifichiamo se ha stagioni caricate
                val existingSeasons = seasonDao.getSeasonsForMedia(localId).first()
                if (existingSeasons.isEmpty()) {
                    Log.d("MediaRepository", "Seasons missing for existing show $localId, reloading from TMDB...")
                    val details = tmdbService.getTvShowDetails(item.providerId.toInt())
                    val totalSeasons = details.numberOfSeasons ?: 0
                    for (seasonNum in 1..totalSeasons) {
                        val seasonDto = tmdbService.getSeasonDetails(item.providerId.toInt(), seasonNum)
                        val season = Season(
                            mediaItemLocalId = localId,
                            mediaItemRemoteId = existing?.remoteId ?: item.remoteId,
                            seasonNumber = seasonNum,
                            name = seasonDto.name,
                            episodeCount = seasonDto.episodes.size
                        )
                        seasonDao.insert(season)
                        repositoryScope.launch {
                            seasonDao.getSeasonByNumber(localId, seasonNum)?.let { syncEngine.pushSeason(it) }
                        }
                        
                        val episodes = seasonDto.episodes.map { ep ->
                            Episode(
                                mediaItemLocalId = localId,
                                mediaItemRemoteId = existing?.remoteId ?: item.remoteId,
                                seasonNumber = seasonNum,
                                episodeNumber = ep.episodeNumber,
                                name = ep.name,
                                overview = ep.overview ?: ""
                            )
                        }
                        episodeDao.insertAll(episodes)
                        repositoryScope.launch {
                            episodes.forEach { syncEngine.pushEpisode(it) }
                        }
                    }
                }
                
                // After adding episodes: if the show was already "completed" but now has
                // unwatched episodes (e.g. new season added), reset status to "watching"
                val currentState = userMediaStateDao.observeByMediaId(localId).first()
                if (currentState?.personalStatus == "completed") {
                    val allEpsCount = episodeDao.getEpisodesCountForMedia(localId)
                    val watchedEpsCount = userEpisodeStateDao.getWatchedEpisodesCountForMedia(localId)
                    if (watchedEpsCount < allEpsCount) {
                        val updatedState = currentState.copy(
                            personalStatus = "watching",
                            mediaItemRemoteId = existing?.remoteId ?: item.remoteId,
                            updatedAt = System.currentTimeMillis(),
                            completedAt = null
                        )
                        userMediaStateDao.insertOrUpdate(updatedState)
                        repositoryScope.launch {
                            syncEngine.pushUserMediaState(updatedState)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaRepository", "Error loading details/seasons for show: ${item.title}", e)
            }
        } else {
            try {
                val details = tmdbService.getMovieDetails(item.providerId.toInt())
                val toInsert = item.copy(localId = localId, runtime = details.runtime)
                mediaItemDao.insert(toInsert)
                repositoryScope.launch {
                    syncEngine.pushMediaItem(toInsert)
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }

    // Import reconciliation helpers
    fun observeImportJobs(): Flow<List<ImportJob>> = importJobDao.getAllJobs()
    fun observeCandidatesForJob(jobId: Long): Flow<List<ImportMatchCandidate>> = 
        importMatchCandidateDao.getCandidatesForJob(jobId)

    suspend fun reconcileCandidate(candidateId: Long, accept: Boolean) {
        val candidate = importMatchCandidateDao.getById(candidateId) ?: return
        val jobId = candidate.importJobId
        
        if (accept) {
            // 1. Fetch item online and add to library
            if (candidate.rawType == "tv") {
                val results = searchTvShowsOnline(candidate.candidateTitle)
                val matched = results.firstOrNull { it.providerId == candidate.candidateProviderId }
                matched?.let { addMediaToLibrary(it) }
            } else {
                val results = searchMoviesOnline(candidate.candidateTitle)
                val matched = results.firstOrNull { it.providerId == candidate.candidateProviderId }
                matched?.let { addMediaToLibrary(it) }
            }
            
            // 2. Mark this candidate as accepted
            importMatchCandidateDao.insert(candidate.copy(accepted = true))
            
            // 3. Mark other candidates for the same rawTitle as rejected
            val allCandidates = importMatchCandidateDao.getCandidatesForJob(jobId).first()
            allCandidates.forEach { other ->
                if (other.rawTitle == candidate.rawTitle && other.localId != candidate.localId) {
                    importMatchCandidateDao.insert(other.copy(rejected = true))
                }
            }
            
            // 4. Update ImportJob counters
            val job = importJobDao.getById(jobId)
            job?.let {
                val updatedJob = it.copy(
                    matchedRows = it.matchedRows + 1,
                    ambiguousRows = maxOf(0, it.ambiguousRows - 1)
                )
                val finalJob = if (updatedJob.ambiguousRows == 0) {
                    updatedJob.copy(status = "completed")
                } else {
                    updatedJob
                }
                importJobDao.insert(finalJob)
            }
        } else {
            // Reject manually
            importMatchCandidateDao.insert(candidate.copy(rejected = true))
            val job = importJobDao.getById(jobId)
            job?.let {
                val updatedJob = it.copy(
                    unmatchedRows = it.unmatchedRows + 1,
                    ambiguousRows = maxOf(0, it.ambiguousRows - 1)
                )
                val finalJob = if (updatedJob.ambiguousRows == 0) {
                    updatedJob.copy(status = "completed")
                } else {
                    updatedJob
                }
                importJobDao.insert(finalJob)
            }
        }
    }

    // Episode state operations
    suspend fun setEpisodeWatched(episodeId: Long, watched: Boolean, mediaItemId: Long) = coroutineScope {
        val parentEpisodeDeferred = async { episodeDao.getById(episodeId) }
        val parentMediaDeferred = async { mediaItemDao.getById(mediaItemId) }
        val currentStateDeferred = async { userEpisodeStateDao.getByEpisodeId(episodeId) }
        val totalEpsDeferred = async { episodeDao.getEpisodesCountForMedia(mediaItemId) }
        val watchedCountDeferred = async { userEpisodeStateDao.getWatchedEpisodesCountForMedia(mediaItemId) }
        val currentMediaStateDeferred = async { userMediaStateDao.getByMediaId(mediaItemId) }

        val parentEpisode = parentEpisodeDeferred.await()
        val parentMedia = parentMediaDeferred.await()
        val currentState = currentStateDeferred.await()
        val totalEps = totalEpsDeferred.await()
        val currentWatchedCount = watchedCountDeferred.await()
        val currentMediaState = currentMediaStateDeferred.await()

        val stateToSave = currentState?.copy(
            watched = watched,
            watchedAt = if (watched) System.currentTimeMillis() else null,
            episodeRemoteId = parentEpisode?.remoteId ?: "",
            updatedAt = System.currentTimeMillis()
        ) ?: UserEpisodeState(
            episodeLocalId = episodeId,
            episodeRemoteId = parentEpisode?.remoteId ?: "",
            watched = watched,
            watchedAt = if (watched) System.currentTimeMillis() else null,
            updatedAt = System.currentTimeMillis()
        )

        // Log watch event
        val event = if (watched) {
            WatchEvent(
                mediaItemLocalId = mediaItemId,
                mediaItemRemoteId = parentMedia?.remoteId ?: "",
                episodeLocalId = episodeId,
                episodeRemoteId = parentEpisode?.remoteId ?: "",
                watchedAt = System.currentTimeMillis(),
                eventType = "watch"
            )
        } else null

        var updatedMediaState: UserMediaState? = null
        if (totalEps > 0) {
            val wasWatchedBefore = currentState?.watched ?: false
            val watchedCount = if (watched && !wasWatchedBefore) {
                currentWatchedCount + 1
            } else if (!watched && wasWatchedBefore) {
                currentWatchedCount - 1
            } else {
                currentWatchedCount
            }
            
            if (currentMediaState != null) {
                val newStatus = when {
                    watchedCount == totalEps -> "completed"
                    watchedCount > 0 -> "watching"
                    else -> "watchlist"
                }
                if (newStatus != currentMediaState.personalStatus) {
                    updatedMediaState = currentMediaState.copy(
                        personalStatus = newStatus,
                        mediaItemRemoteId = parentMedia?.remoteId ?: currentMediaState.mediaItemRemoteId,
                        updatedAt = System.currentTimeMillis(),
                        completedAt = if (newStatus == "completed") System.currentTimeMillis() else null
                    )
                }
            }
        }

        // Run Room updates in a transaction
        userEpisodeStateDao.updateEpisodeWatchedTransaction(stateToSave, event, updatedMediaState)

        // Sync in background (optimistic UI) using stateToSave directly
        repositoryScope.launch {
            syncEngine.pushUserEpisodeState(stateToSave)
            if (event != null) {
                syncEngine.pushWatchEvent(event)
            }
            if (updatedMediaState != null) {
                syncEngine.pushUserMediaState(updatedMediaState)
            }
        }
    }

    suspend fun setSeasonWatched(mediaItemId: Long, seasonNumber: Int, watched: Boolean) = coroutineScope {
        val episodesDeferred = async { episodeDao.getEpisodesForSeasonDirect(mediaItemId, seasonNumber) }
        val parentMediaDeferred = async { mediaItemDao.getById(mediaItemId) }

        val episodes = episodesDeferred.await()
        if (episodes.isEmpty()) return@coroutineScope

        val parentMedia = parentMediaDeferred.await()
        val currentTime = System.currentTimeMillis()

        // Fetch current states for all episodes in this season in one query!
        val episodeIds = episodes.map { it.localId }
        val currentStates = userEpisodeStateDao.getStatesByEpisodeIds(episodeIds).associateBy { it.episodeLocalId }

        val epStatesToSave = mutableListOf<UserEpisodeState>()
        val eventsToSave = mutableListOf<WatchEvent>()

        episodes.forEach { episode ->
            val currentState = currentStates[episode.localId]
            if (currentState == null || currentState.watched != watched) {
                val stateToSave = currentState?.copy(
                    watched = watched,
                    watchedAt = if (watched) currentTime else null,
                    episodeRemoteId = episode.remoteId,
                    updatedAt = currentTime
                ) ?: UserEpisodeState(
                    episodeLocalId = episode.localId,
                    episodeRemoteId = episode.remoteId,
                    watched = watched,
                    watchedAt = if (watched) currentTime else null,
                    updatedAt = currentTime
                )
                epStatesToSave.add(stateToSave)

                if (watched) {
                    eventsToSave.add(
                        WatchEvent(
                            mediaItemLocalId = mediaItemId,
                            mediaItemRemoteId = parentMedia?.remoteId ?: "",
                            episodeLocalId = episode.localId,
                            episodeRemoteId = episode.remoteId,
                            watchedAt = currentTime,
                            eventType = "watch"
                        )
                    )
                }
            }
        }

        // If no changes are needed, we can just return
        if (epStatesToSave.isEmpty()) return@coroutineScope

        // Fetch total episodes count and current watched count in parallel
        val totalEpsDeferred = async { episodeDao.getEpisodesCountForMedia(mediaItemId) }
        val watchedCountDeferred = async { userEpisodeStateDao.getWatchedEpisodesCountForMedia(mediaItemId) }
        val currentMediaStateDeferred = async { userMediaStateDao.getByMediaId(mediaItemId) }

        val totalEps = totalEpsDeferred.await()
        val currentWatchedCount = watchedCountDeferred.await()
        val currentMediaState = currentMediaStateDeferred.await()

        var updatedMediaState: UserMediaState? = null
        if (totalEps > 0) {
            // Count how many we are actually changing from unwatched to watched or vice-versa
            var diff = 0
            epStatesToSave.forEach { state ->
                val wasWatched = currentStates[state.episodeLocalId]?.watched ?: false
                if (state.watched && !wasWatched) {
                    diff++
                } else if (!state.watched && wasWatched) {
                    diff--
                }
            }
            val watchedCount = currentWatchedCount + diff

            if (currentMediaState != null) {
                val newStatus = when {
                    watchedCount == totalEps -> "completed"
                    watchedCount > 0 -> "watching"
                    else -> "watchlist"
                }
                if (newStatus != currentMediaState.personalStatus) {
                    updatedMediaState = currentMediaState.copy(
                        personalStatus = newStatus,
                        mediaItemRemoteId = parentMedia?.remoteId ?: currentMediaState.mediaItemRemoteId,
                        updatedAt = System.currentTimeMillis(),
                        completedAt = if (newStatus == "completed") System.currentTimeMillis() else null
                    )
                }
            }
        }

        // Run Room updates in a transaction
        userEpisodeStateDao.updateSeasonWatchedTransaction(epStatesToSave, eventsToSave, updatedMediaState)

        // Sync in background (optimistic UI) using pushBatch
        repositoryScope.launch {
            syncEngine.pushBatch(
                userEpisodeStates = epStatesToSave,
                watchEvents = eventsToSave,
                userMediaState = updatedMediaState
            )
        }
    }

    suspend fun getNextEpisodeToWatch(mediaId: Long): Episode? {
        return episodeDao.getNextEpisodeToWatch(mediaId)
    }

    suspend fun setEpisodesWatched(episodeIds: List<Long>, watched: Boolean, mediaItemId: Long) = coroutineScope {
        if (episodeIds.isEmpty()) return@coroutineScope
        
        val episodes = episodeIds.mapNotNull { episodeDao.getById(it) }
        val parentMedia = mediaItemDao.getById(mediaItemId)
        val currentTime = System.currentTimeMillis()
        
        val currentStates = userEpisodeStateDao.getStatesByEpisodeIds(episodeIds).associateBy { it.episodeLocalId }
        
        val epStatesToSave = mutableListOf<UserEpisodeState>()
        val eventsToSave = mutableListOf<WatchEvent>()
        
        episodes.forEach { episode ->
            val currentState = currentStates[episode.localId]
            if (currentState == null || currentState.watched != watched) {
                val stateToSave = currentState?.copy(
                    watched = watched,
                    watchedAt = if (watched) currentTime else null,
                    episodeRemoteId = episode.remoteId,
                    updatedAt = currentTime
                ) ?: UserEpisodeState(
                    episodeLocalId = episode.localId,
                    episodeRemoteId = episode.remoteId,
                    watched = watched,
                    watchedAt = if (watched) currentTime else null,
                    updatedAt = currentTime
                )
                epStatesToSave.add(stateToSave)
                
                if (watched) {
                    eventsToSave.add(
                        WatchEvent(
                            mediaItemLocalId = mediaItemId,
                            mediaItemRemoteId = parentMedia?.remoteId ?: "",
                            episodeLocalId = episode.localId,
                            episodeRemoteId = episode.remoteId,
                            watchedAt = currentTime,
                            eventType = "watch"
                        )
                    )
                }
            }
        }
        
        if (epStatesToSave.isEmpty()) return@coroutineScope
        
        // Calculate new status for the media item
        val totalEps = episodeDao.getEpisodesCountForMedia(mediaItemId)
        val currentWatchedCount = userEpisodeStateDao.getWatchedEpisodesCountForMedia(mediaItemId)
        val currentMediaState = userMediaStateDao.getByMediaId(mediaItemId)
        
        // Calculate difference in watched count
        val changedCount = epStatesToSave.count { it.watched } - epStatesToSave.count { !it.watched && currentStates[it.episodeLocalId]?.watched == true }
        val newWatchedCount = (currentWatchedCount + changedCount).coerceIn(0, totalEps)
        
        var updatedMediaState: UserMediaState? = null
        if (totalEps > 0 && currentMediaState != null) {
            val newStatus = when {
                newWatchedCount == totalEps -> "completed"
                newWatchedCount > 0 -> "watching"
                else -> "watchlist"
            }
            if (newStatus != currentMediaState.personalStatus) {
                updatedMediaState = currentMediaState.copy(
                    personalStatus = newStatus,
                    mediaItemRemoteId = parentMedia?.remoteId ?: currentMediaState.mediaItemRemoteId,
                    updatedAt = currentTime,
                    completedAt = if (newStatus == "completed") currentTime else null
                )
            }
        }
        
        userEpisodeStateDao.updateSeasonWatchedTransaction(epStatesToSave, eventsToSave, updatedMediaState)
        
        repositoryScope.launch {
            syncEngine.pushBatch(epStatesToSave, eventsToSave, updatedMediaState)
        }
    }

    suspend fun removeMediaItem(mediaId: Long) {
        val item = mediaItemDao.getById(mediaId) ?: return
        val updatedItem = item.copy(deleted = true, updatedAt = System.currentTimeMillis())
        mediaItemDao.insert(updatedItem)
        
        val state = userMediaStateDao.getByMediaId(mediaId)
        if (state != null) {
            val updatedState = state.copy(
                deleted = true, 
                personalStatus = "", 
                watchlist = false, 
                favorite = false, 
                updatedAt = System.currentTimeMillis()
            )
            userMediaStateDao.insertOrUpdate(updatedState)
            repositoryScope.launch {
                syncEngine.pushUserMediaState(updatedState)
            }
        }
        
        repositoryScope.launch {
            syncEngine.pushMediaItem(updatedItem)
        }
    }

    suspend fun cleanupSpace() {
        mediaItemDao.deleteUnusedMedia()
    }
}
