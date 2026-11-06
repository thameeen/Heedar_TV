package com.heedar.tv.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heedar.tv.data.FavoritesStore
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.domain.Category
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import com.heedar.tv.ui.common.MediaCard
import com.heedar.tv.ui.common.SectionFilters
import com.heedar.tv.ui.common.toCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which catalog a [CatalogViewModel] serves. */
enum class CatalogType { LIVE, MOVIES, SERIES }

/**
 * Backs Live, Movies, Series, News and Sports.
 *
 * News and Sports are the Live catalog restricted to categories whose names match
 * [categoryKeywords]; nothing is fabricated when the panel has no such category.
 */
class CatalogViewModel(
    private val repository: XtreamRepository,
    private val favorites: FavoritesStore,
    private val type: CatalogType,
    private val categoryKeywords: List<String>,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val cards: List<MediaCard> = emptyList(),
        val categories: List<Category> = emptyList(),
        val selectedCategoryId: String? = null,
        val failure: XtreamFailure? = null,
        val favoriteKeys: Set<String> = emptySet()
    ) {
        val isEmpty: Boolean
            get() = !loading && failure == null && cards.isEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var allCards: List<MediaCard> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, failure = null)

            val categories = withContext(ioDispatcher) { loadCategories() }
            val scopedCategories = categories.filter {
                SectionFilters.matchesCategory(it.name, categoryKeywords)
            }
            val categoryNames = categories.associate { it.id to it.name }

            when (val result = withContext(ioDispatcher) { loadCards(categoryNames) }) {
                is XtreamResult.Failure -> {
                    allCards = emptyList()
                    _state.value = _state.value.copy(
                        loading = false,
                        cards = emptyList(),
                        categories = scopedCategories,
                        failure = result.reason
                    )
                }

                is XtreamResult.Success -> {
                    val allowedIds = scopedCategories.map { it.id }.toSet()
                    allCards = if (categoryKeywords.isEmpty()) {
                        result.data
                    } else {
                        result.data.filter { it.categoryId != null && it.categoryId in allowedIds }
                    }
                    _state.value = _state.value.copy(
                        loading = false,
                        failure = null,
                        categories = scopedCategories,
                        selectedCategoryId = null,
                        cards = applyFilter(allCards, null),
                        favoriteKeys = favoriteKeys()
                    )
                }
            }
        }
    }

    /** Re-reads favourites so markers stay correct after another screen changes them. */
    fun refreshFavorites() {
        val keys = favoriteKeys()
        if (keys != _state.value.favoriteKeys) {
            _state.value = _state.value.copy(favoriteKeys = keys)
        }
    }

    fun selectCategory(categoryId: String?) {
        _state.value = _state.value.copy(
            selectedCategoryId = categoryId,
            cards = applyFilter(allCards, categoryId)
        )
    }

    /** @return true when the item is a favourite after this call. */
    fun toggleFavorite(card: MediaCard): Boolean {
        val isFavorite = favorites.toggle(card.toFavoriteItem())
        _state.value = _state.value.copy(favoriteKeys = favoriteKeys())
        return isFavorite
    }

    private fun applyFilter(cards: List<MediaCard>, categoryId: String?): List<MediaCard> =
        if (categoryId == null) cards else cards.filter { it.categoryId == categoryId }

    private fun favoriteKeys(): Set<String> =
        favorites.all().map { "${it.kind}:${it.id}" }.toSet()

    private suspend fun loadCategories(): List<Category> = when (type) {
        CatalogType.LIVE -> repository.loadLiveCategories().orEmpty()
        CatalogType.MOVIES -> repository.loadVodCategories().orEmpty()
        CatalogType.SERIES -> repository.loadSeriesCategories().orEmpty()
    }

    private suspend fun loadCards(categoryNames: Map<String, String>): XtreamResult<List<MediaCard>> =
        when (type) {
            CatalogType.LIVE -> repository.loadLiveChannels()
                .mapList { it.toCard(categoryNames[it.categoryId]) }

            CatalogType.MOVIES -> repository.loadVod()
                .mapList { it.toCard(categoryNames[it.categoryId]) }

            CatalogType.SERIES -> repository.loadSeries()
                .mapList { it.toCard(categoryNames[it.categoryId]) }
        }
}

/** Categories are decorative here: a failed category call must not hide the content. */
private fun <T> XtreamResult<List<T>>.orEmpty(): List<T> = when (this) {
    is XtreamResult.Success -> data
    is XtreamResult.Failure -> emptyList()
}

private inline fun <T, R> XtreamResult<List<T>>.mapList(
    transform: (T) -> R
): XtreamResult<List<R>> = when (this) {
    is XtreamResult.Success -> XtreamResult.Success(data.map(transform))
    is XtreamResult.Failure -> this
}
