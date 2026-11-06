package com.heedar.tv.ui.channels

import com.heedar.tv.data.XtreamRepository
import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.Movie
import com.heedar.tv.domain.Series
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
import org.junit.Before
import org.junit.Test

class ChannelsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `channels are exposed as content state`() = runTest(dispatcher) {
        val channels = listOf(Channel(1, "Bein 1", null, "5"), Channel(2, "News", null, "5"))
        val viewModel = ChannelsViewModel(FakeRepository(XtreamResult.Success(channels)), dispatcher)

        advanceUntilIdle()

        assertEquals(ChannelsUiState.Content(channels), viewModel.state.value)
    }

    @Test
    fun `empty payload maps to empty state`() = runTest(dispatcher) {
        val viewModel = ChannelsViewModel(
            FakeRepository(XtreamResult.Success(emptyList())),
            dispatcher
        )

        advanceUntilIdle()

        assertEquals(ChannelsUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `failure maps to error state with the failure reason`() = runTest(dispatcher) {
        val viewModel = ChannelsViewModel(
            FakeRepository(XtreamResult.Failure(XtreamFailure.NETWORK)),
            dispatcher
        )

        advanceUntilIdle()

        assertEquals(ChannelsUiState.Error(XtreamFailure.NETWORK), viewModel.state.value)
    }

    @Test
    fun `retry reloads after a failure`() = runTest(dispatcher) {
        val repository = FakeRepository(XtreamResult.Failure(XtreamFailure.NETWORK))
        val viewModel = ChannelsViewModel(repository, dispatcher)
        advanceUntilIdle()
        assertEquals(ChannelsUiState.Error(XtreamFailure.NETWORK), viewModel.state.value)

        repository.result = XtreamResult.Success(listOf(Channel(1, "Bein 1", null, null)))
        viewModel.load()
        advanceUntilIdle()

        // init{} loads once, the retry loads again.
        assertEquals(2, repository.calls)
        assertEquals(
            ChannelsUiState.Content(listOf(Channel(1, "Bein 1", null, null))),
            viewModel.state.value
        )
    }

    private class FakeRepository(
        var result: XtreamResult<List<Channel>>
    ) : XtreamRepository {

        var calls: Int = 0
            private set

        override suspend fun authenticate(): XtreamResult<Unit> = XtreamResult.Success(Unit)

        override suspend fun loadLiveChannels(): XtreamResult<List<Channel>> {
            calls++
            return result
        }

        override suspend fun loadVod(): XtreamResult<List<Movie>> =
            XtreamResult.Success(emptyList())

        override suspend fun loadSeries(): XtreamResult<List<Series>> =
            XtreamResult.Success(emptyList())
    }
}
