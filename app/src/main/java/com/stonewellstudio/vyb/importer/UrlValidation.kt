package com.stonewellstudio.vyb.importer

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

sealed interface UrlValidationResult {
    data class Valid(
        val url: String,
        val suggestedFileName: String,
        val contentLength: Long, // -1 if unknown / chunked
        val contentType: String,
        val isHttps: Boolean
    ) : UrlValidationResult

    data class Invalid(
        val reason: String,
        val isStreamingSite: Boolean = false,
        val isOversized: Boolean = false
    ) : UrlValidationResult
}

object UrlValidator {
    const val MAX_IMPORT_SIZE_BYTES = 200 * 1024 * 1024L // 200 MB

    val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma"
    )

    val ACCEPTED_AUDIO_MIME_TYPES = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/x-m4a",
        "audio/m4a",
        "audio/aac",
        "audio/wav",
        "audio/x-wav",
        "audio/flac",
        "audio/x-flac",
        "audio/ogg",
        "application/ogg",
        "audio/opus",
        "audio/x-ms-wma",
        "audio/webm",
        "application/octet-stream" // Allowed if file extension is clearly audio
    )

    private val STREAMING_DOMAINS = listOf(
        "youtube.com", "youtu.be", "music.youtube.com",
        "spotify.com", "open.spotify.com",
        "music.apple.com", "apple.com",
        "soundcloud.com",
        "deezer.com",
        "tidal.com",
        "audiomack.com",
        "pandora.com",
        "bandcamp.com"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Syntactic and domain checks before sending any network request.
     */
    fun preValidateUrl(rawUrl: String): UrlValidationResult? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return UrlValidationResult.Invalid("Please enter a URL.")
        }

        val uri = try {
            URI.create(trimmed)
        } catch (e: Exception) {
            return UrlValidationResult.Invalid("The provided link is not a valid URL.")
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return UrlValidationResult.Invalid("Only HTTP and HTTPS links are supported.")
        }

        val host = uri.host?.lowercase() ?: ""
        if (host.isEmpty()) {
            return UrlValidationResult.Invalid("Invalid URL host.")
        }

        // Check if user is attempting to import from YouTube/Spotify/etc.
        for (streamingDomain in STREAMING_DOMAINS) {
            if (host == streamingDomain || host.endsWith(".$streamingDomain")) {
                return UrlValidationResult.Invalid(
                    reason = "Vyb only imports direct audio file links (e.g. .mp3, .flac). Web streaming services like YouTube, Spotify, and Apple Music are not supported.",
                    isStreamingSite = true
                )
            }
        }

        return null // Passed pre-validation
    }

    /**
     * Sanitizes a file name, removing path traversal and forbidden characters.
     */
    fun sanitizeFileName(candidate: String, defaultName: String = "downloaded_track.mp3"): String {
        var name = candidate.trim()

        // Decode URL encoding if present
        try {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {}

        // Remove path separators and traversal attempts
        name = name.replace("../", "")
            .replace("..\\", "")
            .replace("/", "")
            .replace("\\", "")

        // Replace illegal filesystem characters: / \ : * ? " < > |
        name = name.replace(Regex("""[/:*?"<>|\r\n\t]"""), "_")

        // Collapse duplicate underscores/spaces
        name = name.replace(Regex("""_+"""), "_").trim()

        if (name.isEmpty() || name == "." || name == "..") {
            name = defaultName
        }

        // Ensure reasonable length (max 120 chars)
        val extIndex = name.lastIndexOf('.')
        name = if (extIndex != -1 && extIndex > 0) {
            val base = name.substring(0, extIndex).take(100)
            val ext = name.substring(extIndex).take(10)
            base + ext
        } else {
            name.take(100)
        }

        // Ensure it ends with a supported audio extension
        val currentExt = getExtension(name).lowercase()
        if (currentExt !in SUPPORTED_AUDIO_EXTENSIONS) {
            name = "$name.mp3"
        }

        return name
    }

    fun getExtension(fileNameOrUrl: String): String {
        val clean = fileNameOrUrl.substringBefore('?').substringBefore('#')
        val lastDot = clean.lastIndexOf('.')
        return if (lastDot != -1 && lastDot < clean.length - 1) {
            clean.substring(lastDot + 1)
        } else {
            ""
        }
    }

    /**
     * Performs network validation (HEAD or Range GET) to inspect headers.
     */
    suspend fun validateRemoteUrl(rawUrl: String): UrlValidationResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val preCheck = preValidateUrl(rawUrl)
        if (preCheck != null) return@withContext preCheck

        val trimmedUrl = rawUrl.trim()
        val isHttps = trimmedUrl.startsWith("https://", ignoreCase = true)

        try {
            // First try a HEAD request
            val headRequest = Request.Builder()
                .url(trimmedUrl)
                .head()
                .header("User-Agent", "VybMusicPlayer/1.3 (Android; AudioImporter)")
                .build()

            val headResponse = try {
                httpClient.newCall(headRequest).execute()
            } catch (e: Exception) {
                null
            }

            // Some servers return 405 Method Not Allowed on HEAD; fallback to a 1-byte Range GET
            val response = if (headResponse != null && headResponse.isSuccessful) {
                headResponse
            } else {
                headResponse?.close()
                val getRangeRequest = Request.Builder()
                    .url(trimmedUrl)
                    .get()
                    .header("Range", "bytes=0-1024")
                    .header("User-Agent", "VybMusicPlayer/1.3 (Android; AudioImporter)")
                    .build()
                httpClient.newCall(getRangeRequest).execute()
            }

            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    return@withContext UrlValidationResult.Invalid(
                        "Server returned error HTTP ${resp.code} (${resp.message})."
                    )
                }

                val finalUrl = resp.request.url.toString()
                val contentTypeHeader = resp.header("Content-Type")?.lowercase()?.split(";")?.firstOrNull()?.trim() ?: ""
                val contentLengthHeader = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val contentDisposition = resp.header("Content-Disposition") ?: ""

                // Check for rejected web formats
                if (contentTypeHeader.startsWith("text/html") ||
                    contentTypeHeader.startsWith("application/json") ||
                    contentTypeHeader.startsWith("text/plain")
                ) {
                    return@withContext UrlValidationResult.Invalid(
                        "This link doesn't appear to point directly to an audio file (received $contentTypeHeader)."
                    )
                }

                // Check file size limit if reported
                if (contentLengthHeader > MAX_IMPORT_SIZE_BYTES) {
                    val sizeMb = contentLengthHeader / (1024 * 1024)
                    return@withContext UrlValidationResult.Invalid(
                        reason = "This file is too large to import into Vyb ($sizeMb MB; maximum allowed is 200 MB).",
                        isOversized = true
                    )
                }

                // Resolve suggested file name
                val fileNameFromDisposition = parseFilenameFromDisposition(contentDisposition)
                val fileNameFromPath = finalUrl.substringBefore('?').substringBefore('#').substringAfterLast('/')
                val rawCandidate = if (!fileNameFromDisposition.isNullOrEmpty()) {
                    fileNameFromDisposition
                } else if (fileNameFromPath.isNotEmpty()) {
                    fileNameFromPath
                } else {
                    "imported_track.mp3"
                }

                val suggestedFileName = sanitizeFileName(rawCandidate)
                val ext = getExtension(suggestedFileName).lowercase()

                // MIME verification
                val isAudioMime = contentTypeHeader.startsWith("audio/") ||
                        contentTypeHeader in ACCEPTED_AUDIO_MIME_TYPES ||
                        (contentTypeHeader == "application/octet-stream" && ext in SUPPORTED_AUDIO_EXTENSIONS)

                if (!isAudioMime && ext !in SUPPORTED_AUDIO_EXTENSIONS) {
                    return@withContext UrlValidationResult.Invalid(
                        "Unsupported content format: $contentTypeHeader. Vyb supports MP3, M4A, FLAC, WAV, AAC, and OGG."
                    )
                }

                return@withContext UrlValidationResult.Valid(
                    url = finalUrl,
                    suggestedFileName = suggestedFileName,
                    contentLength = contentLengthHeader,
                    contentType = if (contentTypeHeader.isNotEmpty()) contentTypeHeader else "audio/mpeg",
                    isHttps = isHttps
                )
            }
        } catch (e: java.net.UnknownHostException) {
            UrlValidationResult.Invalid("Unable to resolve server host. Please check your network connection.")
        } catch (e: java.net.SocketTimeoutException) {
            UrlValidationResult.Invalid("Connection timed out while reaching the audio server.")
        } catch (e: Exception) {
            UrlValidationResult.Invalid("Network error: ${e.localizedMessage ?: "Unable to connect."}")
        }
    }

    private fun parseFilenameFromDisposition(disposition: String): String? {
        if (disposition.isBlank()) return null
        // Check filename* (RFC 5987 UTF-8 encoded)
        val utf8Match = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE).find(disposition)
        if (utf8Match != null) {
            return try {
                URLDecoder.decode(utf8Match.groupValues[1].trim('"', '\''), StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
                null
            }
        }
        // Check standard filename=
        val standardMatch = Regex("""filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;]+)""", RegexOption.IGNORE_CASE).find(disposition)
        if (standardMatch != null) {
            val raw = standardMatch.groupValues[1].ifEmpty { standardMatch.groupValues[2] }
            return raw.trim('"', '\'').trim()
        }
        return null
    }
}
