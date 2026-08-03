package com.stonewellstudio.vyb.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

object ListeningStatsManager {
    private const val PREFS_NAME = "listening_stats_prefs"
    private const val KEY_TOTAL_SECONDS = "total_seconds"
    private const val KEY_INITIALIZED = "initialized"
    
    private const val PREFIX_GENRE = "genre_"
    private const val PREFIX_ARTIST = "artist_"
    private const val PREFIX_TRACK = "track_seconds_"
    private const val PREFIX_TRACK_PLAY_COUNT = "track_plays_"
    private const val PREFIX_DAY = "day_seconds_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun initializeIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            prefs.edit().apply {
                putLong(KEY_TOTAL_SECONDS, 0L)
                
                // Initialize days of week to 0L so we have empty slots starting fresh
                putLong("${PREFIX_DAY}${Calendar.MONDAY}", 0L)
                putLong("${PREFIX_DAY}${Calendar.TUESDAY}", 0L)
                putLong("${PREFIX_DAY}${Calendar.WEDNESDAY}", 0L)
                putLong("${PREFIX_DAY}${Calendar.THURSDAY}", 0L)
                putLong("${PREFIX_DAY}${Calendar.FRIDAY}", 0L)
                putLong("${PREFIX_DAY}${Calendar.SATURDAY}", 0L)
                putLong("${PREFIX_DAY}${Calendar.SUNDAY}", 0L)

