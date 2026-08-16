package com.movieswipe

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(private var baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    fun setUrl(url: String) { baseUrl = url }
    fun getBaseUrl(): String = baseUrl

    fun refreshPlex(callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/plex/refresh")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}")
                }
            }
        })
    }

    fun getMovies(skip: Int = 0, limit: Int = 20, callback: (MoviesResponse?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/movies?skip=$skip&limit=$limit")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val movies = gson.fromJson(body, MoviesResponse::class.java)
                        callback(movies, null)
                    } else {
                        callback(null, "HTTP ${it.code}")
                    }
                }
            }
        })
    }

    fun keepMovie(movieId: Int, callback: (Boolean, String?) -> Unit) {
        postAction(movieId, "keep", callback)
    }

    fun superKeepMovie(movieId: Int, callback: (Boolean, String?) -> Unit) {
        postAction(movieId, "super_keep", callback)
    }

    fun blockMovie(movieId: Int, callback: (Boolean, String?) -> Unit) {
        postAction(movieId, "block", callback)
    }

    fun skipMovie(movieId: Int, callback: (Boolean, String?) -> Unit) {
        postAction(movieId, "skip", callback)
    }

    private fun postAction(movieId: Int, action: String, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/movies/$movieId/$action")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}")
                }
            }
        })
    }

    fun getStats(callback: (StatsResponse?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/stats")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val stats = gson.fromJson(body, StatsResponse::class.java)
                        callback(stats, null)
                    } else {
                        callback(null, "HTTP ${it.code}")
                    }
                }
            }
        })
    }

    fun getPosterUrl(movieId: Int): String {
        return "$baseUrl/api/poster/$movieId"
    }

    fun getHistory(callback: (HistoryResponse?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/history")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val history = gson.fromJson(body, HistoryResponse::class.java)
                        callback(history, null)
                    } else {
                        callback(null, "HTTP ${it.code}")
                    }
                }
            }
        })
    }

    fun unkeepMovie(movieId: Int, callback: (Boolean, String?) -> Unit) {
        postAction(movieId, "unkeep", callback)
    }

    fun unblockMovie(movieId: Int, callback: (Boolean, String?) -> Unit) {
        postAction(movieId, "unblock", callback)
    }

    // ==================== SHOW METHODS ====================

    fun getShows(skip: Int = 0, limit: Int = 20, callback: (ShowsResponse?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/shows?skip=$skip&limit=$limit")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val shows = gson.fromJson(body, ShowsResponse::class.java)
                        callback(shows, null)
                    } else {
                        callback(null, "HTTP ${it.code}")
                    }
                }
            }
        })
    }

    fun postShowAction(showId: Int, action: String, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/shows/$showId/$action")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}")
                }
            }
        })
    }

    fun keepShow(showId: Int, callback: (Boolean, String?) -> Unit) {
        postShowAction(showId, "keep", callback)
    }

    fun superKeepShow(showId: Int, callback: (Boolean, String?) -> Unit) {
        postShowAction(showId, "super_keep", callback)
    }

    fun blockShow(showId: Int, callback: (Boolean, String?) -> Unit) {
        postShowAction(showId, "block", callback)
    }

    fun skipShow(showId: Int, callback: (Boolean, String?) -> Unit) {
        postShowAction(showId, "skip", callback)
    }

    fun cleanShow(showId: Int, callback: (Boolean, String?) -> Unit) {
        postShowAction(showId, "clean", callback)
    }

    fun uncleanShow(showId: Int, callback: (Boolean, String?) -> Unit) {
        postShowAction(showId, "unclean", callback)
    }

    fun getShowHistory(callback: (HistoryResponse?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/shows/history")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, e.message)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val history = gson.fromJson(body, HistoryResponse::class.java)
                        callback(history, null)
                    } else {
                        callback(null, "HTTP ${it.code}")
                    }
                }
            }
        })
    }

    // ==================== DISCOVER METHODS ====================

    fun getProviders(callback: (ProvidersResponse?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/providers")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        callback(gson.fromJson(body, ProvidersResponse::class.java), null)
                    } else { callback(null, "HTTP ${it.code}") }
                }
            }
        })
    }

    fun discoverMovies(page: Int = 1, limit: Int = 20, providers: String = "", sortBy: String = "popularity.desc", callback: (DiscoverMoviesResponse?, String?) -> Unit) {
        val url = "$baseUrl/api/discover/movies?page=$page&limit=$limit&sort_by=$sortBy" + if (providers.isNotEmpty()) "&providers=$providers" else ""
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        callback(gson.fromJson(body, DiscoverMoviesResponse::class.java), null)
                    } else { callback(null, "HTTP ${it.code}") }
                }
            }
        })
    }

    fun discoverShows(page: Int = 1, limit: Int = 20, providers: String = "", sortBy: String = "popularity.desc", callback: (DiscoverShowsResponse?, String?) -> Unit) {
        val url = "$baseUrl/api/discover/shows?page=$page&limit=$limit&sort_by=$sortBy" + if (providers.isNotEmpty()) "&providers=$providers" else ""
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        callback(gson.fromJson(body, DiscoverShowsResponse::class.java), null)
                    } else { callback(null, "HTTP ${it.code}") }
                }
            }
        })
    }

    fun hideDiscover(tmdbId: Int, title: String? = null, year: String? = null, posterUrl: String? = null, type: String = "movie", callback: (Boolean, String?) -> Unit = { _, _ -> }) {
        val bodyMap = mutableMapOf<String, Any>("action" to "hidden")
        title?.let { bodyMap["title"] = it }
        year?.let { bodyMap["year"] = it }
        posterUrl?.let { bodyMap["posterUrl"] = it }
        bodyMap["type"] = type

        val json = gson.toJson(bodyMap)
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/discover/$tmdbId/hide")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use { callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}") }
            }
        })
    }

    fun addMovieFromDiscover(tmdbId: Int, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/discover/$tmdbId/add_movie")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use { callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}") }
            }
        })
    }

    fun addShowFromDiscover(tmdbId: Int, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/discover/$tmdbId/add_show")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use { callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}") }
            }
        })
    }

    fun getDiscoverHistory(callback: (HistoryResponse?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/discover/history")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(null, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        callback(gson.fromJson(body, HistoryResponse::class.java), null)
                    } else { callback(null, "HTTP ${it.code}") }
                }
            }
        })
    }

    fun unhideDiscover(tmdbId: Int, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/discover/$tmdbId/unhide")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use { callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}") }
            }
        })
    }

    fun removeMovieFromDiscover(tmdbId: Int, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/discover/$tmdbId/remove_movie")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use { callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}") }
            }
        })
    }

    fun removeShowFromDiscover(tmdbId: Int, callback: (Boolean, String?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/api/discover/$tmdbId/remove_show")
            .post(RequestBody.create(null, ByteArray(0)))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { callback(false, e.message) }
            override fun onResponse(call: Call, response: Response) {
                response.use { callback(it.isSuccessful, if (it.isSuccessful) null else "HTTP ${it.code}") }
            }
        })
    }
}
