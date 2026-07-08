package com.fagghino.tvvault.data.sync

import android.util.Log
import com.fagghino.tvvault.data.local.dao.*
import com.fagghino.tvvault.data.local.entity.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class FirestoreSyncEngine(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val mediaItemDao: MediaItemDao,
    private val userMediaStateDao: UserMediaStateDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val userEpisodeStateDao: UserEpisodeStateDao,
    private val watchEventDao: WatchEventDao,
    private val appSettingDao: AppSettingDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val listeners = mutableListOf<ListenerRegistration>()

    private sealed class PendingRecord {
        data class SeasonRecord(val season: Season) : PendingRecord()
        data class EpisodeRecord(val episode: Episode) : PendingRecord()
        data class UserMediaStateRecord(val state: UserMediaState) : PendingRecord()
        data class UserEpisodeStateRecord(val state: UserEpisodeState) : PendingRecord()
        data class WatchEventRecord(val event: WatchEvent) : PendingRecord()
    }

    // Mappe per la corrispondenza localId (Firestore legacy) -> remoteId (globale)
    private val mediaItemLocalToRemoteMap = ConcurrentHashMap<Long, String>()
    private val episodeLocalToRemoteMap = ConcurrentHashMap<Long, String>()

    // Code di attesa per la risoluzione della mappatura dei genitori legacy
    private val pendingMappingMediaItemOrphans = ConcurrentHashMap<Long, MutableList<PendingRecord>>()
    private val pendingMappingEpisodeOrphans = ConcurrentHashMap<Long, MutableList<PendingRecord>>()

    // Code di retry orfani (con remoteId genitore noto ma genitore non ancora nel database locale)
    private val pendingMediaItemOrphans = ConcurrentHashMap<String, MutableList<PendingRecord>>()
    private val pendingEpisodeOrphans = ConcurrentHashMap<String, MutableList<PendingRecord>>()

    fun startListening() {
        val uid = auth.currentUser?.uid ?: return
        val userRoot = firestore.collection("users").document(uid)

        Log.d("FirestoreSyncEngine", "Starting listeners for user: $uid")

        // Avvia la migrazione dello schema Firestore in background
        scope.launch {
            checkAndRunFirestoreMigration()
        }

        // Ascolta MediaItems
        listeners += userRoot.collection("mediaItems").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                try {
                    for (change in snapshot.documentChanges) {
                        if (change.document.metadata.hasPendingWrites()) continue
                        val remoteItem = change.document.toObject(MediaItem::class.java)
                        
                        // Registra la mappatura localId -> remoteId per risolvere i legacy orfani
                        mediaItemLocalToRemoteMap[remoteItem.localId] = remoteItem.remoteId
                        drainMappingMediaItemOrphans(remoteItem.localId, remoteItem.remoteId)

                        val localItem = mediaItemDao.getMediaItemByRemoteId(remoteItem.remoteId)
                        if (localItem == null || remoteItem.updatedAt > localItem.updatedAt) {
                            val localId = if (localItem != null) {
                                mediaItemDao.insert(remoteItem.copy(localId = localItem.localId))
                                localItem.localId
                            } else {
                                mediaItemDao.insert(remoteItem.copy(localId = 0))
                            }
                            // Drenare orfani in attesa di questo MediaItem
                            drainMediaItemOrphans(remoteItem.remoteId, localId)
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("FirestoreSyncEngine", "Error in mediaItems listener", ex)
                }
            }
        }

        // Ascolta UserMediaStates
        listeners += userRoot.collection("userMediaStates").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                try {
                    for (change in snapshot.documentChanges) {
                        if (change.document.metadata.hasPendingWrites()) continue
                        val remoteState = change.document.toObject(UserMediaState::class.java)
                        
                        var remoteIdParent = remoteState.mediaItemRemoteId
                        if (remoteIdParent.isBlank()) {
                            remoteIdParent = mediaItemLocalToRemoteMap[remoteState.mediaItemLocalId] ?: ""
                        }
                        
                        if (remoteIdParent.isBlank()) {
                            pendingMappingMediaItemOrphans
                                .getOrPut(remoteState.mediaItemLocalId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                .add(PendingRecord.UserMediaStateRecord(remoteState))
                            Log.w("FirestoreSyncEngine", "UserMediaState orfano per mappatura, mediaItemLocalId=${remoteState.mediaItemLocalId}")
                            continue
                        }

                        val localState = userMediaStateDao.getStateByRemoteId(remoteState.remoteId)
                        if (localState == null || remoteState.updatedAt > localState.updatedAt) {
                            val parentMedia = mediaItemDao.getMediaItemByRemoteId(remoteIdParent)
                            if (parentMedia == null) {
                                pendingMediaItemOrphans
                                    .getOrPut(remoteIdParent) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.UserMediaStateRecord(remoteState.copy(mediaItemRemoteId = remoteIdParent)))
                                Log.w("FirestoreSyncEngine", "UserMediaState orfano, mediaItemRemoteId=$remoteIdParent")
                            } else {
                                val updated = remoteState.copy(
                                    localId = localState?.localId ?: 0,
                                    mediaItemLocalId = parentMedia.localId,
                                    mediaItemRemoteId = remoteIdParent
                                )
                                userMediaStateDao.insertOrUpdate(updated)
                                if (remoteState.mediaItemRemoteId.isBlank() && remoteIdParent.isNotBlank()) {
                                    scope.launch { pushUserMediaState(updated) }
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("FirestoreSyncEngine", "Error in userMediaStates listener", ex)
                }
            }
        }

        // Ascolta Seasons
        listeners += userRoot.collection("seasons").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                try {
                    for (change in snapshot.documentChanges) {
                        if (change.document.metadata.hasPendingWrites()) continue
                        val remoteSeason = change.document.toObject(Season::class.java)

                        var remoteIdParent = remoteSeason.mediaItemRemoteId
                        if (remoteIdParent.isBlank()) {
                            remoteIdParent = mediaItemLocalToRemoteMap[remoteSeason.mediaItemLocalId] ?: ""
                        }

                        if (remoteIdParent.isBlank()) {
                            pendingMappingMediaItemOrphans
                                .getOrPut(remoteSeason.mediaItemLocalId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                .add(PendingRecord.SeasonRecord(remoteSeason))
                            Log.w("FirestoreSyncEngine", "Season orfana per mappatura, mediaItemLocalId=${remoteSeason.mediaItemLocalId}")
                            continue
                        }

                        val localSeason = seasonDao.getSeasonByRemoteId(remoteSeason.remoteId)
                        if (localSeason == null || remoteSeason.updatedAt > localSeason.updatedAt) {
                            val parentMedia = mediaItemDao.getMediaItemByRemoteId(remoteIdParent)
                            if (parentMedia == null) {
                                pendingMediaItemOrphans
                                    .getOrPut(remoteIdParent) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.SeasonRecord(remoteSeason.copy(mediaItemRemoteId = remoteIdParent)))
                                Log.w("FirestoreSyncEngine", "Season orfana, mediaItemRemoteId=$remoteIdParent")
                            } else {
                                val updated = remoteSeason.copy(
                                    localId = localSeason?.localId ?: 0,
                                    mediaItemLocalId = parentMedia.localId,
                                    mediaItemRemoteId = remoteIdParent
                                )
                                seasonDao.insert(updated)
                                if (remoteSeason.mediaItemRemoteId.isBlank() && remoteIdParent.isNotBlank()) {
                                    scope.launch { pushSeason(updated) }
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("FirestoreSyncEngine", "Error in seasons listener", ex)
                }
            }
        }

        // Ascolta Episodes
        listeners += userRoot.collection("episodes").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                try {
                    for (change in snapshot.documentChanges) {
                        if (change.document.metadata.hasPendingWrites()) continue
                        val remoteEpisode = change.document.toObject(Episode::class.java)

                        // Registra la mappatura localId -> remoteId per risolvere i legacy orfani di Episode
                        episodeLocalToRemoteMap[remoteEpisode.localId] = remoteEpisode.remoteId
                        drainMappingEpisodeOrphans(remoteEpisode.localId, remoteEpisode.remoteId)

                        var remoteIdParent = remoteEpisode.mediaItemRemoteId
                        if (remoteIdParent.isBlank()) {
                            remoteIdParent = mediaItemLocalToRemoteMap[remoteEpisode.mediaItemLocalId] ?: ""
                        }

                        if (remoteIdParent.isBlank()) {
                            pendingMappingMediaItemOrphans
                                .getOrPut(remoteEpisode.mediaItemLocalId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                .add(PendingRecord.EpisodeRecord(remoteEpisode))
                            Log.w("FirestoreSyncEngine", "Episode orfano per mappatura, mediaItemLocalId=${remoteEpisode.mediaItemLocalId}")
                            continue
                        }

                        val localEpisode = episodeDao.getEpisodeByRemoteId(remoteEpisode.remoteId)
                        if (localEpisode == null || remoteEpisode.updatedAt > localEpisode.updatedAt) {
                            val parentMedia = mediaItemDao.getMediaItemByRemoteId(remoteIdParent)
                            if (parentMedia == null) {
                                pendingMediaItemOrphans
                                    .getOrPut(remoteIdParent) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.EpisodeRecord(remoteEpisode.copy(mediaItemRemoteId = remoteIdParent)))
                                Log.w("FirestoreSyncEngine", "Episode orfano, mediaItemRemoteId=$remoteIdParent")
                            } else {
                                val insertedId = episodeDao.insert(remoteEpisode.copy(
                                    localId = localEpisode?.localId ?: 0,
                                    mediaItemLocalId = parentMedia.localId,
                                    mediaItemRemoteId = remoteIdParent
                                ))
                                val updated = remoteEpisode.copy(
                                    localId = insertedId,
                                    mediaItemLocalId = parentMedia.localId,
                                    mediaItemRemoteId = remoteIdParent
                                )
                                if (remoteEpisode.mediaItemRemoteId.isBlank() && remoteIdParent.isNotBlank()) {
                                    scope.launch { pushEpisode(updated) }
                                }
                                drainEpisodeOrphans(remoteEpisode.remoteId, insertedId)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("FirestoreSyncEngine", "Error in episodes listener", ex)
                }
            }
        }

        // Ascolta UserEpisodeStates
        listeners += userRoot.collection("userEpisodeStates").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                try {
                    for (change in snapshot.documentChanges) {
                        if (change.document.metadata.hasPendingWrites()) continue
                        val remoteState = change.document.toObject(UserEpisodeState::class.java)

                        var remoteIdParent = remoteState.episodeRemoteId
                        if (remoteIdParent.isBlank()) {
                            remoteIdParent = episodeLocalToRemoteMap[remoteState.episodeLocalId] ?: ""
                        }

                        if (remoteIdParent.isBlank()) {
                            pendingMappingEpisodeOrphans
                                .getOrPut(remoteState.episodeLocalId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                .add(PendingRecord.UserEpisodeStateRecord(remoteState))
                            Log.w("FirestoreSyncEngine", "UserEpisodeState orfano per mappatura, episodeLocalId=${remoteState.episodeLocalId}")
                            continue
                        }

                        val localState = userEpisodeStateDao.getStateByRemoteId(remoteState.remoteId)
                        if (localState == null || remoteState.updatedAt > localState.updatedAt) {
                            val parentEpisode = episodeDao.getEpisodeByRemoteId(remoteIdParent)
                            if (parentEpisode == null) {
                                pendingEpisodeOrphans
                                    .getOrPut(remoteIdParent) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.UserEpisodeStateRecord(remoteState.copy(episodeRemoteId = remoteIdParent)))
                                Log.w("FirestoreSyncEngine", "UserEpisodeState orfano, episodeRemoteId=$remoteIdParent")
                            } else {
                                val updated = remoteState.copy(
                                    localId = localState?.localId ?: 0,
                                    episodeLocalId = parentEpisode.localId,
                                    episodeRemoteId = remoteIdParent
                                )
                                userEpisodeStateDao.insertOrUpdate(updated)
                                if (remoteState.episodeRemoteId.isBlank() && remoteIdParent.isNotBlank()) {
                                    scope.launch { pushUserEpisodeState(updated) }
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("FirestoreSyncEngine", "Error in userEpisodeStates listener", ex)
                }
            }
        }

        // Ascolta WatchEvents
        listeners += userRoot.collection("watchEvents").addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            scope.launch {
                try {
                    for (change in snapshot.documentChanges) {
                        if (change.document.metadata.hasPendingWrites()) continue
                        val remoteEvent = change.document.toObject(WatchEvent::class.java)

                        var remoteIdParentMedia = remoteEvent.mediaItemRemoteId
                        if (remoteIdParentMedia.isBlank()) {
                            remoteIdParentMedia = mediaItemLocalToRemoteMap[remoteEvent.mediaItemLocalId] ?: ""
                        }
                        
                        var remoteIdParentEpisode = remoteEvent.episodeRemoteId
                        if (remoteIdParentEpisode.isBlank() && remoteEvent.episodeLocalId != null) {
                            remoteIdParentEpisode = episodeLocalToRemoteMap[remoteEvent.episodeLocalId] ?: ""
                        }

                        if (remoteIdParentMedia.isBlank() || (remoteEvent.episodeLocalId != null && remoteIdParentEpisode.isBlank())) {
                            if (remoteIdParentMedia.isBlank()) {
                                pendingMappingMediaItemOrphans
                                    .getOrPut(remoteEvent.mediaItemLocalId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.WatchEventRecord(remoteEvent))
                            } else {
                                pendingMappingEpisodeOrphans
                                    .getOrPut(remoteEvent.episodeLocalId!!) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.WatchEventRecord(remoteEvent.copy(mediaItemRemoteId = remoteIdParentMedia)))
                            }
                            Log.w("FirestoreSyncEngine", "WatchEvent orfano per mappatura")
                            continue
                        }

                        val localEvent = watchEventDao.getWatchEventByRemoteId(remoteEvent.remoteId)
                        if (localEvent == null || remoteEvent.updatedAt > localEvent.updatedAt) {
                            val parentMedia = mediaItemDao.getMediaItemByRemoteId(remoteIdParentMedia)
                            if (parentMedia == null) {
                                pendingMediaItemOrphans
                                    .getOrPut(remoteIdParentMedia) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.WatchEventRecord(remoteEvent.copy(
                                        mediaItemRemoteId = remoteIdParentMedia,
                                        episodeRemoteId = remoteIdParentEpisode
                                    )))
                                Log.w("FirestoreSyncEngine", "WatchEvent orfano per MediaItem, mediaItemRemoteId=$remoteIdParentMedia")
                            } else {
                                if (remoteIdParentEpisode.isNotBlank()) {
                                    val parentEpisode = episodeDao.getEpisodeByRemoteId(remoteIdParentEpisode)
                                    if (parentEpisode == null) {
                                        pendingEpisodeOrphans
                                            .getOrPut(remoteIdParentEpisode) { java.util.Collections.synchronizedList(mutableListOf()) }
                                            .add(PendingRecord.WatchEventRecord(remoteEvent.copy(
                                                mediaItemLocalId = parentMedia.localId,
                                                mediaItemRemoteId = remoteIdParentMedia,
                                                episodeRemoteId = remoteIdParentEpisode
                                            )))
                                        Log.w("FirestoreSyncEngine", "WatchEvent orfano per Episode, episodeRemoteId=$remoteIdParentEpisode")
                                    } else {
                                        val updated = remoteEvent.copy(
                                            localId = localEvent?.localId ?: 0,
                                            mediaItemLocalId = parentMedia.localId,
                                            mediaItemRemoteId = remoteIdParentMedia,
                                            episodeLocalId = parentEpisode.localId,
                                            episodeRemoteId = remoteIdParentEpisode
                                        )
                                        watchEventDao.insert(updated)
                                        if ((remoteEvent.mediaItemRemoteId.isBlank() && remoteIdParentMedia.isNotBlank()) ||
                                            (remoteEvent.episodeRemoteId.isBlank() && remoteIdParentEpisode.isNotBlank())) {
                                            scope.launch { pushWatchEvent(updated) }
                                        }
                                    }
                                } else {
                                    // Film
                                    val updated = remoteEvent.copy(
                                        localId = localEvent?.localId ?: 0,
                                        mediaItemLocalId = parentMedia.localId,
                                        mediaItemRemoteId = remoteIdParentMedia,
                                        episodeLocalId = null,
                                        episodeRemoteId = ""
                                    )
                                    watchEventDao.insert(updated)
                                    if (remoteEvent.mediaItemRemoteId.isBlank() && remoteIdParentMedia.isNotBlank()) {
                                        scope.launch { pushWatchEvent(updated) }
                                    }
                                }
                            }
                        }
                    }
                } catch (ex: Exception) {
                    Log.e("FirestoreSyncEngine", "Error in watchEvents listener", ex)
                }
            }
        }
    }

    private suspend fun checkAndRunFirestoreMigration() {
        val migrationKey = "firestore_migration_v3_done"
        if (appSettingDao.getValue(migrationKey) == "true") return
        
        Log.d("FirestoreSyncEngine", "Starting one-time Firestore schema migration to version 3...")
        
        val uid = auth.currentUser?.uid ?: return
        val userRoot = firestore.collection("users").document(uid)
        
        try {
            val allOps = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Any>>()

            mediaItemDao.getAll().filter { it.remoteId.isNotBlank() }.forEach { item ->
                allOps.add(userRoot.collection("mediaItems").document(item.remoteId) to item)
            }
            
            userMediaStateDao.getAll().filter { it.remoteId.isNotBlank() }.forEach { state ->
                allOps.add(userRoot.collection("userMediaStates").document(state.remoteId) to state)
            }
            
            seasonDao.getAll().filter { it.remoteId.isNotBlank() }.forEach { season ->
                allOps.add(userRoot.collection("seasons").document(season.remoteId) to season)
            }
            
            episodeDao.getAll().filter { it.remoteId.isNotBlank() }.forEach { ep ->
                allOps.add(userRoot.collection("episodes").document(ep.remoteId) to ep)
            }
            
            userEpisodeStateDao.getAll().filter { it.remoteId.isNotBlank() }.forEach { state ->
                allOps.add(userRoot.collection("userEpisodeStates").document(state.remoteId) to state)
            }
            
            watchEventDao.getAll().filter { it.remoteId.isNotBlank() }.forEach { event ->
                allOps.add(userRoot.collection("watchEvents").document(event.remoteId) to event)
            }

            allOps.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { (ref, data) ->
                    batch.set(ref, data, SetOptions.merge())
                }
                batch.commit().await()
            }
            
            appSettingDao.insert(AppSetting(migrationKey, "true"))
            Log.d("FirestoreSyncEngine", "Firestore schema migration to version 3 completed successfully.")
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error during Firestore schema migration", e)
        }
    }

    private suspend fun drainMappingMediaItemOrphans(originalLocalId: Long, parentRemoteId: String) {
        val orphans = pendingMappingMediaItemOrphans.remove(originalLocalId) ?: return
        val parentMedia = mediaItemDao.getMediaItemByRemoteId(parentRemoteId)
        for (record in orphans) {
            when (record) {
                is PendingRecord.SeasonRecord -> {
                    val s = record.season
                    if (parentMedia == null) {
                        pendingMediaItemOrphans
                            .getOrPut(parentRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                            .add(PendingRecord.SeasonRecord(s.copy(mediaItemRemoteId = parentRemoteId)))
                    } else {
                        val local = seasonDao.getSeasonByRemoteId(s.remoteId)
                        val updated = s.copy(
                            localId = local?.localId ?: 0,
                            mediaItemLocalId = parentMedia.localId,
                            mediaItemRemoteId = parentRemoteId
                        )
                        seasonDao.insert(updated)
                        pushSeason(updated)
                    }
                }
                is PendingRecord.EpisodeRecord -> {
                    val e = record.episode
                    if (parentMedia == null) {
                        pendingMediaItemOrphans
                            .getOrPut(parentRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                            .add(PendingRecord.EpisodeRecord(e.copy(mediaItemRemoteId = parentRemoteId)))
                    } else {
                        val local = episodeDao.getEpisodeByRemoteId(e.remoteId)
                        val insertedId = episodeDao.insert(e.copy(
                            localId = local?.localId ?: 0,
                            mediaItemLocalId = parentMedia.localId,
                            mediaItemRemoteId = parentRemoteId
                        ))
                        val updated = e.copy(localId = insertedId, mediaItemLocalId = parentMedia.localId, mediaItemRemoteId = parentRemoteId)
                        pushEpisode(updated)
                        drainEpisodeOrphans(e.remoteId, insertedId)
                    }
                }
                is PendingRecord.UserMediaStateRecord -> {
                    val s = record.state
                    if (parentMedia == null) {
                        pendingMediaItemOrphans
                            .getOrPut(parentRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                            .add(PendingRecord.UserMediaStateRecord(s.copy(mediaItemRemoteId = parentRemoteId)))
                    } else {
                        val local = userMediaStateDao.getStateByRemoteId(s.remoteId)
                        val updated = s.copy(
                            localId = local?.localId ?: 0,
                            mediaItemLocalId = parentMedia.localId,
                            mediaItemRemoteId = parentRemoteId
                        )
                        userMediaStateDao.insertOrUpdate(updated)
                        pushUserMediaState(updated)
                    }
                }
                is PendingRecord.WatchEventRecord -> {
                    val ev = record.event
                    if (parentMedia == null) {
                        pendingMediaItemOrphans
                            .getOrPut(parentRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                            .add(PendingRecord.WatchEventRecord(ev.copy(mediaItemRemoteId = parentRemoteId)))
                    } else {
                        val local = watchEventDao.getWatchEventByRemoteId(ev.remoteId)
                        val resolvedEpisodeRemoteId = if (ev.episodeLocalId != null) {
                            episodeLocalToRemoteMap[ev.episodeLocalId] ?: ""
                        } else ""
                        
                        if (ev.episodeLocalId != null && resolvedEpisodeRemoteId.isBlank()) {
                            pendingMappingEpisodeOrphans
                                .getOrPut(ev.episodeLocalId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                .add(PendingRecord.WatchEventRecord(ev.copy(mediaItemLocalId = parentMedia.localId, mediaItemRemoteId = parentRemoteId)))
                        } else {
                            val parentEpisode = if (resolvedEpisodeRemoteId.isNotBlank()) {
                                episodeDao.getEpisodeByRemoteId(resolvedEpisodeRemoteId)
                            } else null
                            
                            if (ev.episodeLocalId != null && parentEpisode == null) {
                                pendingEpisodeOrphans
                                    .getOrPut(resolvedEpisodeRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                    .add(PendingRecord.WatchEventRecord(ev.copy(mediaItemLocalId = parentMedia.localId, mediaItemRemoteId = parentRemoteId)))
                            } else {
                                val updated = ev.copy(
                                    localId = local?.localId ?: 0,
                                    mediaItemLocalId = parentMedia.localId,
                                    mediaItemRemoteId = parentRemoteId,
                                    episodeLocalId = parentEpisode?.localId,
                                    episodeRemoteId = resolvedEpisodeRemoteId
                                )
                                watchEventDao.insert(updated)
                                pushWatchEvent(updated)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun drainMappingEpisodeOrphans(originalLocalId: Long, parentRemoteId: String) {
        val orphans = pendingMappingEpisodeOrphans.remove(originalLocalId) ?: return
        val parentEpisode = episodeDao.getEpisodeByRemoteId(parentRemoteId)
        for (record in orphans) {
            when (record) {
                is PendingRecord.UserEpisodeStateRecord -> {
                    val s = record.state
                    if (parentEpisode == null) {
                        pendingEpisodeOrphans
                            .getOrPut(parentRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                            .add(PendingRecord.UserEpisodeStateRecord(s.copy(episodeRemoteId = parentRemoteId)))
                    } else {
                        val local = userEpisodeStateDao.getStateByRemoteId(s.remoteId)
                        val updated = s.copy(
                            localId = local?.localId ?: 0,
                            episodeLocalId = parentEpisode.localId,
                            episodeRemoteId = parentRemoteId
                        )
                        userEpisodeStateDao.insertOrUpdate(updated)
                        pushUserEpisodeState(updated)
                    }
                }
                is PendingRecord.WatchEventRecord -> {
                    val ev = record.event
                    if (parentEpisode == null) {
                        pendingEpisodeOrphans
                            .getOrPut(parentRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                            .add(PendingRecord.WatchEventRecord(ev.copy(episodeRemoteId = parentRemoteId)))
                    } else {
                        val local = watchEventDao.getWatchEventByRemoteId(ev.remoteId)
                        val updated = ev.copy(
                            localId = local?.localId ?: 0,
                            episodeLocalId = parentEpisode.localId,
                            episodeRemoteId = parentRemoteId
                        )
                        watchEventDao.insert(updated)
                        pushWatchEvent(updated)
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun drainMediaItemOrphans(parentRemoteId: String, parentLocalId: Long) {
        val orphans = pendingMediaItemOrphans.remove(parentRemoteId) ?: return
        for (record in orphans) {
            when (record) {
                is PendingRecord.SeasonRecord -> {
                    val s = record.season
                    val local = seasonDao.getSeasonByRemoteId(s.remoteId)
                    seasonDao.insert(s.copy(
                        localId = local?.localId ?: 0,
                        mediaItemLocalId = parentLocalId
                    ))
                }
                is PendingRecord.EpisodeRecord -> {
                    val e = record.episode
                    val local = episodeDao.getEpisodeByRemoteId(e.remoteId)
                    val insertedId = episodeDao.insert(e.copy(
                        localId = local?.localId ?: 0,
                        mediaItemLocalId = parentLocalId
                    ))
                    drainEpisodeOrphans(e.remoteId, insertedId)
                }
                is PendingRecord.UserMediaStateRecord -> {
                    val s = record.state
                    val local = userMediaStateDao.getStateByRemoteId(s.remoteId)
                    userMediaStateDao.insertOrUpdate(s.copy(
                        localId = local?.localId ?: 0,
                        mediaItemLocalId = parentLocalId
                    ))
                }
                is PendingRecord.WatchEventRecord -> {
                    val e = record.event
                    val local = watchEventDao.getWatchEventByRemoteId(e.remoteId)
                    if (e.episodeRemoteId.isNotBlank()) {
                        val parentEpisode = episodeDao.getEpisodeByRemoteId(e.episodeRemoteId)
                        if (parentEpisode == null) {
                            pendingEpisodeOrphans
                                .getOrPut(e.episodeRemoteId) { java.util.Collections.synchronizedList(mutableListOf()) }
                                .add(PendingRecord.WatchEventRecord(e.copy(mediaItemLocalId = parentLocalId)))
                        } else {
                            watchEventDao.insert(e.copy(
                                localId = local?.localId ?: 0,
                                mediaItemLocalId = parentLocalId,
                                episodeLocalId = parentEpisode.localId
                            ))
                        }
                    } else {
                        watchEventDao.insert(e.copy(
                            localId = local?.localId ?: 0,
                            mediaItemLocalId = parentLocalId,
                            episodeLocalId = null
                        ))
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun drainEpisodeOrphans(episodeRemoteId: String, episodeLocalId: Long) {
        val orphans = pendingEpisodeOrphans.remove(episodeRemoteId) ?: return
        for (record in orphans) {
            when (record) {
                is PendingRecord.UserEpisodeStateRecord -> {
                    val s = record.state
                    val local = userEpisodeStateDao.getStateByRemoteId(s.remoteId)
                    userEpisodeStateDao.insertOrUpdate(s.copy(
                        localId = local?.localId ?: 0,
                        episodeLocalId = episodeLocalId
                    ))
                }
                is PendingRecord.WatchEventRecord -> {
                    val e = record.event
                    val local = watchEventDao.getWatchEventByRemoteId(e.remoteId)
                    watchEventDao.insert(e.copy(
                        localId = local?.localId ?: 0,
                        episodeLocalId = episodeLocalId
                    ))
                }
                else -> {}
            }
        }
    }

    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
        Log.d("FirestoreSyncEngine", "Stopped all listeners")
    }

    suspend fun pushBatch(
        userEpisodeStates: List<UserEpisodeState> = emptyList(),
        watchEvents: List<WatchEvent> = emptyList(),
        userMediaState: UserMediaState? = null
    ) {
        try {
            val uid = auth.currentUser?.uid ?: return
            val userRoot = firestore.collection("users").document(uid)
            
            val allOperations = mutableListOf<suspend (WriteBatch) -> Unit>()
            
            userEpisodeStates.forEach { state ->
                if (state.remoteId.isNotBlank()) {
                    allOperations.add { batch ->
                        val docRef = userRoot.collection("userEpisodeStates").document(state.remoteId)
                        batch.set(docRef, state, SetOptions.merge())
                    }
                }
            }
            
            watchEvents.forEach { event ->
                if (event.remoteId.isNotBlank()) {
                    allOperations.add { batch ->
                        val docRef = userRoot.collection("watchEvents").document(event.remoteId)
                        batch.set(docRef, event, SetOptions.merge())
                    }
                }
            }
            
            if (userMediaState != null && userMediaState.remoteId.isNotBlank()) {
                allOperations.add { batch ->
                    val docRef = userRoot.collection("userMediaStates").document(userMediaState.remoteId)
                    batch.set(docRef, userMediaState, SetOptions.merge())
                }
            }
            
            if (allOperations.isEmpty()) return
            
            allOperations.chunked(450).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { op -> op(batch) }
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error in pushBatch: ${e.message}", e)
        }
    }

    suspend fun pushMediaItem(item: MediaItem) {
        if (item.remoteId.isBlank()) return
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users").document(uid).collection("mediaItems").document(item.remoteId)
                .set(item, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error pushing mediaItem: ${item.remoteId}", e)
        }
    }

    suspend fun pushUserMediaState(state: UserMediaState) {
        if (state.remoteId.isBlank()) return
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users").document(uid).collection("userMediaStates").document(state.remoteId)
                .set(state, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error pushing userMediaState: ${state.remoteId}", e)
        }
    }

    suspend fun pushSeason(season: Season) {
        if (season.remoteId.isBlank()) return
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users").document(uid).collection("seasons").document(season.remoteId)
                .set(season, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error pushing season: ${season.remoteId}", e)
        }
    }

    suspend fun pushEpisode(episode: Episode) {
        if (episode.remoteId.isBlank()) return
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users").document(uid).collection("episodes").document(episode.remoteId)
                .set(episode, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error pushing episode: ${episode.remoteId}", e)
        }
    }

    suspend fun pushUserEpisodeState(state: UserEpisodeState) {
        if (state.remoteId.isBlank()) return
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users").document(uid).collection("userEpisodeStates").document(state.remoteId)
                .set(state, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error pushing userEpisodeState: ${state.remoteId}", e)
        }
    }

    suspend fun pushWatchEvent(event: WatchEvent) {
        if (event.remoteId.isBlank()) return
        try {
            val uid = auth.currentUser?.uid ?: return
            firestore.collection("users").document(uid).collection("watchEvents").document(event.remoteId)
                .set(event, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("FirestoreSyncEngine", "Error pushing watchEvent: ${event.remoteId}", e)
        }
    }
}
