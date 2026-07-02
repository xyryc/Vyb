package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ScreenState {
    object Home : ScreenState
    object Search : ScreenState
    object Library : ScreenState
    data class PlaylistDetail(val playlist: PlaylistEntity) : ScreenState
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = TrackRepository(database.trackDao())
    val playerManager = AudioPlayerManager(application)

    // UI States
    val allTracks: StateFlow<List<TrackEntity>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedTracks: StateFlow<List<TrackEntity>> = repository.likedTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<ScreenState>(ScreenState.Home)
    val currentScreen: StateFlow<ScreenState> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<TrackEntity>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                repository.allTracks
            } else {
                repository.searchTracks(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylistTracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val selectedPlaylistTracks: StateFlow<List<TrackEntity>> = _selectedPlaylistTracks.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _showCreatePlaylistDialog = MutableStateFlow(false)
    val showCreatePlaylistDialog: StateFlow<Boolean> = _showCreatePlaylistDialog.asStateFlow()

    private val _showAddToPlaylistDialog = MutableStateFlow<TrackEntity?>(null)
    val showAddToPlaylistDialog: StateFlow<TrackEntity?> = _showAddToPlaylistDialog.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0L)
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()

    private val _showSleepTimerDialog = MutableStateFlow(false)
    val showSleepTimerDialog: StateFlow<Boolean> = _showSleepTimerDialog.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun showSleepTimerDialog(show: Boolean) {
        _showSleepTimerDialog.value = show
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemaining.value = 0L
            return
        }

        val durationMs = minutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + durationMs

        sleepTimerJob = viewModelScope.launch {
            _sleepTimerRemaining.value = durationMs
            while (System.currentTimeMillis() < endTime) {
                val remaining = endTime - System.currentTimeMillis()
                _sleepTimerRemaining.value = remaining.coerceAtLeast(0L)
                if (remaining <= 0) break
                kotlinx.coroutines.delay(1000)
            }
            _sleepTimerRemaining.value = 0L
            // Pause playback when timer expires
            if (playerManager.isPlaying.value) {
                playerManager.togglePlayPause()
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemaining.value = 0L
    }

    init {
        prepopulateDatabaseIfNeeded()
    }

    fun importLocalMp3(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val resolver = context.contentResolver
                
                val id = "local_${System.currentTimeMillis()}"
                
                val retriever = android.media.MediaMetadataRetriever()
                var title = "Local Audio"
                var artist = "Unknown Artist"
                var album = "Local Album"
                var durationMs = 0L
                var genre = "Local"
                var coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop"
                
                try {
                    retriever.setDataSource(context, uri)
                    title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: getFileName(context, uri) ?: "Local Audio"
                    artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
                    album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Local Album"
                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull() ?: 0L
                    genre = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE) ?: "Local"
                    
                    // Extract embedded picture if available
                    val embeddedPicture = retriever.embeddedPicture
                    if (embeddedPicture != null) {
                        val coverFile = java.io.File(context.filesDir, "${id}_cover.jpg")
                        try {
                            coverFile.outputStream().use { output ->
                                output.write(embeddedPicture)
                            }
                            coverUrl = coverFile.absolutePath
                        } catch (writeEx: Exception) {
                            writeEx.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {}
                }

                // Copy audio file to local files directory to ensure offline availability
                val destinationFile = java.io.File(context.filesDir, "$id.mp3")
                resolver.openInputStream(uri)?.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val localTrack = TrackEntity(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    audioUrl = destinationFile.absolutePath,
                    coverUrl = coverUrl,
                    genre = genre
                )

                repository.insertTracks(listOf(localTrack))
                
                // Refresh player queue
                val updatedTracks = repository.allTracks.first()
                playerManager.setQueue(updatedTracks)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result?.removeSuffix(".mp3")
    }

    private fun prepopulateDatabaseIfNeeded() {
        viewModelScope.launch {
            // Check if database is empty
            val currentTracks = repository.allTracks.first()
            if (currentTracks.isEmpty()) {
                val initialTracks = listOf(
                    TrackEntity(
                        id = "song_1",
                        title = "Neon Drive",
                        artist = "Synthwave Project",
                        album = "Retro Grid",
                        durationMs = 372000, // 6:12
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=500&auto=format&fit=crop",
                        genre = "Synthwave"
                    ),
                    TrackEntity(
                        id = "song_2",
                        title = "Midnight Electric",
                        artist = "Vaporwave Kid",
                        album = "Pink Horizon",
                        durationMs = 425000, // 7:05
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&auto=format&fit=crop",
                        genre = "Vaporwave"
                    ),
                    TrackEntity(
                        id = "song_3",
                        title = "Acoustic Sunsets",
                        artist = "Clara & The Strings",
                        album = "Golden Trails",
                        durationMs = 344000, // 5:44
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1506157786151-b8491531f063?w=500&auto=format&fit=crop",
                        genre = "Acoustic"
                    ),
                    TrackEntity(
                        id = "song_4",
                        title = "Techno Horizon",
                        artist = "Beatmaster",
                        album = "Pulse Code",
                        durationMs = 302000, // 5:02
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&auto=format&fit=crop",
                        genre = "Techno"
                    ),
                    TrackEntity(
                        id = "song_5",
                        title = "Smooth Chill",
                        artist = "Lofi Study Beats",
                        album = "Coffee & Rain",
                        durationMs = 363000, // 6:03
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop",
                        genre = "Lofi"
                    ),
                    TrackEntity(
                        id = "song_6",
                        title = "Deep Blue Sea",
                        artist = "Liquid Mind",
                        album = "Ambient Echoes",
                        durationMs = 560000, // 9:20
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=500&auto=format&fit=crop",
                        genre = "Ambient"
                    ),
                    TrackEntity(
                        id = "song_7",
                        title = "Electro Pulse",
                        artist = "Synthesizer Orchestra",
                        album = "Volts & Waves",
                        durationMs = 438000, // 7:18
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&auto=format&fit=crop",
                        genre = "Electronic"
                    ),
                    TrackEntity(
                        id = "song_8",
                        title = "Rock Revolution",
                        artist = "The Distortions",
                        album = "Feedback Loop",
                        durationMs = 338000, // 5:38
                        audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                        coverUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500&auto=format&fit=crop",
                        genre = "Rock"
                    )
                )
                repository.insertTracks(initialTracks)
                playerManager.setQueue(initialTracks)
            } else {
                playerManager.setQueue(currentTracks)
            }

            // Also create a default playlist "My Chill Mix" if empty
            val currentPlaylists = repository.playlists.first()
            if (currentPlaylists.isEmpty()) {
                val playlistId = repository.insertPlaylist(
                    PlaylistEntity(
                        name = "My Chill Mix",
                        description = "A selection of calm beats and retro vibes"
                    )
                )
                // Add some initial songs to this playlist
                repository.addTrackToPlaylist(playlistId.toInt(), "song_1")
                repository.addTrackToPlaylist(playlistId.toInt(), "song_3")
                repository.addTrackToPlaylist(playlistId.toInt(), "song_5")
            }
        }
    }

    // Playback Operations
    fun playTrack(track: TrackEntity, sourceQueue: List<TrackEntity>) {
        playerManager.playTrack(track, sourceQueue)
    }

    fun toggleLike(track: TrackEntity) {
        viewModelScope.launch {
            val updated = track.copy(isLiked = !track.isLiked)
            repository.updateTrack(updated)
            
            // Sync with current playing track
            if (playerManager.currentTrack.value?.id == track.id) {
                // We should make sure the current track in player manager reflects the new like state
                // Since player manager is holding a reference, let's keep it in sync or let ViewModel hold it
            }
        }
    }

    // Navigation and Screen Management
    fun navigateTo(screen: ScreenState) {
        _currentScreen.value = screen
        if (screen is ScreenState.PlaylistDetail) {
            loadPlaylistTracks(screen.playlist.id)
        }
    }

    private fun loadPlaylistTracks(playlistId: Int) {
        viewModelScope.launch {
            repository.getTracksForPlaylist(playlistId).collect { tracks ->
                _selectedPlaylistTracks.value = tracks
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.insertPlaylist(
                    PlaylistEntity(name = name, description = description)
                )
            }
            _showCreatePlaylistDialog.value = false
        }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _currentScreen.value = ScreenState.Library
        }
    }

    fun addTrackToPlaylist(playlistId: Int, trackId: String) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
            _showAddToPlaylistDialog.value = null
            
            // Reload tracks if viewing the playlist currently
            val screen = _currentScreen.value
            if (screen is ScreenState.PlaylistDetail && screen.playlist.id == playlistId) {
                loadPlaylistTracks(playlistId)
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Int, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
            
            // Reload tracks if viewing the playlist currently
            val screen = _currentScreen.value
            if (screen is ScreenState.PlaylistDetail && screen.playlist.id == playlistId) {
                loadPlaylistTracks(playlistId)
            }
        }
    }

    fun showCreatePlaylistDialog(show: Boolean) {
        _showCreatePlaylistDialog.value = show
    }

    fun showAddToPlaylistDialog(track: TrackEntity?) {
        _showAddToPlaylistDialog.value = track
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
