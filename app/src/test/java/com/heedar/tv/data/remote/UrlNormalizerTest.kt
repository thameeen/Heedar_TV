package com.heedar.tv.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `bare host with port gets http scheme and trailing slash`() {
        assertEquals("http://192.168.1.10:8080/", UrlNormalizer.normalize("192.168.1.10:8080"))
    }

    @Test
    fun `existing https scheme is preserved`() {
        assertEquals("https://panel.tv/", UrlNormalizer.normalize("https://panel.tv"))
    }

    @Test
    fun `pasted player_api url is reduced to the panel root`() {
        assertEquals(
            "https://panel.tv/",
            UrlNormalizer.normalize("https://panel.tv/player_api.php")
        )
    }

    @Test
    fun `pasted api url with query string is reduced to the panel root`() {
        assertEquals(
            "http://panel.tv:8080/",
            UrlNormalizer.normalize("http://panel.tv:8080/player_api.php?username=a&password=b")
        )
    }

    @Test
    fun `panel sub path is kept`() {
        assertEquals("http://host/iptv/", UrlNormalizer.normalize("http://host/iptv/"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("http://panel.tv/", UrlNormalizer.normalize("   http://panel.tv   "))
    }

    @Test
    fun `uppercase scheme is accepted`() {
        assertEquals("https://panel.tv/", UrlNormalizer.normalize("HTTPS://panel.tv"))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(UrlNormalizer.normalize(null))
        assertNull(UrlNormalizer.normalize(""))
        assertNull(UrlNormalizer.normalize("     "))
    }

    @Test
    fun `scheme without host is rejected`() {
        assertNull(UrlNormalizer.normalize("http://"))
        assertNull(UrlNormalizer.normalize("https://"))
    }

    @Test
    fun `authority with whitespace is rejected`() {
        assertNull(UrlNormalizer.normalize("http://panel tv"))
    }
}
