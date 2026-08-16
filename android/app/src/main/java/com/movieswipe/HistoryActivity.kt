package com.movieswipe

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.movieswipe.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var api: ApiClient
    private val allItems = mutableListOf<HistoryItem>()
    private var currentFilter = "all"
    private var searchQuery = ""
    private var historyMode = "movies" // "movies" or "shows"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val serverUrl = getSharedPreferences("movieswipe", MODE_PRIVATE)
            .getString("server_url", "http://localhost:8899") ?: "http://localhost:8899"
        api = ApiClient(serverUrl)

        historyMode = intent.getStringExtra("mode") ?: "movies"

        binding.btnBack.setOnClickListener { finish() }

        binding.tvFilter.setOnClickListener {
            currentFilter = when (currentFilter) {
                "all" -> "keep"
                "keep" -> "block"
                "block" -> "all"
                else -> "all"
            }
            binding.tvFilter.text = currentFilter.replaceFirstChar { it.uppercase() }
            refreshList()
        }

        // Add mode toggle via the filter button long press
        binding.tvFilter.setOnLongClickListener {
            historyMode = if (historyMode == "movies") "shows" else "movies"
            binding.tvFilter.text = "All"
            currentFilter = "all"
            loadHistory()
            true
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                refreshList()
            }
        })

        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun loadHistory() {
        if (historyMode == "movies") {
            api.getHistory { response, error ->
                runOnUiThread {
                    if (error != null) return@runOnUiThread
                    if (response != null) {
                        allItems.clear()
                        allItems.addAll(response.history)
                        refreshList()
                    }
                }
            }
        } else {
            api.getShowHistory { response, error ->
                runOnUiThread {
                    if (error != null) return@runOnUiThread
                    if (response != null) {
                        allItems.clear()
                        allItems.addAll(response.history)
                        refreshList()
                    }
                }
            }
        }
    }

    private fun refreshList() {
        var filtered = if (currentFilter == "all") allItems.toList()
            else allItems.filter { it.action == currentFilter }

        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter {
                (it.title?.lowercase()?.contains(searchQuery) == true) ||
                (it.year?.toString()?.contains(searchQuery) == true)
            }
        }

        binding.rvHistory.adapter = HistoryAdapter(filtered,
            onUnkeep = { item ->
                if (historyMode == "movies") {
                    api.unkeepMovie(item.movieId) { ok, _ ->
                        if (ok) {
                            runOnUiThread {
                                allItems.removeAll { it.movieId == item.movieId }
                                refreshList()
                            }
                        }
                    }
                } else {
                    api.postShowAction(item.movieId, "unkeep") { ok, _ ->
                        if (ok) {
                            runOnUiThread {
                                allItems.removeAll { it.movieId == item.movieId }
                                refreshList()
                            }
                        }
                    }
                }
            },
            onUnblock = { item ->
                if (historyMode == "movies") {
                    api.unblockMovie(item.movieId) { ok, _ ->
                        if (ok) {
                            runOnUiThread {
                                allItems.removeAll { it.movieId == item.movieId }
                                refreshList()
                            }
                        }
                    }
                } else {
                    api.postShowAction(item.movieId, "unblock") { ok, _ ->
                        if (ok) {
                            runOnUiThread {
                                allItems.removeAll { it.movieId == item.movieId }
                                refreshList()
                            }
                        }
                    }
                }
            }
        )
    }
}

class HistoryAdapter(
    private val items: List<HistoryItem>,
    private val onUnkeep: (HistoryItem) -> Unit,
    private val onUnblock: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPoster: ImageView = view.findViewById(R.id.ivPoster)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvYear: TextView = view.findViewById(R.id.tvYear)
        val tvAction: TextView = view.findViewById(R.id.tvAction)
        val btnUndo: Button = view.findViewById(R.id.btnUndo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title ?: "Movie #${item.movieId}"
        holder.tvYear.text = item.year?.toString() ?: ""

        when (item.action) {
            "keep" -> {
                holder.tvAction.text = "✓ KEPT (6mo)"
                holder.tvAction.setTextColor(Color.parseColor("#2ecc71"))
                holder.btnUndo.text = "Un-keep"
                holder.btnUndo.visibility = View.VISIBLE
                holder.btnUndo.setOnClickListener { onUnkeep(item) }
            }
            "super_keep" -> {
                holder.tvAction.text = "⭐ SUPER KEEP"
                holder.tvAction.setTextColor(Color.parseColor("#f1c40f"))
                holder.btnUndo.text = "Un-keep"
                holder.btnUndo.visibility = View.VISIBLE
                holder.btnUndo.setOnClickListener { onUnkeep(item) }
            }
            "block" -> {
                holder.tvAction.text = "✗ BLOCKED"
                holder.tvAction.setTextColor(Color.parseColor("#e74c3c"))
                holder.btnUndo.text = "Restore"
                holder.btnUndo.visibility = View.VISIBLE
                holder.btnUndo.setOnClickListener { onUnblock(item) }
            }
            "skip" -> {
                holder.tvAction.text = "→ SKIPPED"
                holder.tvAction.setTextColor(Color.parseColor("#f39c12"))
                holder.btnUndo.visibility = View.GONE
            }
            "clean" -> {
                val freed = item.deletedFiles?.let { " (${it} files)" } ?: ""
                holder.tvAction.text = "🧹 CLEANED$freed"
                holder.tvAction.setTextColor(Color.parseColor("#3498db"))
                holder.btnUndo.visibility = View.GONE
            }
        }

        item.posterUrl?.let { url ->
            Glide.with(holder.itemView.context)
                .load(url)
                .centerCrop()
                .into(holder.ivPoster)
        }
    }

    override fun getItemCount() = items.size
}
