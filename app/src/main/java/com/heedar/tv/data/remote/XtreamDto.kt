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
    @SerializedName("username") val username: String?
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
