package com.movieswipe

import com.google.gson.Gson
import okhttp3.*
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
}
