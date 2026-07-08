@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.fagghino.tvvault.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TmdbSearchResponse<T>(
    @SerialName("results") val results: List<T>
)

@Serializable
data class TmdbTvShowDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList()
)

@Serializable
data class TmdbMovieDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList()
)

@Serializable
data class TmdbTvShowDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("original_name") val originalName: String? = null,
    @SerialName("overview") val overview: String,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("genres") val genres: List<TmdbGenreDto> = emptyList()
)

@Serializable
data class TmdbGenreDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String
)

@Serializable
data class TmdbSeasonDto(
    @SerialName("id") val id: Int,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("name") val name: String,
    @SerialName("episodes") val episodes: List<TmdbEpisodeDto> = emptyList()
)

@Serializable
data class TmdbEpisodeDto(
    @SerialName("id") val id: Int,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("name") val name: String,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("overview") val overview: String? = null
)

@Serializable
data class TmdbMovieDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("overview") val overview: String,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("genres") val genres: List<TmdbGenreDto> = emptyList()
)
