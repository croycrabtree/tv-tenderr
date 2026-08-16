package com.movieswipe

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.movieswipe.databinding.ActivityMainBinding
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var api: ApiClient
    private val movies = mutableListOf<Movie>()
    private val shows = mutableListOf<Show>()
    private var currentIndex = 0
    private var isLoading = false
    private var mode = "movies" // "movies" or "shows"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val serverUrl = getSharedPreferences("movieswipe", MODE_PRIVATE)
            .getString("server_url", "http://localhost:8899") ?: "http://localhost:8899"
        api = ApiClient(serverUrl)

        setupButtons()
        showSettingsDialogOrLoad()

        binding.btnHistory.setOnClickListener {
            val intent = android.content.Intent(this, HistoryActivity::class.java)
            intent.putExtra("mode", mode)
            startActivity(intent)
        }

        binding.btnRefresh.setOnClickListener {
            api.refreshPlex { ok, _ ->
                runOnUiThread {
                    if (ok) {
                        android.widget.Toast.makeText(this, "🔄 Plex library refreshed", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(this, "❌ Refresh failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        binding.tvMode.setOnClickListener {
            mode = if (mode == "movies") "shows" else "movies"
            binding.tvMode.text = if (mode == "movies") "Movies" else "Shows"
            currentIndex = 0
            movies.clear()
            shows.clear()
            binding.cardContainer.removeAllViews()
            if (mode == "movies") loadMovies() else loadShows()
        }
    }

    private fun showSettingsDialogOrLoad() {
        // For first run, ask for server URL
        val prefs = getSharedPreferences("movieswipe", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", null)

        if (serverUrl == null) {
            val input = EditText(this).apply {
                hint = "http://localhost:8899"
                setPadding(48, 32, 48, 32)
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
            }

                AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_Dialog)
                    .setTitle("Server URL")
                    .setMessage("Enter the Movie Swipe server address")
                    .setView(input)
                    .setPositiveButton("Connect") { _, _ ->
                        val url = input.text.toString().trim()
                        if (url.isNotBlank()) {
                            prefs.edit().putString("server_url", url).apply()
                            api.setUrl(url)
                            loadMovies()
                        }
                    }
                    .setNeutralButton("Use Default") { _, _ ->
                        val defaultUrl = "http://localhost:8899"
                        prefs.edit().putString("server_url", defaultUrl).apply()
                        api.setUrl(defaultUrl)
                        loadMovies()
                    }
                .setCancelable(false)
                .show()
        } else {
            api.setUrl(serverUrl)
            loadMovies()
        }
    }

    private fun setupButtons() {
        binding.btnKeep.setOnClickListener {
            if (mode == "movies") {
                if (movies.isNotEmpty() && currentIndex < movies.size) {
                    val movie = movies[currentIndex]
                    animateCardOut(true) {
                        api.keepMovie(movie.id) { _, _ -> }
                        advanceCard()
                    }
                }
            } else {
                if (shows.isNotEmpty() && currentIndex < shows.size) {
                    val show = shows[currentIndex]
                    animateCardOut(true) {
                        api.keepShow(show.id) { _, _ -> }
                        advanceCard()
                    }
                }
            }
        }

        // Long press = super keep (forever)
        binding.btnKeep.setOnLongClickListener {
            if (mode == "movies") {
                if (movies.isNotEmpty() && currentIndex < movies.size) {
                    val movie = movies[currentIndex]
                    animateCardOut(true) {
                        api.superKeepMovie(movie.id) { _, _ -> }
                        advanceCard()
                    }
                    android.widget.Toast.makeText(this, "⭐ Super Keep: ${movie.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else {
                if (shows.isNotEmpty() && currentIndex < shows.size) {
                    val show = shows[currentIndex]
                    animateCardOut(true) {
                        api.superKeepShow(show.id) { _, _ -> }
                        advanceCard()
                    }
                    android.widget.Toast.makeText(this, "⭐ Super Keep: ${show.title}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            true
        }

        binding.btnBlock.setOnClickListener {
            if (mode == "movies") {
                if (movies.isNotEmpty() && currentIndex < movies.size) {
                    blockWithUndo()
                }
            } else {
                if (shows.isNotEmpty() && currentIndex < shows.size) {
                    blockShowWithUndo()
                }
            }
        }

        binding.btnSkip.setOnClickListener {
            if (mode == "movies") {
                if (movies.isNotEmpty() && currentIndex < movies.size) {
                    val movie = movies[currentIndex]
                    api.skipMovie(movie.id) { _, _ -> }
                    advanceCard()
                }
            } else {
                if (shows.isNotEmpty() && currentIndex < shows.size) {
                    val show = shows[currentIndex]
                    api.skipShow(show.id) { _, _ -> }
                    advanceCard()
                }
            }
        }

        // Long press skip on shows = clean files, keep monitoring
        binding.btnSkip.setOnLongClickListener {
            if (mode == "shows" && shows.isNotEmpty() && currentIndex < shows.size) {
                val show = shows[currentIndex]
                val showIndex = currentIndex
                animateCardOut(false) {
                    showUndoBanner("\"${show.title}\" files will be removed") {
                        // Undo - re-monitor episodes
                        api.uncleanShow(show.id) { _, _ -> }
                        shows.add(showIndex, show)
                        currentIndex = showIndex
                        showCurrentCard()
                    }
                    pendingBlock = {
                        api.cleanShow(show.id) { ok, _ ->
                            runOnUiThread {
                                if (ok) android.widget.Toast.makeText(this, "🧹 Cleaned: ${show.title}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    advanceCard()
                }
            }
            true
        }
    }

    private fun blockWithUndo() {
        val movie = movies[currentIndex]
        val movieIndex = currentIndex
        animateCardOut(false) {
            showUndoBanner("\"${movie.title}\" will be deleted") {
                movies.add(movieIndex, movie)
                currentIndex = movieIndex
                showCurrentCard()
            }
            pendingBlock = { api.blockMovie(movie.id) { _, _ -> } }
            advanceCard()
        }
    }

    private fun blockShowWithUndo() {
        val show = shows[currentIndex]
        val showIndex = currentIndex
        animateCardOut(false) {
            showUndoBanner("\"${show.title}\" will be deleted") {
                shows.add(showIndex, show)
                currentIndex = showIndex
                showCurrentCard()
            }
            pendingBlock = { api.blockShow(show.id) { _, _ -> } }
            advanceCard()
        }
    }

    private var undoHandler: android.os.Handler? = null
    private var undoRunnable: Runnable? = null
    private var pendingBlock: (() -> Unit)? = null

    private fun showUndoBanner(message: String, onUndo: () -> Unit) {
        val banner = findViewById<LinearLayout>(R.id.undoBanner)
        val tvMessage = findViewById<TextView>(R.id.tvUndoMessage)
        val btnUndo = findViewById<Button>(R.id.btnUndoAction)

        // Cancel any existing timer and pending block
        undoRunnable?.let { undoHandler?.removeCallbacks(it) }
        pendingBlock = null

        tvMessage.text = message
        btnUndo.setOnClickListener {
            onUndo()
            banner.visibility = View.GONE
            undoRunnable?.let { undoHandler?.removeCallbacks(it) }
            pendingBlock = null
        }
        banner.visibility = View.VISIBLE

        // After 8 seconds, execute the block and hide banner
        undoHandler = android.os.Handler(mainLooper)
        undoRunnable = Runnable {
            banner.visibility = View.GONE
            pendingBlock?.invoke()
            pendingBlock = null
        }
        undoHandler?.postDelayed(undoRunnable!!, 10000)
    }

    private fun loadMovies() {
        if (isLoading) return
        isLoading = true
        binding.tvStats.text = "Loading movies..."

        api.getMovies(skip = currentIndex, limit = 20) { response, error ->
            runOnUiThread {
                isLoading = false
                if (error != null) {
                    binding.tvStats.text = "Error: $error"
                    return@runOnUiThread
                }
                if (response != null && response.movies.isNotEmpty()) {
                    movies.clear()
                    movies.addAll(response.movies)
                    currentIndex = 0
                    binding.tvStats.text = "${response.total} movies"
                    showCurrentCard()
                } else {
                    binding.tvStats.text = "No movies to review!"
                }
            }
        }
    }

    private fun loadShows() {
        if (isLoading) return
        isLoading = true
        binding.tvStats.text = "Loading shows..."

        api.getShows(skip = currentIndex, limit = 20) { response, error ->
            runOnUiThread {
                isLoading = false
                if (error != null) {
                    binding.tvStats.text = "Error: $error"
                    return@runOnUiThread
                }
                if (response != null && response.shows.isNotEmpty()) {
                    shows.clear()
                    shows.addAll(response.shows)
                    currentIndex = 0
                    binding.tvStats.text = "${response.total} shows"
                    showCurrentCard()
                } else {
                    binding.tvStats.text = "No shows to review!"
                }
            }
        }
    }

    private fun showCurrentCard() {
        binding.cardContainer.removeAllViews()

        if (mode == "movies") {
            if (currentIndex >= movies.size) {
                loadMovies()
                return
            }
            val movie = movies[currentIndex]
            val cardView = createMovieCardView(movie)
            binding.cardContainer.addView(cardView)
            setupMovieSwipeGesture(cardView as CardView, movie)
        } else {
            if (currentIndex >= shows.size) {
                loadShows()
                return
            }
            val show = shows[currentIndex]
            val cardView = createShowCardView(show)
            binding.cardContainer.addView(cardView)
            setupShowSwipeGesture(cardView as CardView, show)
        }
    }

    private fun createMovieCardView(movie: Movie): View {
        val cardView = LayoutInflater.from(this).inflate(R.layout.card_movie, binding.cardContainer, false) as CardView

        val ivPoster = cardView.findViewById<ImageView>(R.id.ivPoster)
        val tvTitle = cardView.findViewById<TextView>(R.id.tvTitle)
        val tvYear = cardView.findViewById<TextView>(R.id.tvYear)
        val tvSize = cardView.findViewById<TextView>(R.id.tvSize)
        val tvRating = cardView.findViewById<TextView>(R.id.tvRating)
        val tvGenres = cardView.findViewById<TextView>(R.id.tvGenres)
        val tvOverview = cardView.findViewById<TextView>(R.id.tvOverview)
        val tvKeepLabel = cardView.findViewById<TextView>(R.id.tvKeepLabel)
        val tvBlockLabel = cardView.findViewById<TextView>(R.id.tvBlockLabel)

        tvTitle.text = movie.title
        tvYear.text = movie.year?.toString() ?: ""
        tvSize.text = "${movie.sizeGB} GB"
        tvRating.text = movie.rating?.let { String.format("%.1f", it) } ?: ""
        tvGenres.text = movie.genres.joinToString(" · ")
        tvOverview.text = movie.overview

        // Load poster
        movie.posterUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .centerCrop()
                .into(ivPoster)
        }

        return cardView
    }

    private fun setupMovieSwipeGesture(cardView: CardView, movie: Movie) {
        var startX = 0f
        var startY = 0f
        var currentX = 0f
        var currentY = 0f
        val threshold = 200f

        val tvKeepLabel = cardView.findViewById<TextView>(R.id.tvKeepLabel)
        val tvBlockLabel = cardView.findViewById<TextView>(R.id.tvBlockLabel)

        cardView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    currentX = event.rawX - startX
                    currentY = event.rawY - startY

                    if (abs(currentX) > abs(currentY) && abs(currentX) > 10) {
                        // Horizontal swipe
                        v.translationX = currentX
                        v.rotation = currentX / 30f

                        if (currentX > 50) {
                            tvKeepLabel.visibility = View.VISIBLE
                            tvKeepLabel.text = "KEEP"
                            tvKeepLabel.alpha = minOf(abs(currentX) / threshold, 1f)
                            tvKeepLabel.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
                            tvBlockLabel.visibility = View.GONE
                        } else if (currentX < -50) {
                            tvBlockLabel.visibility = View.VISIBLE
                            tvBlockLabel.alpha = minOf(abs(currentX) / threshold, 1f)
                            tvKeepLabel.visibility = View.GONE
                        }
                        true
                    } else if (currentY > 50 && abs(currentY) > abs(currentX)) {
                        // Vertical swipe down
                        v.translationY = currentY * 0.5f
                        tvKeepLabel.visibility = View.VISIBLE
                        tvKeepLabel.text = "⭐ SUPER"
                        tvKeepLabel.alpha = minOf(abs(currentY) / threshold, 1f)
                        tvKeepLabel.setTextColor(resources.getColor(android.R.color.holo_orange_light, theme))
                        tvBlockLabel.visibility = View.GONE
                        true
                    } else if (currentY < -50 && abs(currentY) > abs(currentX)) {
                        // Vertical swipe up = skip
                        v.translationY = currentY * 0.5f
                        tvBlockLabel.visibility = View.VISIBLE
                        tvBlockLabel.text = "SKIP"
                        tvBlockLabel.alpha = minOf(abs(currentY) / threshold, 1f)
                        tvBlockLabel.setTextColor(resources.getColor(android.R.color.holo_orange_light, theme))
                        tvKeepLabel.visibility = View.GONE
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (abs(currentX) > threshold && abs(currentX) > abs(currentY)) {
                        val goRight = currentX > 0
                        if (goRight) {
                            animateCardOut(true) {
                                api.keepMovie(movie.id) { _, _ -> }
                                advanceCard()
                            }
                        } else {
                            blockWithUndo()
                        }
                    } else if (currentY > threshold && abs(currentY) > abs(currentX)) {
                        // Swipe down = super keep
                        animateCardDown {
                            api.superKeepMovie(movie.id) { _, _ -> }
                            advanceCard()
                        }
                    } else if (currentY < -threshold && abs(currentY) > abs(currentX)) {
                        // Swipe up = skip
                        animateCardUp {
                            api.skipMovie(movie.id) { _, _ -> }
                            advanceCard()
                        }
                    } else {
                        v.animate().translationX(0f).translationY(0f).rotation(0f).setDuration(200).start()
                        tvKeepLabel.visibility = View.GONE
                        tvBlockLabel.visibility = View.GONE
                    }
                    currentX = 0f
                    currentY = 0f
                    true
                }
                else -> false
            }
        }
    }

    private fun animateCardOut(goRight: Boolean, onEnd: () -> Unit) {
        val card = binding.cardContainer.getChildAt(0) ?: run { onEnd(); return }
        val targetX = if (goRight) card.width.toFloat() else -card.width.toFloat()

        val animX = ObjectAnimator.ofFloat(card, "translationX", targetX)
        val animR = ObjectAnimator.ofFloat(card, "rotation", if (goRight) 30f else -30f)
        val animA = ObjectAnimator.ofFloat(card, "alpha", 0f)

        AnimatorSet().apply {
            playTogether(animX, animR, animA)
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    private fun advanceCard() {
        currentIndex++
        showCurrentCard()
    }

    private fun createShowCardView(show: Show): View {
        val cardView = LayoutInflater.from(this).inflate(R.layout.card_movie, binding.cardContainer, false) as CardView

        val ivPoster = cardView.findViewById<ImageView>(R.id.ivPoster)
        val tvTitle = cardView.findViewById<TextView>(R.id.tvTitle)
        val tvYear = cardView.findViewById<TextView>(R.id.tvYear)
        val tvSize = cardView.findViewById<TextView>(R.id.tvSize)
        val tvRating = cardView.findViewById<TextView>(R.id.tvRating)
        val tvGenres = cardView.findViewById<TextView>(R.id.tvGenres)
        val tvOverview = cardView.findViewById<TextView>(R.id.tvOverview)

        tvTitle.text = show.title
        tvYear.text = show.year?.toString() ?: ""
        val statusLabel = if (show.status == "continuing") "🟢 Continuing" else "🔴 Ended"
        tvSize.text = "${show.sizeGB} GB · ${show.episodeCount} eps · $statusLabel"
        tvRating.text = show.rating?.let { String.format("%.1f", it) } ?: ""
        tvGenres.text = show.genres.joinToString(" · ")
        tvOverview.text = show.overview

        show.posterUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .centerCrop()
                .into(ivPoster)
        }

        return cardView
    }

    private fun setupShowSwipeGesture(cardView: CardView, show: Show) {
        var startX = 0f
        var startY = 0f
        var currentX = 0f
        var currentY = 0f
        val threshold = 200f

        val tvKeepLabel = cardView.findViewById<TextView>(R.id.tvKeepLabel)
        val tvBlockLabel = cardView.findViewById<TextView>(R.id.tvBlockLabel)

        cardView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    currentX = event.rawX - startX
                    currentY = event.rawY - startY

                    if (abs(currentX) > abs(currentY) && abs(currentX) > 10) {
                        v.translationX = currentX
                        v.rotation = currentX / 30f

                        if (currentX > 50) {
                            tvKeepLabel.visibility = View.VISIBLE
                            tvKeepLabel.text = "KEEP"
                            tvKeepLabel.alpha = minOf(abs(currentX) / threshold, 1f)
                            tvKeepLabel.setTextColor(resources.getColor(android.R.color.holo_green_light, theme))
                            tvBlockLabel.visibility = View.GONE
                        } else if (currentX < -50) {
                            tvBlockLabel.visibility = View.VISIBLE
                            tvBlockLabel.alpha = minOf(abs(currentX) / threshold, 1f)
                            tvKeepLabel.visibility = View.GONE
                        }
                        true
                    } else if (currentY > 50 && abs(currentY) > abs(currentX)) {
                        v.translationY = currentY * 0.5f
                        tvKeepLabel.visibility = View.VISIBLE
                        tvKeepLabel.text = "⭐ SUPER"
                        tvKeepLabel.alpha = minOf(abs(currentY) / threshold, 1f)
                        tvKeepLabel.setTextColor(resources.getColor(android.R.color.holo_orange_light, theme))
                        tvBlockLabel.visibility = View.GONE
                        true
                    } else if (currentY < -50 && abs(currentY) > abs(currentX)) {
                        v.translationY = currentY * 0.5f
                        tvBlockLabel.visibility = View.VISIBLE
                        tvBlockLabel.text = "SKIP"
                        tvBlockLabel.alpha = minOf(abs(currentY) / threshold, 1f)
                        tvBlockLabel.setTextColor(resources.getColor(android.R.color.holo_orange_light, theme))
                        tvKeepLabel.visibility = View.GONE
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (abs(currentX) > threshold && abs(currentX) > abs(currentY)) {
                        val goRight = currentX > 0
                        if (goRight) {
                            animateCardOut(true) {
                                api.keepShow(show.id) { _, _ -> }
                                advanceCard()
                            }
                        } else {
                            blockShowWithUndo()
                        }
                    } else if (currentY > threshold && abs(currentY) > abs(currentX)) {
                        animateCardDown {
                            api.superKeepShow(show.id) { _, _ -> }
                            advanceCard()
                        }
                    } else if (currentY < -threshold && abs(currentY) > abs(currentX)) {
                        animateCardUp {
                            api.skipShow(show.id) { _, _ -> }
                            advanceCard()
                        }
                    } else {
                        v.animate().translationX(0f).translationY(0f).rotation(0f).setDuration(200).start()
                        tvKeepLabel.visibility = View.GONE
                        tvBlockLabel.visibility = View.GONE
                    }
                    currentX = 0f
                    currentY = 0f
                    true
                }
                else -> false
            }
        }
    }

    private fun animateCardDown(onEnd: () -> Unit) {
        val card = binding.cardContainer.getChildAt(0) ?: run { onEnd(); return }

        val animY = ObjectAnimator.ofFloat(card, "translationY", card.height.toFloat())
        val animA = ObjectAnimator.ofFloat(card, "alpha", 0f)

        AnimatorSet().apply {
            playTogether(animY, animA)
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    private fun animateCardUp(onEnd: () -> Unit) {
        val card = binding.cardContainer.getChildAt(0) ?: run { onEnd(); return }

        val animY = ObjectAnimator.ofFloat(card, "translationY", -card.height.toFloat())
        val animA = ObjectAnimator.ofFloat(card, "alpha", 0f)

        AnimatorSet().apply {
            playTogether(animY, animA)
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            })
            start()
        }
    }
}
