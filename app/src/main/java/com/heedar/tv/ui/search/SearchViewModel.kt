package com.heedar.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import com.heedar.tv.ui.common.MediaCard
import com.heedar.tv.ui.common.toCard
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Searches live channels, movies and series. The whole catalog is fetched once and
 * filtered in memory, which keeps typing responsive and avoids one request per
 * keystroke.
 */
class SearchViewModel(
    private val repository: XtreamRepository
) : ViewModel() {

    data class State(
        val query: String = "",
        val loading: Boolean = true,
        val failure: XtreamFailure? = null,
        val results: List<MediaCard> = emptyList()
    ) {
        val needsMoreCharacters: Boolean
            get() = query.trim().length < MIN_QUERY_LENGTH

        val promptVisible: Boolean
            get() = !loading && failure == null && needsMoreCharacters

        val emptyVisible: Boolean
            get() = !loading && failure == null && !needsMoreCharacters && results.isEmpty()
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var index: List<MediaCard> = emptyList()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, failure = null)
            val catalog = coroutineScope {
                val live = async { repository.loadLiveChannels() }
                val movies = async { repository.loadVod() }
                val series = async { repository.loadSeries() }

                val liveResult = live.await()
                val moviesResult = movies.await()
                val seriesResult = series.await()

                if (liveResult is XtreamResult.Failure &&
                    moviesResult is XtreamResult.Failure &&
                    seriesResult is XtreamResult.Failure
                ) {
                    return@coroutineScope XtreamResult.Failure(liveResult.reason)
                }

                val cards = buildList {
                    addAll(liveResult.orEmpty().map { it.toCard() })
                    addAll(moviesResult.orEmpty().map { it.toCard() })
                    addAll(seriesResult.orEmpty().map { it.toCard() })
                }
                XtreamResult.Success(cards)
            }

            when (catalog) {
                is XtreamResult.Failure -> {
                    index = emptyList()
                    _state.value = _state.value.copy(
                        loading = false,
                        failure = catalog.reason,
                        results = emptyList()
                    )
                }

                is XtreamResult.Success -> {
                    index = catalog.data
                    _state.value = _state.value.copy(
                        loading = false,
                        failure = null,
                        results = filter(index, _state.value.query)
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query, results = filter(index, query))
    }

    private fun filter(cards: List<MediaCard>, query: String): List<MediaCard> {
        val needle = query.trim()
        if (needle.length < MIN_QUERY_LENGTH) return emptyList()
        return cards.asSequence()
            .filter { it.title.contains(needle, ignoreCase = true) }
            .take(MAX_RESULTS)
            .toList()
    }

    private fun XtreamResult<List<MediaCard>>.orEmpty(): List<MediaCard> = when (this) {
        is XtreamResult.Success -> data
        is XtreamResult.Failure -> emptyList()
    }

    private fun <T> XtreamResult<List<T>>.orEmpty(): List<T> = when (this) {
        is XtreamResult.Success -> data
        is XtreamResult.Failure -> emptyList()
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_RESULTS = 120
    }
}
