package com.example.player

import android.util.Log
import com.example.data.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class LyricLine(
    val timeMs: Long,
    val text: String
)

sealed interface LyricsUiState {
    object Idle : LyricsUiState
    object Loading : LyricsUiState
    data class Success(val syncedLines: List<LyricLine>, val plainLyrics: String?, val isSynced: Boolean) : LyricsUiState
    data class Error(val message: String) : LyricsUiState
}

object LyricsService {
    private const val TAG = "LyricsService"
    private val client = OkHttpClient()

    /**
     * Parse LRC lyrics format into a list of [LyricLine]
     */
    fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = lrcContent.split("\n")
        val result = mutableListOf<LyricLine>()
        // Regular expression to match timestamps like [00:12.34], [00:12.345], [00:12]
        val regex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?]\\s*(.*)")
        
        for (line in lines) {
            val trimmed = line.trim()
            val matchResult = regex.find(trimmed)
            if (matchResult != null) {
                val min = matchResult.groupValues[1].toLongOrNull() ?: 0L
                val sec = matchResult.groupValues[2].toLongOrNull() ?: 0L
                val msStr = matchResult.groupValues[3]
                var ms = 0L
                if (msStr.isNotEmpty()) {
                    val parsedMs = msStr.toLongOrNull() ?: 0L
                    ms = when (msStr.length) {
                        1 -> parsedMs * 100
                        2 -> parsedMs * 10
                        else -> parsedMs
                    }
                }
                val text = matchResult.groupValues[4].trim()
                val totalMs = (min * 60 + sec) * 1000 + ms
                
                // Add the parsed lyric line even if empty (allows instrumental/silence intervals)
                result.add(LyricLine(totalMs, text))
            }
        }
        return result.sortedBy { it.timeMs }
    }

    private fun cleanText(text: String): String {
        var cleaned = text.trim()
        if (cleaned.lowercase().endsWith(".mp3")) {
            cleaned = cleaned.substring(0, cleaned.length - 4)
        }
        // Remove brackets and parentheses metadata
        cleaned = cleaned.replace(Regex("\\s*\\([^)]*\\)"), "")
        cleaned = cleaned.replace(Regex("\\s*\\[[^]]*\\]"), "")
        cleaned = cleaned.replace(Regex("\\s*\\{[^}]*\\}"), "")
        
        // Clean common suffixes/tags
        val suffixes = listOf(
            "official video", "official audio", "official music video", "music video", 
            "lyric video", "lyrics", "hd", "hq", "4k", "remix", "cover", "prod.", "prod", "visualizer", "audio"
        )
        for (suffix in suffixes) {
            cleaned = cleaned.replace(Regex("(?i)\\b$suffix\\b"), "")
        }
        return cleaned.replace(Regex("\\s+"), " ").trim()
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

    data class ExactMatchCandidate(val artist: String, val title: String)

    fun getExactMatchCandidates(artist: String, title: String): List<ExactMatchCandidate> {
        val candidates = mutableListOf<ExactMatchCandidate>()
        
        val cleanArtist = cleanText(artist).let { if (it.equals("Unknown Artist", ignoreCase = true) || it.equals("Unknown", ignoreCase = true)) "" else it }
        val cleanTitle = cleanText(title)
        
        if (cleanArtist.isNotEmpty()) {
            candidates.add(ExactMatchCandidate(cleanArtist, cleanTitle))
            val primaryArtist = getPrimaryArtist(cleanArtist)
            if (primaryArtist != cleanArtist) {
                candidates.add(ExactMatchCandidate(primaryArtist, cleanTitle))
            }
        } else {
            // No artist, try to split title
            val hyphenIdx = findSeparatorIndex(cleanTitle)
            if (hyphenIdx != -1) {
                val part1 = cleanTitle.substring(0, hyphenIdx).trim()
                val part2 = cleanTitle.substring(hyphenIdx + 1).trim()
                candidates.add(ExactMatchCandidate(part1, part2))
                val primaryPart1 = getPrimaryArtist(part1)
                if (primaryPart1 != part1) {
                    candidates.add(ExactMatchCandidate(primaryPart1, part2))
                }
            } else {
                // No hyphen, check separators
                val separators = listOf(
                    "(?i)\\s+x\\s+",
                    "(?i)\\s+feat\\.?\\s+",
                    "(?i)\\s+ft\\.?\\s+",
                    "(?i)\\s+and\\s+",
                    "\\s*&\\s*"
                )
                for (sep in separators) {
                    val match = Regex(sep).find(cleanTitle)
                    if (match != null) {
                        val range = match.groups[0]?.range
                        if (range != null) {
                            val left = cleanTitle.substring(0, range.first).trim()
                            val right = cleanTitle.substring(range.last + 1).trim()
                            
                            candidates.add(ExactMatchCandidate(left, right))
                            
                            val rightWords = right.split(Regex("\\s+"))
                            if (rightWords.size > 1) {
                                val rightWithoutFirstWord = rightWords.drop(1).joinToString(" ")
                                candidates.add(ExactMatchCandidate(left, rightWithoutFirstWord))
                            }
                        }
                    }
                }
            }
        }
        
        return candidates.distinctBy { "${it.artist.lowercase()}|||${it.title.lowercase()}" }
    }

    fun getSearchQueries(artist: String, title: String): List<String> {
        val queries = mutableListOf<String>()
        
        val cleanArtist = cleanText(artist).let { if (it.equals("Unknown Artist", ignoreCase = true) || it.equals("Unknown", ignoreCase = true)) "" else it }
        val cleanTitle = cleanText(title)
        
        if (cleanArtist.isNotEmpty()) {
            queries.add("$cleanArtist $cleanTitle")
            
            val primaryArtist = getPrimaryArtist(cleanArtist)
            if (primaryArtist != cleanArtist) {
                queries.add("$primaryArtist $cleanTitle")
            }
        } else {
            queries.add(cleanTitle)
            
            val hyphenIdx = findSeparatorIndex(cleanTitle)
            if (hyphenIdx != -1) {
                val part1 = cleanTitle.substring(0, hyphenIdx).trim()
                val part2 = cleanTitle.substring(hyphenIdx + 1).trim()
                queries.add("$part1 $part2")
                val primaryPart1 = getPrimaryArtist(part1)
                if (primaryPart1 != part1) {
                    queries.add("$primaryPart1 $part2")
                }
            } else {
                val separators = listOf(
                    "(?i)\\s+x\\s+",
                    "(?i)\\s+feat\\.?\\s+",
                    "(?i)\\s+ft\\.?\\s+",
                    "(?i)\\s+and\\s+",
                    "\\s*&\\s*"
                )
                for (sep in separators) {
                    val match = Regex(sep).find(cleanTitle)
                    if (match != null) {
                        val range = match.groups[0]?.range
                        if (range != null) {
                            val left = cleanTitle.substring(0, range.first).trim()
                            val right = cleanTitle.substring(range.last + 1).trim()
                            
                            queries.add("$left $right")
                            
                            val rightWords = right.split(Regex("\\s+"))
                            if (rightWords.size > 1) {
                                val rightWithoutFirstWord = rightWords.drop(1).joinToString(" ")
                                queries.add("$left $rightWithoutFirstWord")
                            }
                        }
                    }
                }
            }
        }
        
        return queries.map { it.replace(Regex("\\s+"), " ").trim() }.distinct().filter { it.isNotEmpty() }
    }

    /**
     * Fetch lyrics for the specified track from lrclib.net (API-key-free)
     */
    suspend fun fetchLyrics(track: TrackEntity): LyricsUiState = withContext(Dispatchers.IO) {
        try {
            val durationSec = track.durationMs / 1000

            // 1. Try exact matches first
            val exactCandidates = getExactMatchCandidates(track.artist, track.title)
            Log.d(TAG, "Generated exact match candidates: $exactCandidates")

            for (candidate in exactCandidates) {
                val artistEncoded = URLEncoder.encode(candidate.artist, "UTF-8")
                val titleEncoded = URLEncoder.encode(candidate.title, "UTF-8")

                // Try with duration if candidate matches original track and duration is valid (>0)
                if (durationSec > 0 && candidate.artist.equals(track.artist, ignoreCase = true) && candidate.title.equals(track.title, ignoreCase = true)) {
                    val getUrlWithDuration = "https://lrclib.net/api/get?artist_name=$artistEncoded&track_name=$titleEncoded&duration=$durationSec"
                    Log.d(TAG, "Trying exact get with duration: $getUrlWithDuration")
                    val result = executeGetRequest(getUrlWithDuration)
                    if (result != null) return@withContext result
                }

                // Try without duration
                val getUrlNoDuration = "https://lrclib.net/api/get?artist_name=$artistEncoded&track_name=$titleEncoded"
                Log.d(TAG, "Trying exact get without duration: $getUrlNoDuration")
                val result = executeGetRequest(getUrlNoDuration)
                if (result != null) return@withContext result
            }

            // 2. Try search fallbacks
            val searchQueries = getSearchQueries(track.artist, track.title)
            Log.d(TAG, "Generated search query fallbacks: $searchQueries")

            // Limit search queries to top 4 to prevent network spam
            for (query in searchQueries.take(4)) {
                val queryEncoded = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://lrclib.net/api/search?q=$queryEncoded"
                Log.d(TAG, "Trying search query: $searchUrl")

                val result = executeSearchRequest(searchUrl)
                if (result != null) return@withContext result
            }

            return@withContext LyricsUiState.Error("No lyrics found for this track.")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics", e)
            return@withContext LyricsUiState.Error("Failed to fetch lyrics: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }

    private fun executeGetRequest(url: String): LyricsUiState.Success? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SpotifyDynamicIsland/1.0 (Android; Open Source Lyrics Feature)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        return parseLyricsJsonObject(bodyString)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed GET query for $url", e)
        }
        return null
    }

    private fun executeSearchRequest(url: String): LyricsUiState.Success? {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "SpotifyDynamicIsland/1.0 (Android; Open Source Lyrics Feature)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val jsonArray = JSONArray(bodyString)
                        if (jsonArray.length() > 0) {
                            for (i in 0 until jsonArray.length()) {
                                val item = jsonArray.getJSONObject(i)
                                val syncedLyrics = item.optString("syncedLyrics", "")
                                val plainLyrics = item.optString("plainLyrics", "")
                                if (syncedLyrics.isNotEmpty() || plainLyrics.isNotEmpty()) {
                                    val isInstrumental = item.optBoolean("instrumental", false)
                                    if (isInstrumental) {
                                        return LyricsUiState.Success(
                                            syncedLines = listOf(LyricLine(0L, "♪ Instrumental ♪")),
                                            plainLyrics = "♪ Instrumental ♪",
                                            isSynced = true
                                        )
                                    }
                                    if (syncedLyrics.isNotEmpty()) {
                                        val syncedLines = parseLrc(syncedLyrics)
                                        if (syncedLines.isNotEmpty()) {
                                            return LyricsUiState.Success(
                                                syncedLines = syncedLines,
                                                plainLyrics = plainLyrics.ifEmpty { null },
                                                isSynced = true
                                            )
                                        }
                                    }
                                    if (plainLyrics.isNotEmpty()) {
                                        return LyricsUiState.Success(
                                            syncedLines = emptyList(),
                                            plainLyrics = plainLyrics,
                                            isSynced = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed search query for $url", e)
        }
        return null
    }

    private fun parseLyricsJsonObject(jsonString: String): LyricsUiState.Success? {
        val json = JSONObject(jsonString)
        val instrumental = json.optBoolean("instrumental", false)
        if (instrumental) {
            return LyricsUiState.Success(
                syncedLines = listOf(LyricLine(0L, "♪ Instrumental ♪")),
                plainLyrics = "♪ Instrumental ♪",
                isSynced = true
            )
        }
        val syncedLyrics = json.optString("syncedLyrics", "")
        val plainLyrics = json.optString("plainLyrics", "")
        
        if (syncedLyrics.isNotEmpty()) {
            val syncedLines = parseLrc(syncedLyrics)
            if (syncedLines.isNotEmpty()) {
                return LyricsUiState.Success(
                    syncedLines = syncedLines,
                    plainLyrics = if (plainLyrics.isNotEmpty()) plainLyrics else null,
                    isSynced = true
                )
            }
        }
        
        if (plainLyrics.isNotEmpty()) {
            return LyricsUiState.Success(
                syncedLines = emptyList(),
                plainLyrics = plainLyrics,
                isSynced = false
            )
        }
        
        return null
    }
}
