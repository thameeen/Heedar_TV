package com.heedar.tv.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heedar.tv.data.PlaylistStore
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.data.remote.UrlNormalizer
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Validates input, authenticates against the panel and only then persists the
 * credentials. Owns no Android types, so it is fully unit testable.
 */
class LoginViewModel(
    private val playlistStore: PlaylistStore,
    private val repositoryFactory: (ServerConfig) -> XtreamRepository,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onServerUrlChanged(value: String) {
        _state.update { it.copy(serverUrl = value, error = null) }
    }

    fun onUsernameChanged(value: String) {
        _state.update { it.copy(username = value, error = null) }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(password = value, error = null) }
    }

    fun onNavigationHandled() {
        _state.update { it.copy(navigateToChannels = false) }
    }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return

        if (current.serverUrl.isBlank() || current.username.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = LoginError.EMPTY_FIELDS) }
            return
        }

        val baseUrl = UrlNormalizer.normalize(current.serverUrl)
        if (baseUrl == null) {
            _state.update { it.copy(error = LoginError.INVALID_URL) }
            return
        }

        val config = ServerConfig(
            baseUrl = baseUrl,
            username = current.username.trim(),
            password = current.password
        )
        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            when (val result = withContext(ioDispatcher) { repositoryFactory(config).authenticate() }) {
                is XtreamResult.Success -> {
                    withContext(ioDispatcher) { playlistStore.save(config) }
                    _state.update { it.copy(isLoading = false, navigateToChannels = true) }
                }

                is XtreamResult.Failure -> {
                    _state.update {
                        it.copy(isLoading = false, error = result.reason.toLoginError())
                    }
                }
            }
        }
    }

    private fun XtreamFailure.toLoginError(): LoginError = when (this) {
        XtreamFailure.AUTH -> LoginError.AUTH
        XtreamFailure.NETWORK -> LoginError.NETWORK
        XtreamFailure.PARSE, XtreamFailure.UNKNOWN -> LoginError.UNKNOWN
    }
}
