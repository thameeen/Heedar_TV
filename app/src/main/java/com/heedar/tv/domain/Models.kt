package com.heedar.tv.domain

/**
 * Immutable domain models. Deliberately free of Gson annotations so the UI layer
 * never depends on the wire format of the Xtream API.
 */
data class Channel(
    val id: Int,
    val name: String,
    val logoUrl: String?,
    val categoryId: String?
)

data class Movie(
    val id: Int,
    val name: String,
    val posterUrl: String?,
    val categoryId: String?
)

data class Series(
    val id: Int,
    val name: String,
    val coverUrl: String?,
    val categoryId: String?
)

/** A resolved Xtream account: always a normalised base URL ending in "/". */
data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String
)
