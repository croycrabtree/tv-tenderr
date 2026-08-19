package com.movieswipe

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.movieswipe.databinding.ActivitySettingsBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var api: ApiClient
    private val radarrProfiles = mutableListOf<Pair<Int, String>>()
    private val sonarrProfiles = mutableListOf<Pair<Int, String>>()
    private val radarrRoots = mutableListOf<String>()
    private val sonarrRoots = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("movieswipe", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://localhost:8899") ?: "http://localhost:8899"
        api = ApiClient(serverUrl)

        // Load saved settings
        binding.etBackendUrl.setText(serverUrl)
        binding.etRadarrUrl.setText(prefs.getString("radarr_url", "http://localhost:7878"))
        binding.etRadarrKey.setText(prefs.getString("radarr_key", "YOUR_RADARR_API_KEY"))
        binding.etSonarrUrl.setText(prefs.getString("sonarr_url", "http://localhost:8989"))
        binding.etSonarrKey.setText(prefs.getString("sonarr_key", "YOUR_SONARR_API_KEY"))
        binding.etPlexUrl.setText(prefs.getString("plex_url", "http://localhost:32400"))
        binding.etPlexToken.setText(prefs.getString("plex_token", "YOUR_PLEX_TOKEN"))
        binding.etTmdbKey.setText(prefs.getString("tmdb_key", "YOUR_TMDB_KEY"))

        // Load quality/root options from backend
        loadQualityProfiles(serverUrl)

        binding.btnBack.setOnClickListener { finish() }

        // Version info and update check
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
        binding.tvCurrentVersion.text = "v$versionName"

        binding.btnCheckUpdates.setOnClickListener {
            binding.tvUpdateStatus.text = "Checking..."
            binding.tvUpdateStatus.setTextColor(android.graphics.Color.parseColor("#8B949E"))
            api.checkForUpdates { info, error ->
                runOnUiThread {
                    if (error != null) {
                        binding.tvUpdateStatus.text = "Check failed"
                        binding.tvUpdateStatus.setTextColor(android.graphics.Color.parseColor("#e74c3c"))
                    } else if (info != null) {
                        val latestVersion = info.version ?: "0"
                        if (isNewerVersion(latestVersion, versionName)) {
                            binding.tvUpdateStatus.text = "⬆ Update available: v${info.version}"
                            binding.tvUpdateStatus.setTextColor(android.graphics.Color.parseColor("#2ecc71"))
                            binding.tvUpdateStatus.setOnClickListener {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(info.htmlUrl ?: "https://github.com/croycrabtree/tv-tenderr/releases/latest"))
                                startActivity(intent)
                            }
                        } else {
                            binding.tvUpdateStatus.text = "✓ You're on the latest version"
                            binding.tvUpdateStatus.setTextColor(android.graphics.Color.parseColor("#8B949E"))
                            binding.tvUpdateStatus.setOnClickListener(null)
                        }
                    }
                }
            }
        }

        binding.btnSave.setOnClickListener {
            val editor = prefs.edit()
            editor.putString("server_url", binding.etBackendUrl.text.toString().trim())
            editor.putString("radarr_url", binding.etRadarrUrl.text.toString().trim())
            editor.putString("radarr_key", binding.etRadarrKey.text.toString().trim())
            editor.putString("sonarr_url", binding.etSonarrUrl.text.toString().trim())
            editor.putString("sonarr_key", binding.etSonarrKey.text.toString().trim())
            editor.putString("plex_url", binding.etPlexUrl.text.toString().trim())
            editor.putString("plex_token", binding.etPlexToken.text.toString().trim())
            editor.putString("tmdb_key", binding.etTmdbKey.text.toString().trim())

            // Save quality selections
            val radarrQualityPos = binding.spinnerRadarrQuality.selectedItemPosition
            if (radarrQualityPos >= 0 && radarrQualityPos < radarrProfiles.size) {
                editor.putInt("radarr_quality_id", radarrProfiles[radarrQualityPos].first)
                editor.putString("radarr_quality_name", radarrProfiles[radarrQualityPos].second)
            }

            val sonarrQualityPos = binding.spinnerSonarrQuality.selectedItemPosition
            if (sonarrQualityPos >= 0 && sonarrQualityPos < sonarrProfiles.size) {
                editor.putInt("sonarr_quality_id", sonarrProfiles[sonarrQualityPos].first)
                editor.putString("sonarr_quality_name", sonarrProfiles[sonarrQualityPos].second)
            }

            val radarrRootPos = binding.spinnerRadarrRoot.selectedItemPosition
            if (radarrRootPos >= 0 && radarrRootPos < radarrRoots.size) {
                editor.putString("radarr_root", radarrRoots[radarrRootPos])
            }

            val sonarrRootPos = binding.spinnerSonarrRoot.selectedItemPosition
            if (sonarrRootPos >= 0 && sonarrRootPos < sonarrRoots.size) {
                editor.putString("sonarr_root", sonarrRoots[sonarrRootPos])
            }

            editor.apply()

            // Push config to backend
            val config = mapOf(
                "radarrUrl" to binding.etRadarrUrl.text.toString().trim(),
                "radarrKey" to binding.etRadarrKey.text.toString().trim(),
                "sonarrUrl" to binding.etSonarrUrl.text.toString().trim(),
                "sonarrKey" to binding.etSonarrKey.text.toString().trim(),
                "plexUrl" to binding.etPlexUrl.text.toString().trim(),
                "plexToken" to binding.etPlexToken.text.toString().trim(),
                "tmdbKey" to binding.etTmdbKey.text.toString().trim()
            )

            val client = OkHttpClient()
            val json = com.google.gson.Gson().toJson(config)
            val body = json.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${binding.etBackendUrl.text.toString().trim()}/api/config")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread { binding.tvStatus.text = "Saved locally (backend unreachable)" }
                }
                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        binding.tvStatus.text = "Settings saved"
                        Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun loadQualityProfiles(serverUrl: String) {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url("$serverUrl/api/config")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val config = com.google.gson.Gson().fromJson(body, Map::class.java) as Map<*, *>
                        val radarrUrl = config["radarrUrl"]?.toString() ?: return@use
                        val radarrKey = config["radarrKey"]?.toString() ?: return@use
                        val sonarrUrl = config["sonarrUrl"]?.toString() ?: return@use
                        val sonarrKey = config["sonarrKey"]?.toString() ?: return@use

                        runOnUiThread {
                            if (binding.etRadarrUrl.text.isEmpty()) binding.etRadarrUrl.setText(radarrUrl)
                            if (binding.etRadarrKey.text.isEmpty()) binding.etRadarrKey.setText(radarrKey)
                            if (binding.etSonarrUrl.text.isEmpty()) binding.etSonarrUrl.setText(sonarrUrl)
                            if (binding.etSonarrKey.text.isEmpty()) binding.etSonarrKey.setText(sonarrKey)
                            if (binding.etPlexUrl.text.isEmpty()) binding.etPlexUrl.setText(config["plexUrl"]?.toString() ?: "")
                            if (binding.etPlexToken.text.isEmpty()) binding.etPlexToken.setText(config["plexToken"]?.toString() ?: "")
                            if (binding.etTmdbKey.text.isEmpty()) binding.etTmdbKey.setText(config["tmdbKey"]?.toString() ?: "")
                        }

                        fetchProfiles(radarrUrl, radarrKey, "radarr")
                        fetchRoots(radarrUrl, radarrKey, "radarr")
                        fetchProfiles(sonarrUrl, sonarrKey, "sonarr")
                        fetchRoots(sonarrUrl, sonarrKey, "sonarr")
                    }
                }
            }
        })
    }

    private fun fetchProfiles(baseUrl: String, apiKey: String, type: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$baseUrl/api/v3/qualityprofile")
            .addHeader("X-Api-Key", apiKey)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val profiles = com.google.gson.Gson().fromJson(body, Array<QualityProfile>::class.java)
                        runOnUiThread {
                            if (type == "radarr") {
                                radarrProfiles.clear()
                                radarrProfiles.addAll(profiles.map { p -> Pair(p.id, p.name) })
                                val adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, radarrProfiles.map { it.second })
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                binding.spinnerRadarrQuality.adapter = adapter
                                val savedId = getSharedPreferences("movieswipe", MODE_PRIVATE).getInt("radarr_quality_id", 4)
                                val pos = radarrProfiles.indexOfFirst { it.first == savedId }
                                if (pos >= 0) binding.spinnerRadarrQuality.setSelection(pos)
                            } else {
                                sonarrProfiles.clear()
                                sonarrProfiles.addAll(profiles.map { p -> Pair(p.id, p.name) })
                                val adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, sonarrProfiles.map { it.second })
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                binding.spinnerSonarrQuality.adapter = adapter
                                val savedId = getSharedPreferences("movieswipe", MODE_PRIVATE).getInt("sonarr_quality_id", 4)
                                val pos = sonarrProfiles.indexOfFirst { it.first == savedId }
                                if (pos >= 0) binding.spinnerSonarrQuality.setSelection(pos)
                            }
                        }
                    }
                }
            }
        })
    }

    private fun fetchRoots(baseUrl: String, apiKey: String, type: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$baseUrl/api/v3/rootfolder")
            .addHeader("X-Api-Key", apiKey)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val folders = com.google.gson.Gson().fromJson(body, Array<RootFolder>::class.java)
                        runOnUiThread {
                            if (type == "radarr") {
                                radarrRoots.clear()
                                radarrRoots.addAll(folders.map { it.path })
                                val adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, radarrRoots)
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                binding.spinnerRadarrRoot.adapter = adapter
                                val saved = getSharedPreferences("movieswipe", MODE_PRIVATE).getString("radarr_root", "H:\\")
                                val pos = radarrRoots.indexOf(saved)
                                if (pos >= 0) binding.spinnerRadarrRoot.setSelection(pos)
                            } else {
                                sonarrRoots.clear()
                                sonarrRoots.addAll(folders.map { it.path })
                                val adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, sonarrRoots)
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                binding.spinnerSonarrRoot.adapter = adapter
                                val saved = getSharedPreferences("movieswipe", MODE_PRIVATE).getString("sonarr_root", "I:\\TV")
                                val pos = sonarrRoots.indexOf(saved)
                                if (pos >= 0) binding.spinnerSonarrRoot.setSelection(pos)
                            }
                        }
                    }
                }
            }
        })
    }
}

data class QualityProfile(val id: Int, val name: String)
data class RootFolder(val path: String)

private fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
    val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
    val maxLen = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until maxLen) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l > c) return true
        if (l < c) return false
    }
    return false
}
