package com.movieswipe

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ApiClientHistoryTest {
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
    fun allHistorySectionsLoadSharedIds() {
        val cases = listOf(
            Triple("/api/history", "{\"history\":[{\"movieId\":22,\"action\":\"keep\"}]}", 22),
            Triple("/api/shows/history", "{\"history\":[{\"movieId\":44,\"showId\":44,\"action\":\"keep\"}]}", 44),
            Triple("/api/discover/history", "{\"history\":[{\"movieId\":12345,\"tmdbId\":12345,\"action\":\"hidden\"}]}", 12345),
        )

        cases.forEachIndexed { index, (path, body, expectedId) ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(body))
            val callback = CountDownLatch(1)
            var response: HistoryResponse? = null
            var error: String? = null

            val handler: (HistoryResponse?, String?) -> Unit = { value, failure ->
                response = value
                error = failure
                callback.countDown()
            }
            when (index) {
                0 -> api.getHistory(handler)
                1 -> api.getShowHistory(handler)
                else -> api.getDiscoverHistory(handler)
            }

            assertTrue(callback.await(2, TimeUnit.SECONDS))
            assertNull(error)
            assertEquals(expectedId, response!!.history.single().movieId)
            assertEquals(path, server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        }
    }

    @Test
    fun everyHistoryActionPostsToItsExpectedEndpoint() {
        val actions = listOf<Pair<String, ((Boolean, String?) -> Unit) -> Unit>>(
            "/api/movies/22/unkeep" to { callback -> api.unkeepMovie(22, callback) },
            "/api/movies/22/unblock" to { callback -> api.unblockMovie(22, callback) },
            "/api/shows/44/unkeep" to { callback -> api.postShowAction(44, "unkeep", callback) },
            "/api/shows/44/unblock" to { callback -> api.postShowAction(44, "unblock", callback) },
            "/api/shows/44/unclean" to { callback -> api.uncleanShow(44, callback) },
            "/api/discover/12345/remove_movie" to { callback -> api.removeMovieFromDiscover(12345, callback) },
            "/api/discover/54321/remove_show" to { callback -> api.removeShowFromDiscover(54321, callback) },
            "/api/discover/12345/unhide" to { callback -> api.unhideDiscover(12345, callback) },
        )

        actions.forEach { (expectedPath, invoke) ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("{\"ok\":true}"))
            val callback = CountDownLatch(1)
            var succeeded = false
            invoke { ok, _ ->
                succeeded = ok
                callback.countDown()
            }

            assertTrue(callback.await(2, TimeUnit.SECONDS))
            assertTrue(succeeded)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals(expectedPath, request.path)
        }
    }
}
