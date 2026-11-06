package com.heedar.tv.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.domain.MovieDetail
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MovieDetailViewModel(
    private val repository: XtreamRepository,
    private val movieId: Int
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val detail: MovieDetail? = null,
        val failure: XtreamFailure? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, failure = null)
            _state.value = when (val result = repository.loadMovieDetail(movieId)) {
                is XtreamResult.Success -> State(
                    loading = false,
                    detail = result.data,
                    failure = null
                )

                is XtreamResult.Failure -> State(
                    loading = false,
                    detail = null,
                    failure = result.reason
                )
            }
        }
    }
}
