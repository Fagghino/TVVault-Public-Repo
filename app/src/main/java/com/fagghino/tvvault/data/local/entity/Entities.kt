@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.fagghino.tvvault.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "media_items",
    indices = [Index(value = ["provider", "providerId"], unique = true)]
)
data class MediaItem(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val provider: String = "tmdb",
    val providerId: String = "",
    val imdbId: String? = null,
    val mediaType: String = "", // "movie" or "tv"
    val title: String = "",
    val originalTitle: String = "",
    val overview: String = "",
    val releaseDate: String?, // release date or first air date
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val genreIds: String = "", // Comma separated list of IDs, e.g. "28,12"
    val originCountry: String? = null,
    val originalLanguage: String? = null,
    val status: String? = null,
    val runtime: Int? = null,
    val seasonCount: Int? = null,
    val episodeCount: Int? = null,
    val lastMetadataSyncAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "user_media_states",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["localId"],
            childColumns = ["mediaItemLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaItemLocalId"], unique = true)]
)
data class UserMediaState(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val mediaItemLocalId: Long = 0,
    val mediaItemRemoteId: String = "",
    val personalStatus: String = "", // "watchlist", "watching", "completed", "paused", "dropped"
    val rating: Float? = null,
    val favorite: Boolean = false,
    val watchlist: Boolean = false,
    val notes: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val lastWatchedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceImport: String? = null
)

@Serializable
@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["localId"],
            childColumns = ["mediaItemLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaItemLocalId", "seasonNumber"], unique = true)]
)
data class Season(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val mediaItemLocalId: Long = 0,
    val mediaItemRemoteId: String = "",
    val seasonNumber: Int = 0,
    val name: String = "",
    val episodeCount: Int = 0,
    val airDate: String? = null,
    val posterPath: String? = null
)

@Serializable
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["localId"],
            childColumns = ["mediaItemLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaItemLocalId", "seasonNumber", "episodeNumber"], unique = true)
    ]
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val mediaItemLocalId: Long = 0,
    val mediaItemRemoteId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val externalEpisodeId: String? = null,
    val name: String = "",
    val airDate: String? = null,
    val runtime: Int? = null,
    val overview: String? = null
)

@Serializable
@Entity(
    tableName = "user_episode_states",
    foreignKeys = [
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["localId"],
            childColumns = ["episodeLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["episodeLocalId"], unique = true)]
)
data class UserEpisodeState(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val episodeLocalId: Long = 0,
    val episodeRemoteId: String = "",
    val watched: Boolean = false,
    val watchedAt: Long? = null,
    val rewatchCount: Int = 0,
    val sourceImport: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "watch_events",
    foreignKeys = [
        ForeignKey(
            entity = MediaItem::class,
            parentColumns = ["localId"],
            childColumns = ["mediaItemLocalId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Episode::class,
            parentColumns = ["localId"],
            childColumns = ["episodeLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaItemLocalId"]),
        Index(value = ["episodeLocalId"])
    ]
)
data class WatchEvent(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String = java.util.UUID.randomUUID().toString(),
    val deleted: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    val mediaItemLocalId: Long = 0,
    val mediaItemRemoteId: String = "",
    val episodeLocalId: Long? = null, // null for movies
    val episodeRemoteId: String = "",
    val watchedAt: Long = 0,
    val eventType: String = "", // "watch", "rewatch"
    val rewatchIndex: Int = 0,
    val sourceImport: String? = null
)

@Entity(
    tableName = "import_jobs"
)
data class ImportJob(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val type: String, // "csv", "json"
    val fileName: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: String, // "running", "completed", "failed", "requires_reconciliation"
    val totalRows: Int = 0,
    val matchedRows: Int = 0,
    val unmatchedRows: Int = 0,
    val ambiguousRows: Int = 0,
    val notes: String? = null
)

@Entity(
    tableName = "import_match_candidates",
    foreignKeys = [
        ForeignKey(
            entity = ImportJob::class,
            parentColumns = ["localId"],
            childColumns = ["importJobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["importJobId"])]
)
data class ImportMatchCandidate(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val importJobId: Long,
    val rawTitle: String,
    val rawYear: String?,
    val rawType: String, // "movie", "tv"
    val candidateProviderId: String,
    val candidateTitle: String,
    val score: Float,
    val accepted: Boolean = false,
    val rejected: Boolean = false
)

@Serializable
@Entity(
    tableName = "app_settings"
)
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