                putBoolean(KEY_INITIALIZED, true)
                apply()
            }
        }
    }

    fun recordListeningSecond(context: Context, track: TrackEntity) {
        initializeIfNeeded(context)
        val prefs = getPrefs(context)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
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

            val dayKey = "$PREFIX_DAY$dayOfWeek"
            val dayTime = prefs.getLong(dayKey, 0L)
            putLong(dayKey, dayTime + 1)
            
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

    fun getDailyListeningTime(context: Context): List<Pair<String, Long>> {
        initializeIfNeeded(context)
        val prefs = getPrefs(context)
        
        // Return structured days of week: Mon, Tue, Wed, Thu, Fri, Sat, Sun
        val days = listOf(
            "Mon" to Calendar.MONDAY,
            "Tue" to Calendar.TUESDAY,
            "Wed" to Calendar.WEDNESDAY,
            "Thu" to Calendar.THURSDAY,
            "Fri" to Calendar.FRIDAY,
            "Sat" to Calendar.SATURDAY,
            "Sun" to Calendar.SUNDAY
        )
        
        return days.map { (dayName, calendarDay) ->
            val dayKey = "$PREFIX_DAY$calendarDay"
            val seconds = prefs.getLong(dayKey, 0L)
            dayName to seconds
        }
    }

    fun simulateListeningSession(context: Context, tracks: List<TrackEntity>, additionalSeconds: Long) {
        initializeIfNeeded(context)
        val prefs = getPrefs(context)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        
        val edit = prefs.edit()
        
        // 1. Add to overall total
        val currentTotal = prefs.getLong(KEY_TOTAL_SECONDS, 0L)
        edit.putLong(KEY_TOTAL_SECONDS, currentTotal + additionalSeconds)
        
        // 2. Add to active day
        val dayKey = "$PREFIX_DAY$dayOfWeek"
        val currentDaySeconds = prefs.getLong(dayKey, 0L)
        edit.putLong(dayKey, currentDaySeconds + additionalSeconds)
        
        // If we have local tracks imported, let's distribute the seconds and play counts
        if (tracks.isNotEmpty()) {
            val randomTracks = tracks.shuffled().take(3)
            val chunk = additionalSeconds / randomTracks.size
            
            randomTracks.forEach { track ->
                // Distribute track playtime
                val trackTimeKey = "$PREFIX_TRACK${track.id}"
                val currentTrackTime = prefs.getLong(trackTimeKey, 0L)
                edit.putLong(trackTimeKey, currentTrackTime + chunk)
                
                // Add play count (1-2 play per simulated block)
                val countKey = "$PREFIX_TRACK_PLAY_COUNT${track.id}"
                val currentCount = prefs.getInt(countKey, 0)
                edit.putInt(countKey, currentCount + (1..2).random())
                
                // Add genre stats
                val genreKey = "$PREFIX_GENRE${track.genre}"
                val currentGenreTime = prefs.getLong(genreKey, 0L)
                edit.putLong(genreKey, currentGenreTime + chunk)
                
                // Add artist stats
                val artistKey = "$PREFIX_ARTIST${track.artist}"
                val currentArtistTime = prefs.getLong(artistKey, 0L)
                edit.putLong(artistKey, currentArtistTime + chunk)
            }
        } else {
            // If library is empty, add to default placeholders
            val dummyTracks = listOf(
                Pair("Synthwave Project", "Synthwave"),
                Pair("Vaporwave Kid", "Vaporwave"),
                Pair("Clara & The Strings", "Acoustic")
            )
            val chunk = additionalSeconds / dummyTracks.size
            dummyTracks.forEachIndexed { i, (artist, genre) ->
                val genreKey = "$PREFIX_GENRE$genre"
                val currentGenreTime = prefs.getLong(genreKey, 0L)
                edit.putLong(genreKey, currentGenreTime + chunk)
                
                val artistKey = "$PREFIX_ARTIST$artist"
                val currentArtistTime = prefs.getLong(artistKey, 0L)
                edit.putLong(artistKey, currentArtistTime + chunk)
                
                val countKey = "$PREFIX_TRACK_PLAY_COUNT${i + 1}"
                val currentCount = prefs.getInt(countKey, 0)
                edit.putInt(countKey, currentCount + (1..2).random())
            }
        }
        
        edit.apply()
    }

    fun loadDemoStats(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putLong(KEY_TOTAL_SECONDS, 19520L) // ~5.4 hours total prepopulated
            
            // Prepopulate genres
            putLong("${PREFIX_GENRE}Synthwave", 6500L)
            putLong("${PREFIX_GENRE}Vaporwave", 4800L)
            putLong("${PREFIX_GENRE}Acoustic", 3500L)
            putLong("${PREFIX_GENRE}Techno", 2800L)
            putLong("${PREFIX_GENRE}Lofi", 1920L)
            putLong("${PREFIX_GENRE}Ambient", 1000L)

            // Prepopulate artists
            putLong("${PREFIX_ARTIST}Synthwave Project", 6500L)
            putLong("${PREFIX_ARTIST}Vaporwave Kid", 4800L)
            putLong("${PREFIX_ARTIST}Clara & The Strings", 3500L)
            putLong("${PREFIX_ARTIST}Beatmaster", 2800L)
            putLong("${PREFIX_ARTIST}Lofi Study Beats", 1920L)
            
            // Prepopulate track plays
            putInt("${PREFIX_TRACK_PLAY_COUNT}song_1", 24)
            putInt("${PREFIX_TRACK_PLAY_COUNT}song_2", 18)
            putInt("${PREFIX_TRACK_PLAY_COUNT}song_3", 14)
            putInt("${PREFIX_TRACK_PLAY_COUNT}song_4", 12)
            putInt("${PREFIX_TRACK_PLAY_COUNT}song_5", 9)

            // Prepopulate days of week listening
            putLong("${PREFIX_DAY}${Calendar.MONDAY}", 2400L) // 40 mins
            putLong("${PREFIX_DAY}${Calendar.TUESDAY}", 3000L) // 50 mins
            putLong("${PREFIX_DAY}${Calendar.WEDNESDAY}", 1500L) // 25 mins
            putLong("${PREFIX_DAY}${Calendar.THURSDAY}", 3600L) // 60 mins
            putLong("${PREFIX_DAY}${Calendar.FRIDAY}", 4200L) // 70 mins
            putLong("${PREFIX_DAY}${Calendar.SATURDAY}", 4800L) // 80 mins
            putLong("${PREFIX_DAY}${Calendar.SUNDAY}", 1800L) // 30 mins

            putBoolean(KEY_INITIALIZED, true)
            apply()
        }
    }
}
