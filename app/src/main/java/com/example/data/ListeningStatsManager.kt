package com.example.data

import android.content.Context
import android.content.SharedPreferences

object ListeningStatsManager {
    private const val PREFS_NAME = "listening_stats_prefs"
    private const val KEY_TOTAL_SECONDS = "total_seconds"
    private const val KEY_INITIALIZED = "initialized"
    
    private const val PREFIX_GENRE = "genre_"
    private const val PREFIX_ARTIST = "artist_"
    private const val PREFIX_TRACK = "track_seconds_"
    private const val PREFIX_TRACK_PLAY_COUNT = "track_plays_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initializeIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit().apply {
                putLong(KEY_TOTAL_SECONDS, 8420L) // ~2.3 hours
                
                // Prepopulate genres
                putLong("${PREFIX_GENRE}Synthwave", 2500L)
                putLong("${PREFIX_GENRE}Vaporwave", 1800L)
                putLong("${PREFIX_GENRE}Acoustic", 1500L)
                putLong("${PREFIX_GENRE}Techno", 1200L)
                putLong("${PREFIX_GENRE}Lofi", 920L)
                putLong("${PREFIX_GENRE}Ambient", 500L)

                // Prepopulate artists
                putLong("${PREFIX_ARTIST}Synthwave Project", 2500L)
                putLong("${PREFIX_ARTIST}Vaporwave Kid", 1800L)
                putLong("${PREFIX_ARTIST}Clara & The Strings", 1500L)
                putLong("${PREFIX_ARTIST}Beatmaster", 1200L)
                putLong("${PREFIX_ARTIST}Lofi Study Beats", 920L)
                
                // Prepopulate track plays
                putInt("${PREFIX_TRACK_PLAY_COUNT}song_1", 12)
                putInt("${PREFIX_TRACK_PLAY_COUNT}song_2", 9)
                putInt("${PREFIX_TRACK_PLAY_COUNT}song_3", 7)
                putInt("${PREFIX_TRACK_PLAY_COUNT}song_4", 6)
                putInt("${PREFIX_TRACK_PLAY_COUNT}song_5", 4)

                putBoolean(KEY_INITIALIZED, true)
                apply()
            }
        }
    }

    fun recordListeningSecond(context: Context, track: TrackEntity) {
        initializeIfNeeded(context)
        val prefs = getPrefs(context)
        prefs.edit().apply {
            val total = prefs.getLong(KEY_TOTAL_SECONDS, 0L)
            putLong(KEY_TOTAL_SECONDS, total + 1)

            val genreKey = "$PREFIX_GENRE${track.genre}"
            val genreTime = prefs.getLong(genreKey, 0L)
            putLong(genreKey, genreTime + 1)

            val artistKey = "$PREFIX_ARTIST${track.artist}"
            val artistTime = prefs.getLong(artistKey, 0L)
            putLong(artistKey, artistTime + 1)

            val trackTimeKey = "$PREFIX_TRACK${track.id}"
            val trackTime = prefs.getLong(trackTimeKey, 0L)
            putLong(trackTimeKey, trackTime + 1)
            
            apply()
        }
    }

    fun incrementPlayCount(context: Context, trackId: String) {
        initializeIfNeeded(context)
        val prefs = getPrefs(context)
        val countKey = "$PREFIX_TRACK_PLAY_COUNT$trackId"
        val currentCount = prefs.getInt(countKey, 0)
        prefs.edit().putInt(countKey, currentCount + 1).apply()
    }

    fun getTotalListeningTimeSeconds(context: Context): Long {
        initializeIfNeeded(context)
        return getPrefs(context).getLong(KEY_TOTAL_SECONDS, 0L)
    }

    fun getTopGenres(context: Context, limit: Int = 5): List<Pair<String, Long>> {
        initializeIfNeeded(context)
        val prefs = getPrefs(context)
        val all = prefs.all
        return all.keys
            .filter { it.startsWith(PREFIX_GENRE) }
            .map { key ->
                val genreName = key.removePrefix(PREFIX_GENRE)
                val seconds = (all[key] as? Long) ?: (all[key] as? Int)?.toLong() ?: 0L
                genreName to seconds
            }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun getTopArtists(context: Context, limit: Int = 5): List<Pair<String, Long>> {
        initializeIfNeeded(context)
        val prefs = getPrefs(context)
        val all = prefs.all
        return all.keys
            .filter { it.startsWith(PREFIX_ARTIST) }
            .map { key ->
                val artistName = key.removePrefix(PREFIX_ARTIST)
                val seconds = (all[key] as? Long) ?: (all[key] as? Int)?.toLong() ?: 0L
                artistName to seconds
            }
            .sortedByDescending { it.second }
            .take(limit)
    }

    fun getTrackPlayCount(context: Context, trackId: String): Int {
        initializeIfNeeded(context)
        return getPrefs(context).getInt("$PREFIX_TRACK_PLAY_COUNT$trackId", 0)
    }
}
