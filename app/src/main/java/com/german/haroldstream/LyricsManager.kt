package com.german.haroldstream

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

object LyricsManager {

    fun parseLrc(lrcText: String): List<LyricLine> {
        if (lrcText.isBlank()) return emptyList()
        val list = mutableListOf<LyricLine>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")
        for (line in lrcText.lines()) {
            val match = regex.find(line)
            if (match != null) {
                val mins = match.groupValues[1].toLongOrNull() ?: 0L
                val secs = match.groupValues[2].toLongOrNull() ?: 0L
                val millisRaw = match.groupValues[3]
                val millis = if (!millisRaw.isNullOrEmpty()) {
                    if (millisRaw.length == 2) millisRaw.toLong() * 10 else millisRaw.toLong()
                } else 0L
                val timeMs = (mins * 60 * 1000) + (secs * 1000) + millis
                val text = line.replace(regex, "").trim()
                if (text.isNotBlank()) {
                    list.add(LyricLine(timeMs, text))
                }
            }
        }
        return list.sortedBy { it.timeMs }
    }

    private const val PREFS_NAME = "harold_lyrics_prefs"

    fun generateKey(cancion: Cancion?): String {
        if (cancion == null) return ""
        val titleClean = (cancion.titulo ?: "").lowercase().trim()
        val canalClean = (cancion.canal ?: "").lowercase().trim()
        return "${titleClean}_$canalClean".replace(Regex("[^a-z0-9_]"), "")
    }

    fun getCustomLyrics(context: Context, key: String): String? {
        if (key.isEmpty()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(key, null)
    }

    fun saveCustomLyrics(context: Context, key: String, lyrics: String) {
        if (key.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, lyrics.trim()).apply()
    }

    suspend fun fetchOnlineLyrics(trackName: String, artistName: String, serverUrl: String = PlayerManager.defaultServerUrl): String? = withContext(Dispatchers.IO) {
        try {
            val encodedTrack = URLEncoder.encode(trackName, "UTF-8")
            val encodedArtist = URLEncoder.encode(artistName, "UTF-8")

            var baseUrl = serverUrl.trim()
            if (!baseUrl.endsWith("/")) baseUrl += "/"
            val serverApiUrl = "${baseUrl}api/letras?titulo=$encodedTrack&artista=$encodedArtist"

            var connection = URL(serverApiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("ngrok-skip-browser-warning", "true")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val letra = json.optString("letra", "")
                if (letra.isNotBlank()) return@withContext letra
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val cleanTrack = trackName.replace(Regex("(?i)\\(.*?\\)|\\[.*?\\]|video oficial|official video|lyric video|audio"), "").trim()
            val cleanArtist = if (artistName != "YouTube" && artistName != "Desconocido") artistName.trim() else ""

            val encodedTrack = URLEncoder.encode(cleanTrack, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")

            // 1. Intentar endpoint directo GET de LRCLIB
            val directUrl = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
            var connection = URL(directUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val plainLyrics = json.optString("plainLyrics", "")
                val syncedLyrics = json.optString("syncedLyrics", "")
                if (plainLyrics.isNotBlank()) return@withContext plainLyrics
                if (syncedLyrics.isNotBlank()) return@withContext cleanSyncedLyrics(syncedLyrics)
            }

            // 2. Fallback: Endpoint de Búsqueda de LRCLIB
            val queryStr = URLEncoder.encode("$cleanTrack $cleanArtist", "UTF-8")
            val searchUrl = "https://lrclib.net/api/search?q=$queryStr"
            connection = URL(searchUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonStr)
                if (jsonArray.length() > 0) {
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val plain = item.optString("plainLyrics", "")
                        val synced = item.optString("syncedLyrics", "")
                        if (plain.isNotBlank()) return@withContext plain
                        if (synced.isNotBlank()) return@withContext cleanSyncedLyrics(synced)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun cleanSyncedLyrics(synced: String): String {
        return synced.lines().map { line ->
            line.replace(Regex("\\[\\d{2}:\\d{2}\\.\\d{2,3}\\]"), "").trim()
        }.filter { it.isNotBlank() }.joinToString("\n")
    }
}
