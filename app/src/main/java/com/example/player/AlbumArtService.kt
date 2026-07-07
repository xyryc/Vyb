package com.example.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object AlbumArtService {
    private const val TAG = "AlbumArtService"
    private val client = OkHttpClient()

    private fun cleanText(text: String): String {
        var cleaned = text.trim()
        if (cleaned.lowercase().endsWith(".mp3")) {
            cleaned = cleaned.substring(0, cleaned.length - 4)
        }
        // Remove brackets and parentheses metadata
        cleaned = cleaned.replace(Regex("\\s*\\([^)]*\\)"), "")
        cleaned = cleaned.replace(Regex("\\s*\\[[^]]*\\]"), "")
        cleaned = cleaned.replace(Regex("\\s*\\{[^}]*\\}"), "")
        return cleaned.replace(Regex("\\s+"), " ").trim()
    }

    suspend fun fetchAlbumArt(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val cleanArtist = cleanText(artist).let { if (it.equals("Unknown Artist", ignoreCase = true) || it.equals("Unknown", ignoreCase = true)) "" else it }
        val cleanTitle = cleanText(title)

        if (cleanTitle.isEmpty() || cleanTitle.equals("Local Audio", ignoreCase = true)) {
            return@withContext null
        }

        // Generate search term candidates to increase hit rate
        val searchTerms = mutableListOf<String>()
        if (cleanArtist.isNotEmpty()) {
            searchTerms.add("$cleanArtist $cleanTitle")
            val primaryArtist = getPrimaryArtist(cleanArtist)
            if (primaryArtist != cleanArtist) {
                searchTerms.add("$primaryArtist $cleanTitle")
            }
        } else {
            val hyphenIdx = findSeparatorIndex(cleanTitle)
            if (hyphenIdx != -1) {
                val part1 = cleanTitle.substring(0, hyphenIdx).trim()
                val part2 = cleanTitle.substring(hyphenIdx + 1).trim()
                searchTerms.add("$part1 $part2")
            } else {
                searchTerms.add(cleanTitle)
            }
        }

        for (term in searchTerms.distinct()) {
            try {
                val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=song&limit=3"
                Log.d(TAG, "Querying iTunes Search API: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Vyb-Music-Player/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "iTunes API error: ${response.code}")
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        for (i in 0 until results.length()) {
                            val result = results.getJSONObject(i)
                            val artworkUrl100 = result.optString("artworkUrl100", "")
                            if (artworkUrl100.isNotEmpty()) {
                                // Convert to high resolution (600x600bb is high quality and responsive)
                                val hqArtworkUrl = artworkUrl100.replace("100x100bb", "600x600bb")
                                    .replace("100x100", "600x600")
                                Log.d(TAG, "Found artwork for term '$term': $hqArtworkUrl")
                                return@withContext hqArtworkUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching artwork for term '$term'", e)
            }
        }
        null
    }

    private fun getPrimaryArtist(artist: String): String {
        val separators = listOf(
            "(?i)\\s+x\\s+",
            "(?i)\\s+feat\\.?\\s+",
            "(?i)\\s+ft\\.?\\s+",
            "(?i)\\s+and\\s+",
            "\\s*&\\s*",
            "\\s*,\\s*"
        )
        var primary = artist
        for (sep in separators) {
            val parts = primary.split(Regex(sep))
            if (parts.isNotEmpty()) {
                primary = parts[0].trim()
            }
        }
        return primary
    }

    private fun findSeparatorIndex(text: String): Int {
        val separators = listOf(" - ", " – ", " — ", " ~ ", " : ", " | ", " / ")
        for (sep in separators) {
            val idx = text.indexOf(sep)
            if (idx != -1) {
                return idx + sep.length / 2
            }
        }
        return -1
    }
}
