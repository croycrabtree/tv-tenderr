package com.movieswipe

import com.google.gson.JsonParser
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ApiClientDiscoverDislikeTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = ApiClient(server.url("").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun dislikeDiscoverPostsToDedicatedDislikeEndpointWithMetadata() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))
        val callback = CountDownLatch(1)

        api.dislikeDiscover(
            tmdbId = 12345,
            title = "The Test Movie",
            year = "2024",
            posterUrl = "https://image.test/poster.jpg",
            type = "movies",
        ) { ok, _ ->
            assertTrue(ok)
            callback.countDown()
        }

        assertTrue(callback.await(2, TimeUnit.SECONDS))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/discover/12345/dislike", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("The Test Movie", json["title"].asString)
        assertEquals("2024", json["year"].asString)
        assertEquals("https://image.test/poster.jpg", json["posterUrl"].asString)
        assertEquals("movies", json["type"].asString)
    }

    @Test
    fun hideDiscoverStillPostsToHideEndpointForSkipWithoutDislikeSignal() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))
        val callback = CountDownLatch(1)

        api.hideDiscover(
            tmdbId = 12345,
            title = "The Test Movie",
            year = "2024",
            posterUrl = "https://image.test/poster.jpg",
            type = "movies",
        ) { ok, _ ->
            assertTrue(ok)
            callback.countDown()
        }

        assertTrue(callback.await(2, TimeUnit.SECONDS))
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/discover/12345/hide", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("hidden", json["action"].asString)
    }
}
