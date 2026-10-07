package com.stonewellstudio.vyb.importer

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import com.stonewellstudio.vyb.data.TrackEntity
import com.stonewellstudio.vyb.data.TrackRepository
import com.stonewellstudio.vyb.player.AlbumArtService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest

enum class ImportStatus {
    QUEUED,
    VALIDATING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class ImportHistoryItem(
    val id: String,
    val url: String,
    val title: String,
    val artist: String,
    val status: ImportStatus,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val trackId: String? = null,
    val fileSizeFormatted: String = ""
)

sealed interface ImportUrlUiState {
    object Idle : ImportUrlUiState
    data class Validating(val url: String) : ImportUrlUiState
    data class DuplicateFound(
        val existingTrack: TrackEntity,
        val url: String,
        val validationResult: UrlValidationResult.Valid
    ) : ImportUrlUiState
    data class Downloading(
        val url: String,
        val fileName: String,
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedFormatted: String,
        val downloadedFormatted: String,
        val totalFormatted: String
    ) : ImportUrlUiState
    data class Success(val track: TrackEntity, val wasDuplicateIgnored: Boolean = false) : ImportUrlUiState
    data class Error(val url: String, val message: String, val canRetry: Boolean = true) : ImportUrlUiState
}

class UrlImportManager(
    private val context: Context,
    private val repository: TrackRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val notificationHelper = DownloadNotificationHelper(context)
    private val downloader = AudioDownloader()

    private val _uiState = MutableStateFlow<ImportUrlUiState>(ImportUrlUiState.Idle)
    val uiState: StateFlow<ImportUrlUiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<ImportHistoryItem>>(emptyList())
    val history: StateFlow<List<ImportHistoryItem>> = _history.asStateFlow()

    private var activeJob: Job? = null
    private var lastAttemptedUrl: String? = null

    // Ensure audio imports directory exists in internal storage
    private val storageDir: File by lazy {
        File(context.filesDir, "audio_imports").apply {
            if (!exists()) mkdirs()
        }
    }

    fun startImport(url: String, forceReimport: Boolean = false) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = ImportUrlUiState.Error("", "Please enter a valid URL.", canRetry = false)
            return
        }

        lastAttemptedUrl = trimmed
        activeJob?.cancel()

