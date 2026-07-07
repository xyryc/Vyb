package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.player.AudioPlayerManager
import com.example.player.LyricsUiState
import com.example.player.LyricsService
import com.example.ui.theme.ThemeAccent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface ScreenState {
    object Home : ScreenState
    object Search : ScreenState
    object Library : ScreenState
    data class PlaylistDetail(val playlist: PlaylistEntity) : ScreenState
    object Settings : ScreenState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
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

    private val sharedPrefs = application.getSharedPreferences("music_player_settings", android.content.Context.MODE_PRIVATE)

    private val _currentThemeAccent = MutableStateFlow(
        try {
            val savedName = sharedPrefs.getString("theme_accent", ThemeAccent.COSMIC_BLUE.name)
            ThemeAccent.valueOf(savedName ?: ThemeAccent.COSMIC_BLUE.name)
        } catch (e: Exception) {
            ThemeAccent.COSMIC_BLUE
        }
    )
    val currentThemeAccent: StateFlow<ThemeAccent> = _currentThemeAccent.asStateFlow()

    private val _visualizerStyle = MutableStateFlow(
        sharedPrefs.getString("pref_visualizer_style", "Fluid Particles") ?: "Fluid Particles"
    )
    val visualizerStyle: StateFlow<String> = _visualizerStyle.asStateFlow()

    private val _controlsOpacity = MutableStateFlow(
        sharedPrefs.getFloat("island_opacity", 0.95f)
    )
    val controlsOpacity: StateFlow<Float> = _controlsOpacity.asStateFlow()

    private val _ambientGlowEnabled = MutableStateFlow(
        sharedPrefs.getBoolean("pref_ambient_glow", true)
    )
    val ambientGlowEnabled: StateFlow<Boolean> = _ambientGlowEnabled.asStateFlow()

    private val _blurIntensity = MutableStateFlow(
        sharedPrefs.getFloat("pref_blur_intensity", 25f)
    )
    val blurIntensity: StateFlow<Float> = _blurIntensity.asStateFlow()

    private val _lyricsUiState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsUiState: StateFlow<LyricsUiState> = _lyricsUiState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scannedCount = MutableStateFlow(0)
    val scannedCount: StateFlow<Int> = _scannedCount.asStateFlow()

    fun loadLyricsForTrack(track: TrackEntity) {
        viewModelScope.launch {
            _lyricsUiState.value = LyricsUiState.Loading
            val state = LyricsService.fetchLyrics(track)
            _lyricsUiState.value = state
        }
    }

    fun setThemeAccent(accent: ThemeAccent) {
        _currentThemeAccent.value = accent
        sharedPrefs.edit().putString("theme_accent", accent.name).apply()
    }

    fun setVisualizerStyle(style: String) {
        _visualizerStyle.value = style
        sharedPrefs.edit().putString("pref_visualizer_style", style).apply()
    }

    fun setControlsOpacity(opacity: Float) {
        _controlsOpacity.value = opacity
        sharedPrefs.edit().putFloat("island_opacity", opacity).apply()
    }

    fun setAmbientGlowEnabled(enabled: Boolean) {
        _ambientGlowEnabled.value = enabled
        sharedPrefs.edit().putBoolean("pref_ambient_glow", enabled).apply()
    }

    fun setBlurIntensity(intensity: Float) {
        _blurIntensity.value = intensity
        sharedPrefs.edit().putFloat("pref_blur_intensity", intensity).apply()
    }

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

    fun extendSleepTimer(additionalMinutes: Int) {
        val currentRemaining = _sleepTimerRemaining.value
        val newRemainingMs = currentRemaining + (additionalMinutes * 60 * 1000L)
        val newRemainingMinutes = (newRemainingMs / (1000 * 60)).toInt().coerceAtLeast(1)
        setSleepTimer(newRemainingMinutes)
    }

    init {
        prepopulateDatabaseIfNeeded()
        playerManager.onToggleLike = { track ->
            toggleLike(track)
        }

        // Observe track changes to fetch lyrics automatically and check album art
        viewModelScope.launch {
            playerManager.currentTrack.collect { track ->
                if (track != null) {
                    loadLyricsForTrack(track)
                    checkAndDownloadAlbumArt(track)
                } else {
                    _lyricsUiState.value = LyricsUiState.Idle
                }
            }
        }
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

                var finalCoverUrl = coverUrl
                if (coverUrl.contains("unsplash.com")) {
                    try {
                        val onlineArtUrl = com.example.player.AlbumArtService.fetchAlbumArt(artist, title)
                        if (onlineArtUrl != null) {
                            finalCoverUrl = onlineArtUrl
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val localTrack = TrackEntity(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    audioUrl = destinationFile.absolutePath,
                    coverUrl = finalCoverUrl,
                    genre = genre,
                    folderName = "Single Imports",
                    importDate = System.currentTimeMillis()
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

    fun importLocalFolder(treeUri: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isScanning.value = true
            _scanProgress.value = 0f
            _scannedCount.value = 0
            
            try {
                val context = getApplication<Application>()
                val rootFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
                val allAudioFiles = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                val fileToFolderMap = mutableMapOf<androidx.documentfile.provider.DocumentFile, String>()
                
                gatherAudioFiles(rootFile, rootFile.name ?: "Imported Folder", allAudioFiles, fileToFolderMap)
                
                val totalCount = allAudioFiles.size
                val importedTracks = mutableListOf<TrackEntity>()
                
                if (totalCount > 0) {
                    allAudioFiles.forEachIndexed { index, file ->
                        val folderName = fileToFolderMap[file] ?: "Imported Folder"
                        val track = processMp3DocumentFile(context, file, folderName)
                        if (track != null) {
                            importedTracks.add(track)
                        }
                        _scannedCount.value = index + 1
                        _scanProgress.value = (index + 1).toFloat() / totalCount
                    }
                }
                
                if (importedTracks.isNotEmpty()) {
                    repository.insertTracks(importedTracks)
                    // Refresh player queue
                    val updatedTracks = repository.allTracks.first()
                    playerManager.setQueue(updatedTracks)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun rescanLibraryFolders(folderEntries: Set<String>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isScanning.value = true
            _scanProgress.value = 0f
            _scannedCount.value = 0
            
            try {
                val context = getApplication<Application>()
                val allAudioFiles = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                val fileToFolderMap = mutableMapOf<androidx.documentfile.provider.DocumentFile, String>()
                
                folderEntries.forEach { folderEntry ->
                    try {
                        val parts = folderEntry.split("|")
                        val uriStr = parts.getOrNull(0) ?: ""
                        if (uriStr.isNotEmpty()) {
                            val uri = android.net.Uri.parse(uriStr)
                            val rootFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                            if (rootFile != null) {
                                gatherAudioFiles(rootFile, rootFile.name ?: "Imported Folder", allAudioFiles, fileToFolderMap)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                val totalCount = allAudioFiles.size
                val importedTracks = mutableListOf<TrackEntity>()
                
                if (totalCount > 0) {
                    allAudioFiles.forEachIndexed { index, file ->
                        val folderName = fileToFolderMap[file] ?: "Imported Folder"
                        val track = processMp3DocumentFile(context, file, folderName)
                        if (track != null) {
                            importedTracks.add(track)
                        }
                        _scannedCount.value = index + 1
                        _scanProgress.value = (index + 1).toFloat() / totalCount
                    }
                }
                
                if (importedTracks.isNotEmpty()) {
                    repository.insertTracks(importedTracks)
                    val updatedTracks = repository.allTracks.first()
                    playerManager.setQueue(updatedTracks)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun gatherAudioFiles(
        documentFile: androidx.documentfile.provider.DocumentFile,
        currentFolderName: String,
        outputList: MutableList<androidx.documentfile.provider.DocumentFile>,
        fileToFolderMap: MutableMap<androidx.documentfile.provider.DocumentFile, String>
    ) {
        if (documentFile.isDirectory) {
            val files = documentFile.listFiles()
            for (file in files) {
                if (file.isDirectory) {
                    val subFolder = file.name ?: currentFolderName
                    gatherAudioFiles(file, subFolder, outputList, fileToFolderMap)
                } else if (file.isFile && (file.name?.endsWith(".mp3", ignoreCase = true) == true || file.type?.contains("audio") == true)) {
                    outputList.add(file)
                    fileToFolderMap[file] = currentFolderName
                }
            }
        } else if (documentFile.isFile && (documentFile.name?.endsWith(".mp3", ignoreCase = true) == true || documentFile.type?.contains("audio") == true)) {
            outputList.add(documentFile)
            fileToFolderMap[documentFile] = currentFolderName
        }
    }

    private suspend fun processMp3DocumentFile(
        context: android.content.Context,
        file: androidx.documentfile.provider.DocumentFile,
        folderName: String
    ): TrackEntity? {
        val resolver = context.contentResolver
        val uri = file.uri
        val id = "local_folder_${System.currentTimeMillis()}_${(1000..9999).random()}"
        
        var title = file.name?.removeSuffix(".mp3") ?: "Local Audio"
        var artist = "Unknown Artist"
        var album = "Local Album"
        var durationMs = 0L
        var genre = "Local"
        var coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop"
        
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val extractedTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!extractedTitle.isNullOrEmpty()) {
                title = extractedTitle
            }
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
        
        try {
            // Copy audio file to local files directory to ensure offline availability
            val destinationFile = java.io.File(context.filesDir, "$id.mp3")
            resolver.openInputStream(uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            var finalCoverUrl = coverUrl
            if (coverUrl.contains("unsplash.com")) {
                try {
                    val onlineArtUrl = com.example.player.AlbumArtService.fetchAlbumArt(artist, title)
                    if (onlineArtUrl != null) {
                        finalCoverUrl = onlineArtUrl
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return TrackEntity(
                id = id,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                audioUrl = destinationFile.absolutePath,
                coverUrl = finalCoverUrl,
                genre = genre,
                folderName = folderName,
                importDate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
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
                playerManager.updateCurrentTrack(updated)
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
            if (playlistId < 0) {
                repository.allTracks.collect { allTracks ->
                    val smartTracks = when (playlistId) {
                        -1 -> {
                            // Heavy Rotation: Sort descending by playCount
                            val context = getApplication<Application>()
                            val played = allTracks.filter { track ->
                                val count = maxOf(track.playCount, ListeningStatsManager.getTrackPlayCount(context, track.id))
                                count > 0
                            }.sortedByDescending { track ->
                                maxOf(track.playCount, ListeningStatsManager.getTrackPlayCount(context, track.id))
                            }.take(20)
                            
                            if (played.isEmpty()) {
                                // Fallback: default top tracks on fresh install
                                allTracks.take(4)
                            } else {
                                played
                            }
                        }
                        -2 -> {
                            // Forgotten Favorites: Liked or high plays (>=4), not played in last 2 weeks (or never played)
                            val twoWeeksAgo = System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000L
                            val context = getApplication<Application>()
                            val favorites = allTracks.filter { track ->
                                val count = maxOf(track.playCount, ListeningStatsManager.getTrackPlayCount(context, track.id))
                                val isHighRated = track.isLiked || count >= 4
                                val notPlayedRecently = track.lastPlayedTimestamp < twoWeeksAgo || (track.lastPlayedTimestamp == 0L && count == 0)
                                isHighRated && notPlayedRecently
                            }
                            if (favorites.isEmpty()) {
                                // Fallback: liked tracks, or first few if no likes
                                val liked = allTracks.filter { it.isLiked }
                                if (liked.isEmpty()) allTracks.take(3) else liked
                            } else {
                                favorites
                            }
                        }
                        -3 -> {
                            // Time of Day Mixes: suggests dynamic genres depending on the clock
                            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                            val matchingGenres = when (hour) {
                                in 6..11 -> listOf("Acoustic", "Lofi", "Ambient")
                                in 12..17 -> listOf("Electronic", "Techno", "Synthwave", "Rock")
                                else -> listOf("Vaporwave", "Ambient", "Lofi")
                            }
                            val filtered = allTracks.filter { track ->
                                matchingGenres.any { genre -> track.genre.equals(genre, ignoreCase = true) }
                            }
                            if (filtered.isEmpty()) {
                                allTracks.take(4)
                            } else {
                                filtered
                            }
                        }
                        else -> emptyList()
                    }
                    _selectedPlaylistTracks.value = smartTracks
                }
            } else {
                repository.getTracksForPlaylist(playlistId).collect { tracks ->
                    _selectedPlaylistTracks.value = tracks
                }
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
            if (playlistId >= 0) {
                repository.deletePlaylist(playlistId)
            }
            _currentScreen.value = ScreenState.Library
        }
    }

    fun addTrackToPlaylist(playlistId: Int, trackId: String) {
        viewModelScope.launch {
            if (playlistId >= 0) {
                repository.addTrackToPlaylist(playlistId, trackId)
                _showAddToPlaylistDialog.value = null
                
                // Reload tracks if viewing the playlist currently
                val screen = _currentScreen.value
                if (screen is ScreenState.PlaylistDetail && screen.playlist.id == playlistId) {
                    loadPlaylistTracks(playlistId)
                }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Int, trackId: String) {
        viewModelScope.launch {
            if (playlistId >= 0) {
                repository.removeTrackFromPlaylist(playlistId, trackId)
                
                // Reload tracks if viewing the playlist currently
                val screen = _currentScreen.value
                if (screen is ScreenState.PlaylistDetail && screen.playlist.id == playlistId) {
                    loadPlaylistTracks(playlistId)
                }
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

    // Equalizer & Sound booster delegation
    val isEqualizerEnabled = playerManager.isEqualizerEnabled
    val currentPresetIndex = playerManager.currentPresetIndex
    val bandGains = playerManager.bandGains
    val bassBoostStrength = playerManager.bassBoostStrength
    val virtualizerStrength = playerManager.virtualizerStrength
    val presetNames = playerManager.presetNames
    val bandCenterFreqs = playerManager.bandCenterFreqs

    // Audio Output Device Delegation
    val availableOutputDevices = playerManager.availableOutputDevices
    val selectedOutputDevice = playerManager.selectedOutputDevice

    fun selectOutputDevice(device: com.example.player.AudioDeviceWrapper) {
        playerManager.selectOutputDevice(device)
    }

    fun updateAvailableDevices() {
        playerManager.updateAvailableDevices()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        playerManager.setEqualizerEnabled(enabled)
    }

    fun setPreset(presetIndex: Int) {
        playerManager.setPreset(presetIndex)
    }

    fun setBandLevel(bandIndex: Int, levelDb: Int) {
        playerManager.setBandLevel(bandIndex, levelDb)
    }

    fun setBassBoostStrength(strength: Int) {
        playerManager.setBassBoostStrength(strength)
    }

    fun setVirtualizerStrength(strength: Int) {
        playerManager.setVirtualizerStrength(strength)
    }

    fun checkAndDownloadAlbumArt(track: TrackEntity) {
        val autoDownload = sharedPrefs.getBoolean("pref_auto_download_album_art", true)
        if (!autoDownload) return

        val isPlaceholder = track.coverUrl.contains("unsplash.com") || track.coverUrl.isEmpty()
        if (isPlaceholder) {
            viewModelScope.launch {
                try {
                    val onlineArtUrl = com.example.player.AlbumArtService.fetchAlbumArt(track.artist, track.title)
                    if (onlineArtUrl != null) {
                        val updatedTrack = track.copy(coverUrl = onlineArtUrl)
                        repository.updateTrack(updatedTrack)
                        playerManager.updateTrackInQueue(updatedTrack)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun manualDownloadAlbumArt(track: TrackEntity): Boolean {
        return try {
            val onlineArtUrl = com.example.player.AlbumArtService.fetchAlbumArt(track.artist, track.title)
            if (onlineArtUrl != null) {
                val updatedTrack = track.copy(coverUrl = onlineArtUrl)
                repository.updateTrack(updatedTrack)
                playerManager.updateTrackInQueue(updatedTrack)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
