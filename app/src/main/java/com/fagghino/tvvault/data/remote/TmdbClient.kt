package com.fagghino.tvvault.data.remote

import android.content.Context
import com.fagghino.tvvault.BuildConfig
import com.fagghino.tvvault.data.remote.dto.*
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbService {
    @GET("search/tv")
    suspend fun searchTvShows(
        @Query("query") query: String
    ): TmdbSearchResponse<TmdbTvShowDto>

    @GET("search/movie")
    suspend fun searchMovies(
        @Query("query") query: String
    ): TmdbSearchResponse<TmdbMovieDto>

    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(
        @Path("tv_id") tvId: Int
    ): TmdbTvShowDetailDto

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): TmdbSeasonDto

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int
    ): TmdbMovieDetailDto

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonWithAirDates(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): TmdbSeasonDto
}

class TmdbApiKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val apiKey = BuildConfig.TMDB_API_KEY
        
        val originalUrl = originalRequest.url
        val newUrl = originalUrl.newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "it-IT") // Retrieve in Italian as requested in the plan
            .build()
            
        val newRequest = originalRequest.newBuilder()
            .url(newUrl)
            .build()
            
        return chain.proceed(newRequest)
    }
}

object TmdbClient {
    private const val BASE_URL = "https://api.themoviedb.org/3/"
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun createService(context: Context): TmdbService {
        val cacheSize = 10 * 1024 * 1024L // 10 MiB
        val cache = Cache(context.cacheDir, cacheSize)

        val okHttpClient = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(TmdbApiKeyInterceptor())
            .build()

        val contentType = "application/json".toMediaType()
        
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(TmdbService::class.java)
    }
}
