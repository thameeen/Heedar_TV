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

    @GET("player_api.php")
    suspend fun getCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): List<CategoryDto>

    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("series_id") seriesId: Int
    ): SeriesInfoDto

    @GET("player_api.php")
    suspend fun getVodInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("vod_id") vodId: Int
    ): VodInfoDto

    companion object {
        const val ACTION_LIVE_STREAMS = "get_live_streams"
        const val ACTION_VOD_STREAMS = "get_vod_streams"
        const val ACTION_SERIES = "get_series"
        const val ACTION_LIVE_CATEGORIES = "get_live_categories"
        const val ACTION_VOD_CATEGORIES = "get_vod_categories"
        const val ACTION_SERIES_CATEGORIES = "get_series_categories"
        const val ACTION_SERIES_INFO = "get_series_info"
        const val ACTION_VOD_INFO = "get_vod_info"
    }
}
