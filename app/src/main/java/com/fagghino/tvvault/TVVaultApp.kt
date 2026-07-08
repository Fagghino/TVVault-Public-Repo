package com.fagghino.tvvault

import android.app.Application
import com.fagghino.tvvault.data.backup.BackupManager
import com.fagghino.tvvault.data.importer.TvTimeImporter
import com.fagghino.tvvault.data.local.TVVaultDatabase
import com.fagghino.tvvault.data.remote.TmdbClient
import com.fagghino.tvvault.data.repository.MediaRepository
import com.fagghino.tvvault.data.auth.AuthManager
import com.fagghino.tvvault.data.sync.FirestoreSyncEngine

class TVVaultApp : Application() {
    val database by lazy { TVVaultDatabase.getDatabase(this) }
    val tmdbService by lazy { TmdbClient.createService(this) }
    
    val authManager by lazy { AuthManager(this) }
    
    val syncEngine by lazy {
        FirestoreSyncEngine(
            mediaItemDao = database.mediaItemDao(),
            userMediaStateDao = database.userMediaStateDao(),
            seasonDao = database.seasonDao(),
            episodeDao = database.episodeDao(),
            userEpisodeStateDao = database.userEpisodeStateDao(),
            watchEventDao = database.watchEventDao(),
            appSettingDao = database.appSettingDao()
        )
    }

    val repository by lazy {
        MediaRepository(
            mediaItemDao = database.mediaItemDao(),
            userMediaStateDao = database.userMediaStateDao(),
            seasonDao = database.seasonDao(),
            episodeDao = database.episodeDao(),
            userEpisodeStateDao = database.userEpisodeStateDao(),
            watchEventDao = database.watchEventDao(),
            importJobDao = database.importJobDao(),
            importMatchCandidateDao = database.importMatchCandidateDao(),
            appSettingDao = database.appSettingDao(),
            tmdbService = tmdbService,
            syncEngine = syncEngine
        )
    }
    val tvTimeImporter by lazy { TvTimeImporter(this, repository) }
    val backupManager by lazy { BackupManager(this, database) }

    override fun onCreate() {
        super.onCreate()
        if (authManager.getCurrentUserUid() != null) {
            syncEngine.startListening()
        }
    }
}
