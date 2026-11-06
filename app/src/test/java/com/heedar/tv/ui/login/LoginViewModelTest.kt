package com.heedar.tv.ui.login

import com.heedar.tv.data.PlaylistStore
import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.Movie
import com.heedar.tv.domain.Series
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        store: PlaylistStore,
        repository: XtreamRepository,
        onFactoryCall: () -> Unit = {}
    ): LoginViewModel = LoginViewModel(
        playlistStore = store,
        repositoryFactory = {
            onFactoryCall()
            repository
        },
        ioDispatcher = dispatcher
    )

    @Test
    fun `blank password is rejected without touching the network`() = runTest(dispatcher) {
        val store = FakePlaylistStore()
        val repository = FakeRepository(XtreamResult.Success(Unit))
        var factoryCalls = 0
        val viewModel = viewModel(store, repository) { factoryCalls++ }

        viewModel.onServerUrlChanged("http://panel.tv:8080")
        viewModel.onUsernameChanged("user")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(LoginError.EMPTY_FIELDS, viewModel.state.value.error)
        assertEquals(0, factoryCalls)
        assertEquals(0, repository.authCalls)
        assertNull(store.saved)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `unusable server url is rejected before any request`() = runTest(dispatcher) {
        val store = FakePlaylistStore()
        val repository = FakeRepository(XtreamResult.Success(Unit))
        val viewModel = viewModel(store, repository)

        viewModel.onServerUrlChanged("http://")
        viewModel.onUsernameChanged("user")
        viewModel.onPasswordChanged("pass")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(LoginError.INVALID_URL, viewModel.state.value.error)
        assertEquals(0, repository.authCalls)
        assertNull(store.saved)
    }

    @Test
    fun `successful login stores the normalised config and navigates`() = runTest(dispatcher) {
        val store = FakePlaylistStore()
        val viewModel = viewModel(store, FakeRepository(XtreamResult.Success(Unit)))

        viewModel.onServerUrlChanged("panel.tv:8080/player_api.php")
        viewModel.onUsernameChanged("  user  ")
        viewModel.onPasswordChanged("pass")
        viewModel.submit()
        advanceUntilIdle()

        val saved = requireNotNull(store.saved)
        assertEquals("http://panel.tv:8080/", saved.baseUrl)
        assertEquals("user", saved.username)
        assertEquals("pass", saved.password)
        assertTrue(viewModel.state.value.navigateToChannels)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `navigation flag is consumed once`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePlaylistStore(), FakeRepository(XtreamResult.Success(Unit)))

        viewModel.onServerUrlChanged("http://panel.tv")
        viewModel.onUsernameChanged("user")
        viewModel.onPasswordChanged("pass")
        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.navigateToChannels)

        viewModel.onNavigationHandled()

        assertFalse(viewModel.state.value.navigateToChannels)
    }

    @Test
    fun `rejected credentials surface an auth error and store nothing`() = runTest(dispatcher) {
        val store = FakePlaylistStore()
        val viewModel = viewModel(store, FakeRepository(XtreamResult.Failure(XtreamFailure.AUTH)))

        viewModel.onServerUrlChanged("http://panel.tv")
        viewModel.onUsernameChanged("user")
        viewModel.onPasswordChanged("wrong")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(LoginError.AUTH, viewModel.state.value.error)
        assertNull(store.saved)
        assertFalse(viewModel.state.value.navigateToChannels)
    }

    @Test
    fun `network failure surfaces a network error`() = runTest(dispatcher) {
        val viewModel = viewModel(
            FakePlaylistStore(),
            FakeRepository(XtreamResult.Failure(XtreamFailure.NETWORK))
        )

        viewModel.onServerUrlChanged("http://panel.tv")
        viewModel.onUsernameChanged("user")
        viewModel.onPasswordChanged("pass")
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(LoginError.NETWORK, viewModel.state.value.error)
    }

    @Test
    fun `typed http url raises the cleartext warning`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePlaylistStore(), FakeRepository(XtreamResult.Success(Unit)))

        viewModel.onServerUrlChanged("http://panel.tv")
        assertTrue(viewModel.state.value.isCleartext)

        viewModel.onServerUrlChanged("https://panel.tv")
        assertFalse(viewModel.state.value.isCleartext)
    }

    @Test
    fun `editing a field clears the previous error`() = runTest(dispatcher) {
        val viewModel = viewModel(FakePlaylistStore(), FakeRepository(XtreamResult.Success(Unit)))

        viewModel.submit()
        advanceUntilIdle()
        assertEquals(LoginError.EMPTY_FIELDS, viewModel.state.value.error)

        viewModel.onUsernameChanged("user")

        assertNull(viewModel.state.value.error)
    }

    private class FakePlaylistStore : PlaylistStore {
        var saved: ServerConfig? = null
            private set

        override fun save(config: ServerConfig) {
            saved = config
        }

        override fun load(): ServerConfig? = saved

        override fun clear() {
            saved = null
        }
    }

    private class FakeRepository(
        private val authResult: XtreamResult<Unit>
    ) : XtreamRepository {

        var authCalls: Int = 0
            private set

        override suspend fun authenticate(): XtreamResult<Unit> {
            authCalls++
            return authResult
        }

        override suspend fun loadLiveChannels(): XtreamResult<List<Channel>> =
            XtreamResult.Success(emptyList())

        override suspend fun loadVod(): XtreamResult<List<Movie>> =
            XtreamResult.Success(emptyList())

        override suspend fun loadSeries(): XtreamResult<List<Series>> =
            XtreamResult.Success(emptyList())
    }
}
