package com.heedar.tv.ui.login

/** Immutable screen state for the login screen. */
data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    val navigateToChannels: Boolean = false
) {
    /** True when the typed URL would send credentials unencrypted. */
    val isCleartext: Boolean
        get() = serverUrl.trim().startsWith("http://", ignoreCase = true)
}

enum class LoginError {
    EMPTY_FIELDS,
    INVALID_URL,
    AUTH,
    NETWORK,
    UNKNOWN
}
