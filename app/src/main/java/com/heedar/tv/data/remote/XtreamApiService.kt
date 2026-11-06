package com.heedar.tv.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Xtream Codes `player_api.php` endpoint.
 *
 * The base URL is always the normalised panel root (see [UrlNormalizer]), so the
 * relative path here is just the API file name. No default argument values: the
 * `action` is always passed explicitly by the repository.
 */
interface XtreamApiService {

    @GET("player_api.php")
    suspend fun authenticate(
        @Query("username") username: String,
        @Query("password") password: String
    ): AuthResponseDto

    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): List<LiveStreamDto>

    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): List<VodStreamDto>

    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): List<SeriesDto>

    companion object {
        const val ACTION_LIVE_STREAMS = "get_live_streams"
        const val ACTION_VOD_STREAMS = "get_vod_streams"
        const val ACTION_SERIES = "get_series"
    }
}