        activeJob = scope.launch {
            _uiState.value = ImportUrlUiState.Validating(trimmed)

            // Step 1: Pre-validation and Remote HTTP HEAD validation
            val validation = UrlValidator.validateRemoteUrl(trimmed)
            if (validation is UrlValidationResult.Invalid) {
                _uiState.value = ImportUrlUiState.Error(trimmed, validation.reason, canRetry = !validation.isStreamingSite)
                addHistoryEntry(trimmed, "Unknown", "Unknown", ImportStatus.FAILED, validation.reason)
                return@launch
            }

            val validResult = validation as UrlValidationResult.Valid

            // Step 2: Pre-download duplicate check (by existing target path or title/artist if known)
            val expectedDest = File(storageDir, validResult.suggestedFileName)
            if (!forceReimport && expectedDest.exists()) {
                val existing = repository.getTrackByPath(expectedDest.absolutePath)
                if (existing != null) {
                    _uiState.value = ImportUrlUiState.DuplicateFound(existing, trimmed, validResult)
                    return@launch
                }
            }

            // Step 3: Execute download
            executeDownload(trimmed, validResult, forceReimport)
        }
    }

    private suspend fun executeDownload(
        url: String,
        validResult: UrlValidationResult.Valid,
        forceReimport: Boolean
    ) {
        val tempFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.tmp")

        try {
            _uiState.value = ImportUrlUiState.Downloading(
                url = url,
                fileName = validResult.suggestedFileName,
                progress = 0,
                downloadedBytes = 0,
                totalBytes = validResult.contentLength,
                speedFormatted = "Connecting...",
                downloadedFormatted = "0 B",
                totalFormatted = if (validResult.contentLength > 0) AudioDownloader.formatBytes(validResult.contentLength) else "Unknown"
            )

            notificationHelper.showProgressNotification(
                validResult.suggestedFileName,
                0,
                "Starting download..."
            )

            downloader.downloadFile(url, tempFile, validResult.contentLength).collect { progress ->
                _uiState.value = ImportUrlUiState.Downloading(
                    url = url,
                    fileName = validResult.suggestedFileName,
                    progress = progress.percentage,
                    downloadedBytes = progress.bytesDownloaded,
                    totalBytes = progress.totalBytes,
                    speedFormatted = progress.speedFormatted,
                    downloadedFormatted = progress.downloadedFormatted,
                    totalFormatted = progress.totalFormatted
                )

                notificationHelper.showProgressNotification(
                    validResult.suggestedFileName,
                    progress.percentage,
                    "${progress.downloadedFormatted} / ${progress.totalFormatted}"
                )
            }

            // Step 4: Verify media integrity using MediaMetadataRetriever
            val metadataResult = extractMetadataAndValidate(tempFile, validResult.suggestedFileName)
            if (metadataResult == null) {
                tempFile.delete()
                val errorMsg = "The downloaded file could not be verified as a valid audio file."
                _uiState.value = ImportUrlUiState.Error(url, errorMsg, canRetry = false)
                notificationHelper.dismissProgressNotification()
                addHistoryEntry(url, validResult.suggestedFileName, "Unknown", ImportStatus.FAILED, errorMsg)
                return
            }

            // Step 5: Duplicate check by Title & Artist against library
            if (!forceReimport) {
                val duplicateTrack = repository.getTrackByTitleAndArtist(metadataResult.title, metadataResult.artist)
                if (duplicateTrack != null) {
                    tempFile.delete()
                    _uiState.value = ImportUrlUiState.DuplicateFound(duplicateTrack, url, validResult)
                    notificationHelper.dismissProgressNotification()
                    return
                }
            }

            // Step 6: Move file permanently into secure internal storage
            val trackId = "url_${System.currentTimeMillis()}_${(1000..9999).random()}"
            val finalFileName = if (forceReimport && File(storageDir, validResult.suggestedFileName).exists()) {
                val base = validResult.suggestedFileName.substringBeforeLast('.')
                val ext = validResult.suggestedFileName.substringAfterLast('.', "mp3")
                "${base}_${System.currentTimeMillis()}.$ext"
            } else {
                validResult.suggestedFileName
            }

            val finalAudioFile = File(storageDir, finalFileName)
            if (finalAudioFile.exists()) {
                finalAudioFile.delete()
            }
            tempFile.copyTo(finalAudioFile, overwrite = true)
            tempFile.delete()

            // Save embedded artwork if present
            var coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop"
            if (metadataResult.embeddedArt != null) {
                val coverFile = File(storageDir, "${trackId}_cover.jpg")
                try {
                    coverFile.outputStream().use { it.write(metadataResult.embeddedArt) }
                    coverUrl = coverFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // Try fetching online album art as fallback
                try {
                    val fetchedArt = AlbumArtService.fetchAlbumArt(metadataResult.artist, metadataResult.title)
                    if (fetchedArt != null) {
                        coverUrl = fetchedArt
                    }
                } catch (_: Exception) {}
            }

            // Step 7: Construct TrackEntity and persist in Room Database
            val track = TrackEntity(
                id = trackId,
                title = metadataResult.title,
                artist = metadataResult.artist,
                album = metadataResult.album,
                durationMs = metadataResult.durationMs,
                audioUrl = finalAudioFile.absolutePath,
                coverUrl = coverUrl,
                genre = metadataResult.genre,
                folderName = "URL Imports",
                importDate = System.currentTimeMillis()
            )

            repository.insertTracks(listOf(track))

            // Step 8: Update notification & state
            notificationHelper.showCompletionNotification(track.title, track.artist)
            _uiState.value = ImportUrlUiState.Success(track)
            addHistoryEntry(
                url = url,
                title = track.title,
                artist = track.artist,
                status = ImportStatus.COMPLETED,
                trackId = track.id,
                fileSize = AudioDownloader.formatBytes(finalAudioFile.length())
            )

        } catch (e: CancellationException) {
            tempFile.delete()
            notificationHelper.dismissProgressNotification()
            _uiState.value = ImportUrlUiState.Idle
            addHistoryEntry(url, validResult.suggestedFileName, "Unknown", ImportStatus.CANCELLED, "Download was cancelled.")
        } catch (e: Exception) {
            tempFile.delete()
            notificationHelper.dismissProgressNotification()
            val message = e.localizedMessage ?: "Failed to download audio file."
            _uiState.value = ImportUrlUiState.Error(url, message, canRetry = true)
            addHistoryEntry(url, validResult.suggestedFileName, "Unknown", ImportStatus.FAILED, message)
        }
    }

    private data class ExtractedMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val genre: String,
        val embeddedArt: ByteArray?
    )

    private fun extractMetadataAndValidate(file: File, fallbackFileName: String): ExtractedMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)

            val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val extractedAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val extractedGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val title = if (!extractedTitle.isNullOrBlank()) {
                extractedTitle.trim()
            } else {
                fallbackFileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ').trim()
            }

            val artist = if (!extractedArtist.isNullOrBlank()) {
                extractedArtist.trim()
            } else {
                "Unknown Artist"
            }

            val album = if (!extractedAlbum.isNullOrBlank()) {
                extractedAlbum.trim()
            } else {
                "URL Import"
            }

            val genre = if (!extractedGenre.isNullOrBlank()) {
                extractedGenre.trim()
            } else {
                "URL Import"
            }

            val embeddedArt = retriever.embeddedPicture

            ExtractedMetadata(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                genre = genre,
                embeddedArt = embeddedArt
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun cancelDownload() {
        activeJob?.cancel()
        activeJob = null
        notificationHelper.dismissProgressNotification()
        _uiState.value = ImportUrlUiState.Idle
    }

    fun retryLast() {
        lastAttemptedUrl?.let { startImport(it) }
    }

    fun dismissResult() {
        _uiState.value = ImportUrlUiState.Idle
    }

    private fun addHistoryEntry(
        url: String,
        title: String,
        artist: String,
        status: ImportStatus,
        errorMessage: String? = null,
        trackId: String? = null,
        fileSize: String = ""
    ) {
        val item = ImportHistoryItem(
            id = "hist_${System.currentTimeMillis()}",
            url = url,
            title = title,
            artist = artist,
            status = status,
            errorMessage = errorMessage,
            trackId = trackId,
            fileSizeFormatted = fileSize
        )
        _history.value = listOf(item) + _history.value.take(19) // Keep last 20
    }

    fun clearHistory() {
        _history.value = emptyList()
    }
}
