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

    fun liveStream(config: ServerConfig, streamId: Int): String =
        liveStream(config.baseUrl, config.username, config.password, streamId)
}
