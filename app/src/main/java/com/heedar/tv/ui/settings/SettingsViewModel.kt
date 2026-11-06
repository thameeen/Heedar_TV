package com.heedar.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.domain.AccountProfile
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads the signed-in account snapshot shown on the settings screen. */
class SettingsViewModel(
    private val repository: XtreamRepository
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val profile: AccountProfile? = null,
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
            _state.value = when (val result = repository.loadProfile()) {
                is XtreamResult.Success -> State(
                    loading = false,
                    profile = result.data,
                    failure = null
                )

                is XtreamResult.Failure -> State(
                    loading = false,
                    profile = null,
                    failure = result.reason
                )
            }
        }
    }
}
