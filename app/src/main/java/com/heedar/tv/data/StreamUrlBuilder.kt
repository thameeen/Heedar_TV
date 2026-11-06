package com.heedar.tv.data

import com.heedar.tv.domain.ServerConfig

/**
 * Builds Xtream stream URLs. Kept separate from the player so the format can be
 * unit tested without Android.
 */
object StreamUrlBuilder {

    fun liveStream(
        baseUrl: String,
        username: String,
        password: String,
        streamId: Int
    ): String {
        val base = baseUrl.trimEnd('/')
        return "$base/live/$username/$password/$streamId.m3u8"
    }

    fun vodStream(
        baseUrl: String,
        username: String,
        password: String,
        streamId: Int,
        extension: String = "mp4"
    ): String {
        val base = baseUrl.trimEnd('/')
        return "$base/movie/$username/$password/$streamId.$extension"
    }

    /**
     * Series episodes live under /series/ and use the *episode* id, which the
     * Xtream API returns separately from the series id. The container extension
     * comes from the episode payload, so it is passed through unchanged.
     */
    fun seriesStream(
        baseUrl: String,
        username: String,
        password: String,
        episodeId: Int,
        extension: String = "mp4"
    ): String {
        val base = baseUrl.trimEnd('/')
        return "$base/series/$username/$password/$episodeId.$extension"
    }

    fun liveStream(config: ServerConfig, streamId: Int): String =
        liveStream(config.baseUrl, config.username, config.password, streamId)

    fun vodStream(config: ServerConfig, streamId: Int, extension: String = "mp4"): String =
        vodStream(config.baseUrl, config.username, config.password, streamId, extension)

    fun seriesStream(config: ServerConfig, episodeId: Int, extension: String = "mp4"): String =
        seriesStream(config.baseUrl, config.username, config.password, episodeId, extension)
}
