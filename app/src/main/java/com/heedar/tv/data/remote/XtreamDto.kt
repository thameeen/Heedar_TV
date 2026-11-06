package com.heedar.tv.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Xtream `player_api.php` wire models.
 *
 * Every field is nullable: real panels omit keys and mix numeric/string types,
 * so the mapper (not Gson) decides what is valid.
 */
data class AuthResponseDto(
    @SerializedName("user_info") val userInfo: UserInfoDto?,
    @SerializedName("server_info") val serverInfo: ServerInfoDto?
)

data class UserInfoDto(
    @SerializedName("auth") val auth: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("exp_date") val expDate: Long?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeConnections: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class ServerInfoDto(
    @SerializedName("url") val url: String?,
    @SerializedName("port") val port: String?,
    @SerializedName("https_port") val httpsPort: String?,
    @SerializedName("server_protocol") val serverProtocol: String?
)

data class LiveStreamDto(
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class VodStreamDto(
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?
)

data class SeriesDto(
    @SerializedName("series_id") val seriesId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("category_id") val categoryId: String?
)

/** `get_live_categories`, `get_vod_categories`, `get_series_categories`. */
data class CategoryDto(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("parent_id") val parentId: Int?
)

/** `get_series_info` payload: metadata plus episodes grouped by season number. */
data class SeriesInfoDto(
    @SerializedName("info") val info: SeriesMetadataDto?,
    @SerializedName("seasons") val seasons: List<SeasonDto>?,
    @SerializedName("episodes") val episodes: Map<String, List<EpisodeDto>>?
)

data class SeriesMetadataDto(
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("releaseDate") val releaseDate: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("episode_run_time") val episodeRunTime: String?
)

data class SeasonDto(
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("episode_count") val episodeCount: Int?
)

data class EpisodeDto(
    @SerializedName("id") val id: String?,
    @SerializedName("episode_num") val episodeNum: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("season") val season: Int?,
    @SerializedName("info") val info: EpisodeMetadataDto?
)

data class EpisodeMetadataDto(
    @SerializedName("plot") val plot: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("movie_image") val movieImage: String?,
    @SerializedName("rating") val rating: String?
)

/** `get_vod_info` payload. */
data class VodInfoDto(
    @SerializedName("info") val info: VodMetadataDto?,
    @SerializedName("movie_data") val movieData: VodMovieDataDto?
)

data class VodMetadataDto(
    @SerializedName("name") val name: String?,
    @SerializedName("movie_image") val movieImage: String?,
    @SerializedName("plot") val plot: String?,
    @SerializedName("cast") val cast: String?,
    @SerializedName("director") val director: String?,
    @SerializedName("genre") val genre: String?,
    @SerializedName("duration") val duration: String?,
    @SerializedName("rating") val rating: String?,
    @SerializedName("releasedate") val releaseDate: String?,
    @SerializedName("releaseDate") val releaseDateAlt: String?
)

data class VodMovieDataDto(
    @SerializedName("stream_id") val streamId: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("container_extension") val containerExtension: String?,
    @SerializedName("category_id") val categoryId: String?
)
