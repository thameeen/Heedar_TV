package com.heedar.tv.data

import com.heedar.tv.domain.ServerConfig

/**
 * Persists the single playlist this app supports.
 *
 * Implementations must never store the password in plaintext: see
 * [EncryptedPlaylistStore].
 */
interface PlaylistStore {

    fun save(config: ServerConfig)

    fun load(): ServerConfig?

    fun clear()
}
