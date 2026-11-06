package com.heedar.tv.ui.common

import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.FavoriteItem
import com.heedar.tv.domain.MediaKind
import com.heedar.tv.domain.Movie
import com.heedar.tv.domain.Series

/**
 * One renderable tile. Channels, movies, series and favourites all collapse to
 * this so a single adapter (and therefore one focus implementation) serves every
 * grid in the app.
 */
data class MediaCard(
    val kind: MediaKind,
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val subtitle: String? = null,
    val categoryId: String? = null
) {
    /** Stable key for favourite lookups across content types. */
    val key: String get() = "$kind:$id"

    fun toFavoriteItem(): FavoriteItem = FavoriteItem(
        kind = kind,
        id = id,
        title = title,
        imageUrl = imageUrl,
        subtitle = subtitle
    )
}

fun Channel.toCard(categoryName: String? = null): MediaCard = MediaCard(
    kind = MediaKind.LIVE,
    id = id,
    title = name,
    imageUrl = logoUrl,
    subtitle = categoryName,
    categoryId = categoryId
)

fun Movie.toCard(categoryName: String? = null): MediaCard = MediaCard(
    kind = MediaKind.MOVIE,
    id = id,
    title = name,
    imageUrl = posterUrl,
    subtitle = categoryName,
    categoryId = categoryId
)

fun Series.toCard(categoryName: String? = null): MediaCard = MediaCard(
    kind = MediaKind.SERIES,
    id = id,
    title = name,
    imageUrl = coverUrl,
    subtitle = categoryName,
    categoryId = categoryId
)

fun FavoriteItem.toCard(): MediaCard = MediaCard(
    kind = kind,
    id = id,
    title = title,
    imageUrl = imageUrl,
    subtitle = subtitle
)
