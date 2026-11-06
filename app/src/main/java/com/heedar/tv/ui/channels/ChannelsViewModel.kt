package com.heedar.tv.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.domain.XtreamResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChannelsViewModel(
    private val repository: XtreamRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow<ChannelsUiState>(ChannelsUiState.Loading)
    val state: StateFlow<ChannelsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ChannelsUiState.Loading
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { repository.loadLiveChannels() }
            _state.value = when (result) {
                is XtreamResult.Success ->
                    if (result.data.isEmpty()) {
                        ChannelsUiState.Empty
                    } else {
                        ChannelsUiState.Content(result.data)
                    }

                is XtreamResult.Failure -> ChannelsUiState.Error(result.reason)
            }
        }
    }
}
