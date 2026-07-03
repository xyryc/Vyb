package com.example.player

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
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

    private val attributionContext: Context = if (android.os.Build.VERSION.SDK_INT >= 30) {
        context.createAttributionContext("music_player")
    } else {
        context
    }

    private var mediaPlayer: MediaPlayer? = null
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

    init {
        instance = this
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

    private fun observePlaybackStateForNotification() {
        scope.launch {
            combine(currentTrack, isPlaying, playbackDuration) { track, playing, duration ->
                Triple(track, playing, duration)
            }.collect { (track, playing, duration) ->
                if (track != null) {
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
                        if (MediaPlaybackService.isServiceRunning) {
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
        mediaPlayer = if (android.os.Build.VERSION.SDK_INT >= 31) {
            MediaPlayer(attributionContext)
        } else {
            MediaPlayer()
        }.apply {
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
        _isBuffering.value = true
        _playbackPosition.value = 0L
        _playbackDuration.value = track.durationMs // Fallback duration initially

        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(attributionContext, Uri.parse(track.audioUrl))
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
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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

    fun release() {
        stopPositionUpdates()
        scope.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
        MediaPlaybackService.onPlay = null
        MediaPlaybackService.onPause = null
        MediaPlaybackService.onNext = null
        MediaPlaybackService.onPrevious = null
        MediaPlaybackService.onSeekTo = null
    }
}
