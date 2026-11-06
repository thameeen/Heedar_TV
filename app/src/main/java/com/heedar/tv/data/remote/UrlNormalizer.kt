package com.heedar.tv.data.remote

/**
 * Turns whatever the user typed into a Retrofit-legal base URL.
 *
 * Handles the four shapes users actually paste:
 *  - "192.168.1.10:8080"                  -> "http://192.168.1.10:8080/"
 *  - "http://panel.tv:8080/"              -> "http://panel.tv:8080/"
 *  - "https://panel.tv/player_api.php"    -> "https://panel.tv/"
 *  - "   "                                -> null
 *
 * Pure Kotlin on purpose: unit tested on the JVM without Android or Robolectric.
 */
object UrlNormalizer {

    private const val API_FILE = "player_api.php"
    private val SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)
    private val SCHEME_LENGTH = "https://".length

    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else "http://$trimmed"
        val withoutQuery = withScheme.substringBefore('?').substringBefore('#')
        val withoutApiFile = if (withoutQuery.endsWith(API_FILE, ignoreCase = true)) {
            withoutQuery.dropLast(API_FILE.length)
        } else {
            withoutQuery
        }

        val root = withoutApiFile.trimEnd('/')
        if (root.length <= SCHEME_LENGTH) return null

        val authority = root.substringAfter("://")
        if (authority.isBlank() || authority.startsWith("/")) return null
        if (authority.any { it.isWhitespace() }) return null

        return "$root/"
    }
}
