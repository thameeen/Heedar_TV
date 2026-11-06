package com.heedar.tv.di

import android.content.Context
import com.heedar.tv.data.EncryptedPlaylistStore
import com.heedar.tv.data.PlaylistStore
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.data.XtreamRepositoryImpl
import com.heedar.tv.data.remote.XtreamClientFactory
import com.heedar.tv.domain.ServerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Manual dependency graph. Deliberately not Hilt: the object graph is three
 * classes deep, and constructor injection keeps every ViewModel unit testable.
 */
class AppContainer(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext: Context = context.applicationContext

    val playlistStore: PlaylistStore = EncryptedPlaylistStore(appContext)

    fun createRepository(config: ServerConfig): XtreamRepository =
        XtreamRepositoryImpl(
            api = XtreamClientFactory.create(config.baseUrl),
            config = config,
            ioDispatcher = ioDispatcher
        )
}
