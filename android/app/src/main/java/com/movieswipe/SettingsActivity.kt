package com.movieswipe

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.movieswipe.databinding.ActivitySettingsBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("movieswipe", MODE_PRIVATE)
        val serverUrl = prefs.getString("server_url", "http://localhost:8899") ?: "http://localhost:8899"

        // Load saved local settings
        binding.etBackendUrl.setText(serverUrl)
        binding.etRadarrUrl.setText(prefs.getString("radarr_url", ""))
        binding.etRadarrKey.setText(prefs.getString("radarr_key", ""))
        binding.etSonarrUrl.setText(prefs.getString("sonarr_url", ""))
        binding.etSonarrKey.setText(prefs.getString("sonarr_key", ""))
        binding.etPlexUrl.setText(prefs.getString("plex_url", ""))
        binding.etPlexToken.setText(prefs.getString("plex_token", ""))

        // Fetch current config from backend
        loadConfigFromBackend(serverUrl)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val editor = prefs.edit()
            editor.putString("server_url", binding.etBackendUrl.text.toString().trim())
            editor.putString("radarr_url", binding.etRadarrUrl.text.toString().trim())
            editor.putString("radarr_key", binding.etRadarrKey.text.toString().trim())
            editor.putString("sonarr_url", binding.etSonarrUrl.text.toString().trim())
            editor.putString("sonarr_key", binding.etSonarrKey.text.toString().trim())
            editor.putString("plex_url", binding.etPlexUrl.text.toString().trim())
            editor.putString("plex_token", binding.etPlexToken.text.toString().trim())
            editor.apply()

            val config = mapOf(
                "radarrUrl" to binding.etRadarrUrl.text.toString().trim(),
                "radarrKey" to binding.etRadarrKey.text.toString().trim(),
                "sonarrUrl" to binding.etSonarrUrl.text.toString().trim(),
                "sonarrKey" to binding.etSonarrKey.text.toString().trim(),
                "plexUrl" to binding.etPlexUrl.text.toString().trim(),
                "plexToken" to binding.etPlexToken.text.toString().trim()
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
                    runOnUiThread {
                        binding.tvStatus.text = "Saved locally (backend unreachable)"
                    }
                }
                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        binding.tvStatus.text = "Settings saved and backend updated"
                        Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    private fun loadConfigFromBackend(serverUrl: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$serverUrl/api/config")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Backend unreachable, use local values
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        val body = it.body?.string()
                        val config = com.google.gson.Gson().fromJson(body, Map::class.java) as Map<*, *>
                        runOnUiThread {
                            // Only fill empty fields from backend
                            if (binding.etRadarrUrl.text.isEmpty()) {
                                binding.etRadarrUrl.setText(config["radarrUrl"]?.toString() ?: "")
                            }
                            if (binding.etRadarrKey.text.isEmpty()) {
                                binding.etRadarrKey.setText(config["radarrKey"]?.toString() ?: "")
                            }
                            if (binding.etSonarrUrl.text.isEmpty()) {
                                binding.etSonarrUrl.setText(config["sonarrUrl"]?.toString() ?: "")
                            }
                            if (binding.etSonarrKey.text.isEmpty()) {
                                binding.etSonarrKey.setText(config["sonarrKey"]?.toString() ?: "")
                            }
                            if (binding.etPlexUrl.text.isEmpty()) {
                                binding.etPlexUrl.setText(config["plexUrl"]?.toString() ?: "")
                            }
                            if (binding.etPlexToken.text.isEmpty()) {
                                binding.etPlexToken.setText(config["plexToken"]?.toString() ?: "")
                            }
                            binding.tvStatus.text = "Loaded config from backend"
                        }
                    }
                }
            }
        })
    }
}
