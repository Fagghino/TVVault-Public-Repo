@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.fagghino.tvvault.data.backup

import android.content.Context
import android.net.Uri
import com.fagghino.tvvault.data.local.TVVaultDatabase
import com.fagghino.tvvault.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.lang.Exception

@Serializable
data class TVVaultBackup(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaItems: List<MediaItem>,
    val userMediaStates: List<UserMediaState>,
    val seasons: List<Season>,
    val episodes: List<Episode>,
    val userEpisodeStates: List<UserEpisodeState>,
    val watchEvents: List<WatchEvent>,
    val settings: List<AppSetting>
)

class BackupManager(
    private val context: Context,
    private val database: TVVaultDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun exportBackup(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = TVVaultBackup(
                mediaItems = database.mediaItemDao().getAll(),
                userMediaStates = database.userMediaStateDao().getAll(),
                seasons = database.seasonDao().getAll(),
                episodes = database.episodeDao().getAll(),
                userEpisodeStates = database.userEpisodeStateDao().getAll(),
                watchEvents = database.watchEventDao().getAll(),
                settings = database.appSettingDao().getAll()
            )

            val serializedJson = json.encodeToString(backup)

            context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                    writer.write(serializedJson)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportBackupToZip(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipStream ->
                    // 1. media_items.csv
                    zipStream.putNextEntry(ZipEntry("media_items.csv"))
                    val mediaItems = database.mediaItemDao().getAll()
                    zipStream.write("localId,provider,providerId,mediaType,title,originalTitle,releaseDate,runtime\n".toByteArray())
                    mediaItems.forEach {
                        zipStream.write("${it.localId},${it.provider},${it.providerId},${it.mediaType},\"${it.title.replace("\"", "\"\"")}\",\"${it.originalTitle.replace("\"", "\"\"")}\",${it.releaseDate},${it.runtime}\n".toByteArray())
                    }
                    zipStream.closeEntry()

                    // 2. user_media_states.csv
                    zipStream.putNextEntry(ZipEntry("user_media_states.csv"))
                    val states = database.userMediaStateDao().getAll()
                    zipStream.write("localId,mediaItemLocalId,personalStatus,rating,favorite,watchlist,notes\n".toByteArray())
                    states.forEach {
                        val escapedNotes = (it.notes ?: "").replace("\"", "\"\"")
                        zipStream.write("${it.localId},${it.mediaItemLocalId},${it.personalStatus},${it.rating},${it.favorite},${it.watchlist},\"$escapedNotes\"\n".toByteArray())
                    }
                    zipStream.closeEntry()

                    // 3. watch_events.csv
                    zipStream.putNextEntry(ZipEntry("watch_events.csv"))
                    val events = database.watchEventDao().getAll()
                    zipStream.write("localId,mediaItemLocalId,episodeLocalId,watchedAt,eventType\n".toByteArray())
                    events.forEach {
                        zipStream.write("${it.localId},${it.mediaItemLocalId},${it.episodeLocalId},${it.watchedAt},${it.eventType}\n".toByteArray())
                    }
                    zipStream.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importBackup(fileUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val serializedJson = contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext false

            val backup = json.decodeFromString<TVVaultBackup>(serializedJson)

            if (backup.version > 1) return@withContext false

            database.runInTransaction {
                runBlockingInTransaction {
                    database.clearAllTables()

                    backup.mediaItems.forEach { database.mediaItemDao().insert(it) }
                    backup.userMediaStates.forEach { database.userMediaStateDao().insertOrUpdate(it) }
                    backup.seasons.forEach { database.seasonDao().insert(it) }
                    backup.episodes.forEach { database.episodeDao().insert(it) }
                    backup.userEpisodeStates.forEach { database.userEpisodeStateDao().insertOrUpdate(it) }
                    backup.watchEvents.forEach { database.watchEventDao().insert(it) }
                    backup.settings.forEach { database.appSettingDao().insert(it) }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun runBlockingInTransaction(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking {
            block()
        }
    }
}
