package com.example.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.util.Log
import android.os.Build
import com.example.data.TrackEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class AudioPlayerManager(private val context: Context) {
    companion object {
        @Volatile
        var instance: AudioPlayerManager? = null
            private set
    }

    private var mediaPlayer: MediaPlayer? = null
    
    // Equalizer and Sound booster instances
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    // Equalizer state flows
    private val _isEqualizerEnabled = MutableStateFlow(false)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _currentPresetIndex = MutableStateFlow(-1) // -1 for Custom
    val currentPresetIndex: StateFlow<Int> = _currentPresetIndex.asStateFlow()

    private val _bandGains = MutableStateFlow<List<Int>>(listOf(0, 0, 0, 0, 0)) // 5 bands by default
    val bandGains: StateFlow<List<Int>> = _bandGains.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0) // 0 to 1000
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0) // 0 to 1000
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _presetNames = MutableStateFlow<List<String>>(emptyList())
    val presetNames: StateFlow<List<String>> = _presetNames.asStateFlow()

    private val _bandCenterFreqs = MutableStateFlow<List<Int>>(listOf(60, 230, 910, 4000, 14000)) // Fallback Hz
    val bandCenterFreqs: StateFlow<List<Int>> = _bandCenterFreqs.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var playOnFocusGain = false
    var onToggleLike: ((TrackEntity) -> Unit)? = null

    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                if (playOnFocusGain) {
                    playOnFocusGain = false
                    try {
                        mediaPlayer?.start()
                        _isPlaying.value = true
                        startPositionUpdates()
                    } catch (e: Exception) {
                        Log.e("AudioPlayerManager", "Failed to start on focus gain", e)
                    }
                }
                try {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                } catch (e: Exception) {
                    Log.e("AudioPlayerManager", "Failed to restore volume", e)
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                playOnFocusGain = false
                if (_isPlaying.value) {
                    togglePlayPause()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_isPlaying.value) {
                    playOnFocusGain = true
                    togglePlayPause()
                }
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                try {
                    mediaPlayer?.setVolume(0.2f, 0.2f)
                } catch (e: Exception) {
                    Log.e("AudioPlayerManager", "Failed to duck volume", e)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            val result = audioManager.requestAudioFocus(focusRequest!!)
            return result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
            return result == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun updateCurrentTrack(track: TrackEntity) {
        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = track
        }
    }

    private val _currentTrack = MutableStateFlow<TrackEntity?>(null)
    val currentTrack: StateFlow<TrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<TrackEntity>>(emptyList())
    val playbackQueue: StateFlow<List<TrackEntity>> = _playbackQueue.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _isRepeatEnabled = MutableStateFlow(false)
    val isRepeatEnabled: StateFlow<Boolean> = _isRepeatEnabled.asStateFlow()

    private var originalQueue: List<TrackEntity> = emptyList()

    private val sharedPrefs = context.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)
    private val _notificationsEnabled = MutableStateFlow(sharedPrefs.getBoolean("pref_notifications", true))
    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "pref_notifications") {
            _notificationsEnabled.value = prefs.getBoolean(key, true)
        }
    }

    init {
        instance = this
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefListener)
        initializeMediaPlayer()
        setupMediaPlaybackServiceCallbacks()
        observePlaybackStateForNotification()
    }

    private fun setupMediaPlaybackServiceCallbacks() {
        MediaPlaybackService.onPlay = {
            if (!_isPlaying.value) {
                togglePlayPause()
            }
        }
        MediaPlaybackService.onPause = {
            if (_isPlaying.value) {
                togglePlayPause()
            }
        }
        MediaPlaybackService.onNext = {
            skipToNext()
        }
        MediaPlaybackService.onPrevious = {
            skipToPrevious()
        }
        MediaPlaybackService.onSeekTo = { pos ->
            seekTo(pos)
        }
        MediaPlaybackService.onLike = {
            _currentTrack.value?.let { track ->
                onToggleLike?.invoke(track)
            }
        }
    }

    private data class NotificationState(
        val track: TrackEntity?,
        val playing: Boolean,
        val duration: Long,
        val enabled: Boolean
    )

    private fun observePlaybackStateForNotification() {
        scope.launch {
            combine(currentTrack, isPlaying, playbackDuration, _notificationsEnabled) { track, playing, duration, enabled ->
                NotificationState(track, playing, duration, enabled)
            }.collect { state ->
                val track = state.track
                val playing = state.playing
                val duration = state.duration
                val enabled = state.enabled

                if (track != null && enabled) {
                    val intent = Intent(context, MediaPlaybackService::class.java).apply {
                        putExtra("track_id", track.id)
                        putExtra("track_title", track.title)
                        putExtra("track_artist", track.artist)
                        putExtra("track_artwork", track.coverUrl)
                        putExtra("is_playing", playing)
                        putExtra("track_position", _playbackPosition.value)
                        putExtra("track_duration", duration)
                        putExtra("track_liked", track.isLiked)
                    }
                    try {
                        if (MediaPlaybackService.isForeground) {
                            context.startService(intent)
                        } else {
                            if (playing) {
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            } else {
                                try {
                                    context.startService(intent)
                                } catch (e: Exception) {
                                    Log.e("AudioPlayerManager", "Failed to start background service when paused", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AudioPlayerManager", "Failed to start MediaPlaybackService", e)
                    }
                } else {
                    val intent = Intent(context, MediaPlaybackService::class.java).apply {
                        action = MediaPlaybackService.ACTION_STOP
                    }
                    try {
                        context.startService(intent)
                    } catch (e: Exception) {
                        Log.e("AudioPlayerManager", "Failed to stop MediaPlaybackService", e)
                    }
                }
            }
        }
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                _isBuffering.value = false
                if (requestAudioFocus()) {
                    _isPlaying.value = true
                    _playbackDuration.value = mp.duration.toLong()
                    mp.start()
                    startPositionUpdates()
                } else {
                    _isPlaying.value = false
                }
            }
            setOnCompletionListener {
                _isPlaying.value = false
                _playbackPosition.value = _playbackDuration.value
                stopPositionUpdates()
                if (_isRepeatEnabled.value) {
                    _currentTrack.value?.let { playTrack(it) }
                } else {
                    skipToNext()
                }
            }
            setOnErrorListener { _, what, extra ->
                Log.e("AudioPlayerManager", "MediaPlayer error: what=$what extra=$extra")
                _isBuffering.value = false
                _isPlaying.value = false
                stopPositionUpdates()
                false
            }
        }

        try {
            initEqualizer(mediaPlayer?.audioSessionId ?: 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setQueue(queue: List<TrackEntity>) {
        originalQueue = queue
        updateQueueList()
    }

    private fun updateQueueList() {
        if (_isShuffleEnabled.value) {
            val current = _currentTrack.value
            val list = originalQueue.toMutableList()
            if (current != null) {
                list.remove(current)
                list.shuffle()
                list.add(0, current)
            } else {
                list.shuffle()
            }
            _playbackQueue.value = list
        } else {
            _playbackQueue.value = originalQueue
        }
    }

    fun playTrack(track: TrackEntity, queue: List<TrackEntity> = emptyList()) {
        if (queue.isNotEmpty() && originalQueue != queue) {
            setQueue(queue)
        } else if (originalQueue.isEmpty()) {
            setQueue(listOf(track))
        }

        if (_currentTrack.value?.id == track.id) {
            // Already selected, just resume if paused
            togglePlayPause()
            return
        }

        _currentTrack.value = track
        com.example.data.ListeningStatsManager.incrementPlayCount(context, track.id)
        
        // Update play count and last played timestamp in Room database
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val db = com.example.data.AppDatabase.getDatabase(context)
                val trackDao = db.trackDao()
                val existingTrack = trackDao.getTrackById(track.id)
                if (existingTrack != null) {
                    val updated = existingTrack.copy(
                        playCount = existingTrack.playCount + 1,
                        lastPlayedTimestamp = System.currentTimeMillis()
                    )
                    trackDao.updateTrack(updated)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _isBuffering.value = true
        _playbackPosition.value = 0L
        _playbackDuration.value = track.durationMs // Fallback duration initially

        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(context.applicationContext, Uri.parse(track.audioUrl))
            mediaPlayer?.prepareAsync()
            
            // If shuffle was enabled, ensure queue is ordered accordingly
            if (_isShuffleEnabled.value) {
                updateQueueList()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error setting datasource", e)
            _isBuffering.value = false
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (_currentTrack.value == null) {
            // Play first item in queue if available
            val queue = _playbackQueue.value
            if (queue.isNotEmpty()) {
                playTrack(queue[0])
            }
            return
        }

        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            stopPositionUpdates()
            abandonAudioFocus()
        } else {
            try {
                if (requestAudioFocus()) {
                    player.start()
                    _isPlaying.value = true
                    startPositionUpdates()
                }
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Error resuming playback", e)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let { mp ->
            try {
                mp.seekTo(positionMs.toInt())
                _playbackPosition.value = positionMs
                syncPositionToService()
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Error seeking", e)
            }
        }
    }

    private fun syncPositionToService() {
        if (!_notificationsEnabled.value) return
        val track = _currentTrack.value ?: return
        val playing = _isPlaying.value
        val duration = _playbackDuration.value
        val intent = Intent(context, MediaPlaybackService::class.java).apply {
            putExtra("track_id", track.id)
            putExtra("track_title", track.title)
            putExtra("track_artist", track.artist)
            putExtra("track_artwork", track.coverUrl)
            putExtra("is_playing", playing)
            putExtra("track_position", _playbackPosition.value)
            putExtra("track_duration", duration)
            putExtra("track_liked", track.isLiked)
        }
        try {
            if (MediaPlaybackService.isForeground) {
                context.startService(intent)
            } else {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to sync MediaPlaybackService position", e)
        }
    }

    fun skipToNext() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return

        val currentIndex = queue.indexOfFirst { it.id == _currentTrack.value?.id }
        if (currentIndex == -1) {
            playTrack(queue[0])
        } else if (currentIndex < queue.lastIndex) {
            playTrack(queue[currentIndex + 1])
        } else {
            // Loop back to start of queue
            playTrack(queue[0])
        }
    }

    fun skipToPrevious() {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) return

        // If played more than 3 seconds, restart the song
        if (_playbackPosition.value > 3000) {
            seekTo(0)
            return
        }

        val currentIndex = queue.indexOfFirst { it.id == _currentTrack.value?.id }
        if (currentIndex == -1) {
            playTrack(queue[0])
        } else if (currentIndex > 0) {
            playTrack(queue[currentIndex - 1])
        } else {
            // Loop to end of queue
            playTrack(queue[queue.lastIndex])
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        updateQueueList()
    }

    fun toggleRepeat() {
        _isRepeatEnabled.value = !_isRepeatEnabled.value
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            var lastSyncedSecond = -1L
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val currentPos = mp.currentPosition.toLong()
                        _playbackPosition.value = currentPos
                        val currentSecond = currentPos / 1000
                        if (currentSecond != lastSyncedSecond) {
                            lastSyncedSecond = currentSecond
                            syncPositionToService()
                            _currentTrack.value?.let { track ->
                                com.example.data.ListeningStatsManager.recordListeningSecond(context, track)
                            }
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun initEqualizer(audioSessionId: Int) {
        try {
            // Release existing ones
            releaseEqualizer()

            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _isEqualizerEnabled.value
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _isEqualizerEnabled.value
            }
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = _isEqualizerEnabled.value
            }

            // Retrieve actual presets
            val presets = mutableListOf<String>()
            val numPresets = equalizer?.numberOfPresets ?: 0
            for (i in 0 until numPresets) {
                equalizer?.getPresetName(i.toShort())?.let { presets.add(it) }
            }
            _presetNames.value = presets

            // Retrieve band count and center freqs
            val numBands = equalizer?.numberOfBands?.toInt() ?: 5
            val freqs = mutableListOf<Int>()
            val initialGains = mutableListOf<Int>()
            val sharedPrefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
            
            for (i in 0 until numBands) {
                val centerFreqHz = (equalizer?.getCenterFreq(i.toShort()) ?: 0) / 1000
                freqs.add(centerFreqHz)
                
                // Load saved gain
                val savedGain = sharedPrefs.getInt("band_$i", 0)
                initialGains.add(savedGain)
                if (_isEqualizerEnabled.value) {
                    try {
                        equalizer?.setBandLevel(i.toShort(), (savedGain * 100).toShort())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _bandCenterFreqs.value = freqs
            _bandGains.value = initialGains

            // Load saved Bass Boost and Virtualizer strength
            val savedBass = sharedPrefs.getInt("bass_boost", 0)
            _bassBoostStrength.value = savedBass
            if (_isEqualizerEnabled.value) {
                try {
                    bassBoost?.setStrength(savedBass.toShort())
                } catch (e: Exception) { e.printStackTrace() }
            }

            val savedVirtualizer = sharedPrefs.getInt("virtualizer", 0)
            _virtualizerStrength.value = savedVirtualizer
            if (_isEqualizerEnabled.value) {
                try {
                    virtualizer?.setStrength(savedVirtualizer.toShort())
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Load saved preset index
            val savedPreset = sharedPrefs.getInt("preset_index", -1)
            _currentPresetIndex.value = savedPreset
            if (savedPreset >= 0 && _isEqualizerEnabled.value) {
                try {
                    equalizer?.usePreset(savedPreset.toShort())
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Load equalizer enabled state
            val isEnabled = sharedPrefs.getBoolean("eq_enabled", false)
            _isEqualizerEnabled.value = isEnabled
            equalizer?.enabled = isEnabled
            bassBoost?.enabled = isEnabled
            virtualizer?.enabled = isEnabled

        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to initialize Equalizer/BassBoost/Virtualizer", e)
            fallbackToSimulatedEqualizer()
        }
    }

    private fun fallbackToSimulatedEqualizer() {
        val sharedPrefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
        val isEnabled = sharedPrefs.getBoolean("eq_enabled", false)
        _isEqualizerEnabled.value = isEnabled

        val presets = listOf("Flat", "Classical", "Dance", "Folk", "Heavy Metal", "Jazz", "Pop", "Rock", "Acoustic", "Bass Booster", "Vocal Booster")
        _presetNames.value = presets

        val freqs = listOf(60, 230, 910, 4000, 14000)
        _bandCenterFreqs.value = freqs

        val initialGains = mutableListOf<Int>()
        for (i in freqs.indices) {
            initialGains.add(sharedPrefs.getInt("band_$i", 0))
        }
        _bandGains.value = initialGains

        _bassBoostStrength.value = sharedPrefs.getInt("bass_boost", 0)
        _virtualizerStrength.value = sharedPrefs.getInt("virtualizer", 0)
        _currentPresetIndex.value = sharedPrefs.getInt("preset_index", -1)
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
            equalizer = null
            bassBoost?.release()
            bassBoost = null
            virtualizer?.release()
            virtualizer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        val sharedPrefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("eq_enabled", enabled).apply()

        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled

            if (enabled) {
                applyCurrentEqualizerSettings()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyCurrentEqualizerSettings() {
        try {
            val preset = _currentPresetIndex.value
            if (preset >= 0) {
                equalizer?.usePreset(preset.toShort())
                val numBands = equalizer?.numberOfBands?.toInt() ?: 5
                val currentGains = mutableListOf<Int>()
                for (i in 0 until numBands) {
                    val gainDb = (equalizer?.getBandLevel(i.toShort()) ?: 0) / 100
                    currentGains.add(gainDb)
                }
                _bandGains.value = currentGains
            } else {
                val gains = _bandGains.value
                for (i in gains.indices) {
                    equalizer?.setBandLevel(i.toShort(), (gains[i] * 100).toShort())
                }
            }

            bassBoost?.setStrength(_bassBoostStrength.value.toShort())
            virtualizer?.setStrength(_virtualizerStrength.value.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPreset(presetIndex: Int) {
        _currentPresetIndex.value = presetIndex
        val sharedPrefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("preset_index", presetIndex).apply()

        if (_isEqualizerEnabled.value) {
            try {
                if (presetIndex >= 0) {
                    equalizer?.usePreset(presetIndex.toShort())
                    
                    val numBands = equalizer?.numberOfBands?.toInt() ?: 5
                    val currentGains = mutableListOf<Int>()
                    for (i in 0 until numBands) {
                        val gainDb = (equalizer?.getBandLevel(i.toShort()) ?: 0) / 100
                        currentGains.add(gainDb)
                        sharedPrefs.edit().putInt("band_$i", gainDb).apply()
                    }
                    _bandGains.value = currentGains
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            if (presetIndex >= 0) {
                val simulatedGains = when (presetIndex) {
                    0 -> listOf(0, 0, 0, 0, 0) // Flat
                    1 -> listOf(4, 2, 0, 2, 4) // Classical
                    2 -> listOf(5, 3, 0, 2, 4) // Dance
                    3 -> listOf(3, 1, 0, 2, 1) // Folk
                    4 -> listOf(4, 1, 9, 3, 0) // Heavy Metal
                    5 -> listOf(3, 2, 1, 2, 3) // Jazz
                    6 -> listOf(-2, -1, 3, 2, -1) // Pop
                    7 -> listOf(5, 3, -1, 2, 5) // Rock
                    8 -> listOf(3, 1, 2, 2, 1) // Acoustic
                    9 -> listOf(6, 4, 0, 0, 0) // Bass Booster
                    10 -> listOf(-2, -2, 1, 4, 3) // Vocal Booster
                    else -> listOf(0, 0, 0, 0, 0)
                }
                
                _bandGains.value = simulatedGains
                for (i in simulatedGains.indices) {
                    sharedPrefs.edit().putInt("band_$i", simulatedGains[i]).apply()
                }
            }
        }
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        _currentPresetIndex.value = -1
        
        val updatedGains = _bandGains.value.toMutableList()
        if (bandIndex in updatedGains.indices) {
            updatedGains[bandIndex] = levelDb
            _bandGains.value = updatedGains

            val sharedPrefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .putInt("band_$bandIndex", levelDb)
                .putInt("preset_index", -1)
                .apply()

            if (_isEqualizerEnabled.value) {
                try {
                    equalizer?.setBandLevel(bandIndex.toShort(), (levelDb * 100).toShort())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setBassBoostStrength(strength: Int) {
        _bassBoostStrength.value = strength
        val sharedPrefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("bass_boost", strength).apply()

        if (_isEqualizerEnabled.value) {
            try {
                bassBoost?.setStrength(strength.toShort())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setVirtualizerStrength(strength: Int) {
        _virtualizerStrength.value = strength
        val sharedPrefs = context.getSharedPreferences("equalizer_settings", Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt("virtualizer", strength).apply()

        if (_isEqualizerEnabled.value) {
            try {
                virtualizer?.setStrength(strength.toShort())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun release() {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        stopPositionUpdates()
        scope.cancel()
        releaseEqualizer()
        mediaPlayer?.release()
        mediaPlayer = null
        MediaPlaybackService.onPlay = null
        MediaPlaybackService.onPause = null
        MediaPlaybackService.onNext = null
        MediaPlaybackService.onPrevious = null
        MediaPlaybackService.onSeekTo = null
    }
}
