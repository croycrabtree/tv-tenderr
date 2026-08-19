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
    val monitored: Boolean,
    val cast: List<String>?,
    val watched: Boolean,
    val imdbId: String?,
    val trailerId: String?
)

data class MoviesResponse(
    val movies: List<Movie>,
    val total: Int
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
    val freedGB: Double?,
    val type: String?
)

data class StatsSection(
    val total: Int,
    val kept: Int,
    val superKept: Int,
    val blocked: Int,
    val skipped: Int,
    val undecided: Int
)

data class DiscoverStats(
    val added: Int,
    val hidden: Int
)

data class StatsResponse(
    val movies: StatsSection,
    val shows: StatsSection,
    val discover: DiscoverStats
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
    val network: String?,
    val cast: List<String>?,
    val watched: Boolean,
    val imdbId: String?,
    val trailerId: String?
)

data class ShowsResponse(
    val shows: List<Show>,
    val total: Int
)

data class DiscoverItem(
    val tmdbId: Int,
    val title: String,
    val year: String?,
    val overview: String?,
    val rating: Double?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val cast: List<String>?
)

data class DiscoverMoviesResponse(
    val movies: List<DiscoverItem>,
    val total: Int
)

data class DiscoverShowsResponse(
    val shows: List<DiscoverItem>,
    val total: Int
)

data class Provider(
    val id: Int,
    val name: String,
    val logo: String?
)

data class ProvidersResponse(
    val movie_providers: List<Provider>,
    val tv_providers: List<Provider>
)
