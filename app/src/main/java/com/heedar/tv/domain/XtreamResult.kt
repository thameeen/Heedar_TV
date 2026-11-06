package com.heedar.tv.domain

/** Why a repository call did not produce data. */
enum class XtreamFailure {
    /** Connection refused, timeout, DNS, TLS, HTTP error status. */
    NETWORK,

    /** Server answered but rejected the credentials. */
    AUTH,

    /** Server answered with a payload that does not match the Xtream schema. */
    PARSE,

    /** Anything else. */
    UNKNOWN
}

/**
 * Result of a repository call. Callers must handle every branch; no silent nulls
 * and no exception leaking into the ViewModel.
 */
sealed interface XtreamResult<out T> {

    data class Success<T>(val data: T) : XtreamResult<T>

    data class Failure(
        val reason: XtreamFailure,
        val message: String? = null
    ) : XtreamResult<Nothing>
}
