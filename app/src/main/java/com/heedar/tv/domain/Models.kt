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

/** A server-provided category (`get_*_categories`). Never invented client-side. */
data class Category(
    val id: String,
    val name: String
)

data class Episode(
    val id: Int,
    val season: Int,
    val number: Int,
    val title: String,
    val imageUrl: String?,
    val containerExtension: String?,
    val plot: String?
)

data class Season(
    val number: Int,
    val title: String?,
    val episodes: List<Episode>
)

data class SeriesDetail(
    val id: Int,
    val name: String,
    val coverUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val seasons: List<Season>
) {
    val episodeCount: Int
        get() = seasons.sumOf { it.episodes.size }
}

data class MovieDetail(
    val id: Int,
    val name: String,
    val posterUrl: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val duration: String?,
    val rating: String?,
    val releaseDate: String?,
    val containerExtension: String?
)

/** Subscription snapshot returned by the panel for the signed-in account. */
data class AccountProfile(
    val username: String?,
    val status: String?,
    val expiresAtMillis: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean
)

/** What a favourite row points at, so one store can hold every content type. */
enum class MediaKind { LIVE, MOVIE, SERIES, EPISODE }

data class FavoriteItem(
    val kind: MediaKind,
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val subtitle: String? = null
)

/** A resolved Xtream account: always a normalised base URL ending in "/". */
data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String
)
