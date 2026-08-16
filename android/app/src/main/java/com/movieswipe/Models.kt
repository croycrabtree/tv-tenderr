package com.movieswipe

data class Movie(
    val id: Int,
    val title: String,
    val year: Int?,
    val overview: String?,
    val genres: List<String>,
    val rating: Double?,
    val runtime: Int?,
    val sizeGB: Double,
    val posterUrl: String?,
    val hasFile: Boolean,
    val monitored: Boolean
)

data class MoviesResponse(
    val movies: List<Movie>,
    val total: Int
)

data class StatsResponse(
    val totalMovies: Int,
    val kept: Int,
    val blocked: Int,
    val skipped: Int,
    val undecided: Int
)

data class HistoryItem(
    val movieId: Int,
    val action: String,
    val timestamp: String?,
    val title: String?,
    val year: Int?,
    val tmdbId: Int?,
    val posterUrl: String?,
    val deletedFiles: Int?,
    val freedGB: Double?
)

data class HistoryResponse(
    val history: List<HistoryItem>
)

data class Show(
    val id: Int,
    val title: String,
    val year: Int?,
    val overview: String?,
    val genres: List<String>,
    val rating: Double?,
    val seasonCount: Int,
    val episodeCount: Int,
    val sizeGB: Double,
    val posterUrl: String?,
    val status: String?,
    val monitored: Boolean,
    val network: String?
)

data class ShowsResponse(
    val shows: List<Show>,
    val total: Int
)
