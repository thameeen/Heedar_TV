package com.heedar.tv.data

import com.heedar.tv.data.remote.XtreamApiService
import com.heedar.tv.data.remote.XtreamClientFactory
import com.heedar.tv.domain.Channel
import com.heedar.tv.domain.ServerConfig
import com.heedar.tv.domain.XtreamFailure
import com.heedar.tv.domain.XtreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real Retrofit/OkHttp/Gson stack against MockWebServer fixtures, so
 * the wire format assumptions of the Xtream API are verified without credentials.
 */
class XtreamRepositoryImplTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(): XtreamRepositoryImpl {
        val baseUrl = requireNotNull(UrlNormalizerFixture.normalize(server.url("/").toString()))
        return XtreamRepositoryImpl(
            api = XtreamClientFactory.create(baseUrl),
            config = ServerConfig(baseUrl, "user", "pass"),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `live streams are mapped and unusable rows are dropped`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    [
                      {"stream_id": 1, "name": "Bein 1", "stream_icon": "http://logo/1.png", "category_id": "5"},
                      {"stream_id": 2, "name": "   ", "stream_icon": "", "category_id": "5"},
                      {"name": "missing id"}
                    ]
                    """.trimIndent()
                )
        )

        val result = repository().loadLiveChannels()

        assertTrue(result is XtreamResult.Success)
        val channels = (result as XtreamResult.Success).data
        assertEquals(listOf(Channel(1, "Bein 1", "http://logo/1.png", "5")), channels)

        val request = requireNotNull(server.takeRequest().requestUrl)
        assertEquals("/player_api.php", request.encodedPath)
        assertEquals(XtreamApiService.ACTION_LIVE_STREAMS, request.queryParameter("action"))
        assertEquals("user", request.queryParameter("username"))
        assertEquals("pass", request.queryParameter("password"))
    }

    @Test
    fun `blank logo is normalised to null`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"stream_id": 9, "name": "News", "stream_icon": "  "}]""")
        )

        val result = repository().loadLiveChannels()

        val channels = (result as XtreamResult.Success).data
        assertEquals(listOf(Channel(9, "News", null, null)), channels)
    }

    @Test
    fun `authenticate succeeds when auth flag is 1`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"user_info": {"auth": 1, "status": "Active"}}""")
        )

        assertTrue(repository().authenticate() is XtreamResult.Success)
    }

    @Test
    fun `authenticate reports AUTH when the panel rejects the credentials`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"user_info": {"auth": 0, "status": "Disabled"}}""")
        )

        val result = repository().authenticate()

        assertEquals(XtreamFailure.AUTH, (result as XtreamResult.Failure).reason)
    }

    @Test
    fun `authenticate reports AUTH when user_info is missing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{}"""))

        val result = repository().authenticate()

        assertEquals(XtreamFailure.AUTH, (result as XtreamResult.Failure).reason)
    }

    @Test
    fun `http error status is reported as NETWORK`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repository().loadLiveChannels()

        assertEquals(XtreamFailure.NETWORK, (result as XtreamResult.Failure).reason)
    }

    @Test
    fun `panel returning an object instead of an array is reported as PARSE`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"error": "invalid account"}""")
        )

        val result = repository().loadLiveChannels()

        assertEquals(XtreamFailure.PARSE, (result as XtreamResult.Failure).reason)
    }

    @Test
    fun `unreachable panel is reported as NETWORK`() = runBlocking {
        // Port 1 is never listening, so the connection is refused immediately.
        // The shared MockWebServer is left untouched for tearDown().
        val baseUrl = requireNotNull(UrlNormalizerFixture.normalize("http://127.0.0.1:1"))

        val repository = XtreamRepositoryImpl(
            api = XtreamClientFactory.create(baseUrl),
            config = ServerConfig(baseUrl, "user", "pass"),
            ioDispatcher = Dispatchers.Unconfined
        )

        val result = repository.loadLiveChannels()

        assertEquals(XtreamFailure.NETWORK, (result as XtreamResult.Failure).reason)
    }
}
