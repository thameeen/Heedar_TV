package com.heedar.tv.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.heedar.tv.domain.FavoriteItem
import com.heedar.tv.domain.MediaKind

/**
 * Favourites are per-device UI state, not account data, so they are stored
 * locally. The store keeps every content type in one list so the Favourites
 * screen can render channels, movies, series and episodes together.
 */
interface FavoritesStore {

    fun all(): List<FavoriteItem>

    fun ids(kind: MediaKind): Set<Int>

    fun contains(kind: MediaKind, id: Int): Boolean

    /** @return true when the item is a favourite after this call. */
    fun toggle(item: FavoriteItem): Boolean
}

class SharedPreferencesFavoritesStore(
    context: Context,
    private val gson: Gson = Gson()
) : FavoritesStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    private val listType = object : TypeToken<List<FavoriteItem>>() {}.type

    override fun all(): List<FavoriteItem> = synchronized(lock) { read() }

    override fun ids(kind: MediaKind): Set<Int> =
        all().asSequence().filter { it.kind == kind }.map { it.id }.toSet()

    override fun contains(kind: MediaKind, id: Int): Boolean =
        all().any { it.kind == kind && it.id == id }

    override fun toggle(item: FavoriteItem): Boolean = synchronized(lock) {
        val current = read()
        val existing = current.any { it.kind == item.kind && it.id == item.id }
        val updated = if (existing) {
            current.filterNot { it.kind == item.kind && it.id == item.id }
        } else {
            // Newest first so the most recent favourite is immediately visible.
            listOf(item) + current
        }
        write(updated)
        !existing
    }

    private fun read(): List<FavoriteItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            gson.fromJson<List<FavoriteItem>>(raw, listType) ?: emptyList()
        } catch (malformed: RuntimeException) {
            // Corrupt or schema-changed payload: drop it rather than crash the app.
            prefs.edit().remove(KEY_ITEMS).apply()
            emptyList()
        }
    }

    private fun write(items: List<FavoriteItem>) {
        prefs.edit().putString(KEY_ITEMS, gson.toJson(items, listType)).apply()
    }

    private companion object {
        const val PREFS_NAME = "heedar_favorites"
        const val KEY_ITEMS = "items_v1"
    }
}

/** Deterministic store for unit tests and previews. */
class InMemoryFavoritesStore(
    initial: List<FavoriteItem> = emptyList()
) : FavoritesStore {

    private val items = initial.toMutableList()

    override fun all(): List<FavoriteItem> = items.toList()

    override fun ids(kind: MediaKind): Set<Int> =
        items.filter { it.kind == kind }.map { it.id }.toSet()

    override fun contains(kind: MediaKind, id: Int): Boolean =
        items.any { it.kind == kind && it.id == id }

    override fun toggle(item: FavoriteItem): Boolean {
        val existing = items.any { it.kind == item.kind && it.id == item.id }
        if (existing) {
            items.removeAll { it.kind == item.kind && it.id == item.id }
        } else {
            items.add(0, item)
        }
        return !existing
    }
}
