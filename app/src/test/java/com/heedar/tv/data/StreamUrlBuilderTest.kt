package com.heedar.tv.data

import com.heedar.tv.domain.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamUrlBuilderTest {

    private val config = ServerConfig(
        baseUrl = "http://panel.tv:8080/",
        username = "user",
        password = "pass"
    )

    @Test
    fun `live url has no double slash`() {
        assertEquals(
            "http://panel.tv:8080/live/user/pass/42.m3u8",
            StreamUrlBuilder.liveStream(config, 42)
        )
    }

    @Test
    fun `vod url uses the movie path and extension`() {
        assertEquals(
            "http://panel.tv:8080/movie/user/pass/7.mkv",
            StreamUrlBuilder.vodStream(config.baseUrl, config.username, config.password, 7, "mkv")
        )
    }
}
