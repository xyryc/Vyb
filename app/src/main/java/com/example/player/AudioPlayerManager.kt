package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.data.TrackEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

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
        initializeMediaPlayer()
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
                _isPlaying.value = true
                _playbackDuration.value = mp.duration.toLong()
                mp.start()
                startPositionUpdates()
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
            mediaPlayer?.setDataSource(track.audioUrl)
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
        } else {
            try {
                player.start()
                _isPlaying.value = true
                startPositionUpdates()
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
            } catch (e: Exception) {
                Log.e("AudioPlayerManager", "Error seeking", e)
            }
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
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        _playbackPosition.value = mp.currentPosition.toLong()
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
    }
}
