package com.fagghino.tvvault.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fagghino.tvvault.data.backup.BackupManager
import com.fagghino.tvvault.data.importer.TvTimeImporter
import com.fagghino.tvvault.data.local.entity.*
import com.fagghino.tvvault.data.repository.MediaRepository
import com.fagghino.tvvault.ui.screens.UpcomingEpisodeItem
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.fagghino.tvvault.data.auth.AuthManager
import com.fagghino.tvvault.data.sync.FirestoreSyncEngine

class MediaViewModel(
    private val repository: MediaRepository,
    private val tvTimeImporter: TvTimeImporter,
    private val backupManager: BackupManager,
    private val authManager: AuthManager,
    private val syncEngine: FirestoreSyncEngine
) : ViewModel() {

    val shows: StateFlow<List<MediaItem>> = repository.observeShows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movies: StateFlow<List<MediaItem>> = repository.observeMovies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Joined shows+state for grouped display
    val showsWithState: StateFlow<List<Pair<MediaItem, UserMediaState?>>> =
        repository.observeShowsWithState().map { list ->
            list.map { it.mediaItem to it.userMediaState }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun formatMinutes(minutes: Int): String {
        val days = minutes / (24 * 60)
        val hours = (minutes % (24 * 60)) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> "${days}g ${hours}o"
            hours > 0 -> "${hours}o ${mins}m"
            else -> "${mins}m"
        }
    }

    val tvShowStats: StateFlow<Pair<Int, String>> = repository.observeGlobalWatchedEpisodesCount()
        .map { count ->
            val totalMinutes = count * 45 // 45 min per episode
            count to formatMinutes(totalMinutes)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to "0m")

    val movieStats: StateFlow<Pair<Int, String>> = repository.observeMediaByStatus("movie", "completed")
        .map { list ->
            val count = list.size
            val totalMinutes = list.sumOf { it.runtime ?: 0 }
            count to formatMinutes(totalMinutes)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0 to "0m")

    fun observeMediaState(mediaId: Long): Flow<UserMediaState?> = repository.observeMediaState(mediaId)

    fun observeMediaByStatus(mediaType: String, status: String): Flow<List<MediaItem>> =
        repository.observeMediaByStatus(mediaType, status)

    fun observeSeasons(mediaId: Long): Flow<List<Season>> = repository.observeSeasons(mediaId)

    fun observeEpisodes(mediaId: Long, seasonNumber: Int): Flow<List<Episode>> = 
        repository.observeEpisodes(mediaId, seasonNumber)

    fun observeAllEpisodes(mediaId: Long): Flow<List<Episode>> = repository.observeAllEpisodes(mediaId)

    fun observeWatchedEpisodes(mediaId: Long): Flow<List<UserEpisodeState>> = repository.observeWatchedEpisodes(mediaId)

    suspend fun getMediaItemById(id: Long): MediaItem? {
        return repository.getMediaItemById(id)
    }


    private val _profileImageUri = MutableStateFlow("")
    val profileImageUri = _profileImageUri.asStateFlow()

    private val _profileBannerUri = MutableStateFlow("")
    val profileBannerUri = _profileBannerUri.asStateFlow()

    // "system" | "light" | "dark"
    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Group order: comma-separated status keys, e.g. "watching,watchlist,dropped,completed"
    private val _groupOrder = MutableStateFlow("watching,watchlist,dropped,completed")
    val groupOrder: StateFlow<String> = _groupOrder.asStateFlow()

    init {
        viewModelScope.launch {
            _profileImageUri.value = repository.getSetting("profile_image_uri") ?: ""
            _profileBannerUri.value = repository.getSetting("profile_banner_uri") ?: ""
            _themeMode.value = repository.getSetting("theme_mode") ?: "system"
            _groupOrder.value = repository.getSetting("group_order") ?: "watching,watchlist,dropped,completed"
        }
    }

    fun saveThemeMode(mode: String) {
        viewModelScope.launch {
            repository.saveSetting("theme_mode", mode)
            _themeMode.value = mode
        }
    }

    fun saveGroupOrder(order: String) {
        viewModelScope.launch {
            repository.saveSetting("group_order", order)
            _groupOrder.value = order
        }
    }

    fun saveProfileImageUri(uri: String) {
        viewModelScope.launch {
            repository.saveSetting("profile_image_uri", uri)
            _profileImageUri.value = uri
        }
    }

    fun saveProfileBannerUri(uri: String) {
        viewModelScope.launch {
            repository.saveSetting("profile_banner_uri", uri)
            _profileBannerUri.value = uri
        }
    }


    // TMDb Search logic
    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun searchOnline(query: String, mediaType: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val results = if (mediaType == "tv") {
                repository.searchTvShowsOnline(query)
            } else {
                repository.searchMoviesOnline(query)
            }
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun addMediaToLibrary(item: MediaItem) {
        viewModelScope.launch {
            repository.addMediaToLibrary(item)
        }
    }

    // TV Time Importer Integration
    val importJobs: StateFlow<List<ImportJob>> = repository.observeImportJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun observeCandidates(jobId: Long): Flow<List<ImportMatchCandidate>> = 
        repository.observeCandidatesForJob(jobId)

    fun importFollowedShows(uri: Uri) {
        viewModelScope.launch {
            tvTimeImporter.importFollowedShows(uri)
        }
    }

    fun reconcileCandidate(candidateId: Long, accept: Boolean) {
        viewModelScope.launch {
            repository.reconcileCandidate(candidateId, accept)
        }
    }

    // Backup & Restore operations
    fun exportBackup(uri: Uri, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupManager.exportBackup(uri)
            callback(success)
        }
    }

    fun exportBackupToZip(uri: Uri, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupManager.exportBackupToZip(uri)
            callback(success)
        }
    }

    fun importBackup(uri: Uri, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupManager.importBackup(uri)
            callback(success)
        }
    }

    fun cleanupSpace(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                repository.cleanupSpace()
                callback(true)
            } catch (e: Exception) {
                callback(false)
            }
        }
    }

    // User interactions
    fun toggleFavorite(mediaId: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            val currentState = repository.observeMediaState(mediaId).first()
            val newState = currentState?.copy(favorite = isFavorite, updatedAt = System.currentTimeMillis())
                ?: UserMediaState(mediaItemLocalId = mediaId, personalStatus = "watchlist", favorite = isFavorite)
            repository.updateMediaState(newState)
        }
    }

    fun updateStatus(mediaId: Long, status: String) {
        viewModelScope.launch {
            val currentState = repository.observeMediaState(mediaId).first()
            val newState = currentState?.copy(
                personalStatus = status,
                updatedAt = System.currentTimeMillis(),
                completedAt = if (status == "completed") System.currentTimeMillis() else currentState.completedAt,
                startedAt = if (status == "watching" && currentState.startedAt == null) System.currentTimeMillis() else currentState.startedAt
            ) ?: UserMediaState(mediaItemLocalId = mediaId, personalStatus = status)
            repository.updateMediaState(newState)
        }
    }

    fun updateRatingAndNotes(mediaId: Long, rating: Float?, notes: String?) {
        viewModelScope.launch {
            val currentState = repository.observeMediaState(mediaId).first()
            val newState = currentState?.copy(
                rating = rating,
                notes = notes,
                updatedAt = System.currentTimeMillis()
            ) ?: UserMediaState(mediaItemLocalId = mediaId, personalStatus = "watchlist", rating = rating, notes = notes)
            repository.updateMediaState(newState)
        }
    }

    fun setEpisodeWatched(episodeId: Long, watched: Boolean, mediaItemId: Long) {
        viewModelScope.launch {
            repository.setEpisodeWatched(episodeId, watched, mediaItemId)
        }
    }

    fun setSeasonWatched(mediaId: Long, seasonNumber: Int, watched: Boolean) {
        viewModelScope.launch {
            repository.setSeasonWatched(mediaId, seasonNumber, watched)
        }
    }

    // ─── Upcoming Episodes ───────────────────────────────────────────────
    private val _upcomingEpisodes = MutableStateFlow<List<UpcomingEpisodeItem>>(emptyList())
    val upcomingEpisodes: StateFlow<List<UpcomingEpisodeItem>> = _upcomingEpisodes.asStateFlow()

    private val _isLoadingUpcoming = MutableStateFlow(false)
    val isLoadingUpcoming: StateFlow<Boolean> = _isLoadingUpcoming.asStateFlow()

    fun loadUpcomingEpisodes() {
        viewModelScope.launch {
            _isLoadingUpcoming.value = true
            val result = mutableListOf<UpcomingEpisodeItem>()
            val today = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val cutoff = today.plusDays(60) // Look 60 days ahead

            // Only look at shows that are being watched or not started
            val activeShows = repository.observeShows().first()
            activeShows.forEach { show ->
                try {
                    val state = repository.observeMediaState(show.localId).first()
                    val status = state?.personalStatus ?: "watchlist"
                    if (status == "watching" || status == "watchlist") {
                        // Fetch latest season info from TMDb to get air dates
                        show.providerId.toIntOrNull()?.let { tmdbId ->
                            val details = repository.getTvShowDetails(tmdbId)
                            val lastSeason = details?.numberOfSeasons ?: 0
                            if (lastSeason > 0) {
                                val season = repository.getSeasonWithAirDates(tmdbId, lastSeason)
                                season?.episodes?.forEach { ep ->
                                    val airDateStr = ep.airDate ?: return@forEach
                                    if (airDateStr.isBlank()) return@forEach
                                    val airDate = runCatching {
                                        LocalDate.parse(airDateStr, formatter)
                                    }.getOrNull() ?: return@forEach
                                    if (airDate >= today && airDate <= cutoff) {
                                        result.add(
                                            UpcomingEpisodeItem(
                                                show = show,
                                                seasonNumber = lastSeason,
                                                episodeNumber = ep.episodeNumber,
                                                episodeName = ep.name,
                                                airDate = airDate,
                                                posterPath = show.posterPath
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip this show silently
                }
            }
            _upcomingEpisodes.value = result.sortedWith(
                compareBy({ it.airDate }, { it.show.title })
            )
            _isLoadingUpcoming.value = false
        }
    }

    // ─── Authentication ───────────────────────────────────────────────
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        // Load initial state if already logged in
        authManager.getCurrentUserUid()?.let {
            _userEmail.value = "Utente Autenticato" // Potremmo salvare l'email da qualche parte se serve
        }
    }

    fun login(callback: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            val result = authManager.signIn()
            result.onSuccess { user ->
                _userEmail.value = user.displayName ?: user.email ?: "Utente Autenticato"
                syncEngine.startListening()
                callback(true, null)
            }.onFailure {
                callback(false, it.message)
            }
            _isSyncing.value = false
        }
    }

    fun logout() {
        authManager.signOut()
        syncEngine.stopListening()
        _userEmail.value = null
    }
}

class MediaViewModelFactory(
    private val repository: MediaRepository,
    private val tvTimeImporter: TvTimeImporter,
    private val backupManager: BackupManager,
    private val authManager: AuthManager,
    private val syncEngine: FirestoreSyncEngine
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MediaViewModel(repository, tvTimeImporter, backupManager, authManager, syncEngine) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
