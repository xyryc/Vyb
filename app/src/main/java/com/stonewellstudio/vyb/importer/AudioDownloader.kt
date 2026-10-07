package com.stonewellstudio.vyb.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long, // -1 if unknown
    val percentage: Int, // 0..100
    val speedBytesPerSec: Long,
    val speedFormatted: String,
    val downloadedFormatted: String,
    val totalFormatted: String
)

data class DownloadResult(
    val file: File,
    val sha256Hash: String,
    val totalBytes: Long,
    val contentType: String
)

class AudioDownloader(private val client: OkHttpClient = defaultClient) {

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            } else if (kb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f KB", kb)
            } else {
                "$bytes B"
            }
        }
    }

    /**
     * Downloads an audio file from [url] into [tempTargetFile], emitting progress updates.
     */
    fun downloadFile(
        url: String,
        tempTargetFile: File,
        expectedLength: Long = -1L
    ): Flow<DownloadProgress> = flow {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VybMusicPlayer/1.3 (Android; AudioImporter)")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw java.io.IOException("Download server responded with HTTP ${response.code}")
        }

        val body = response.body ?: throw java.io.IOException("Empty server response body")
        val contentLength = if (expectedLength > 0) expectedLength else body.contentLength()

        val digest = MessageDigest.getInstance("SHA-256")
        var bytesCopied = 0L
        var lastEmitTime = System.currentTimeMillis()
        var lastBytesCopied = 0L
        var speedBps = 0L

        body.byteStream().use { input ->
            FileOutputStream(tempTargetFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()

                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    bytesCopied += bytesRead

                    // Enforce 200MB size protection during download
                    if (bytesCopied > UrlValidator.MAX_IMPORT_SIZE_BYTES) {
                        tempTargetFile.delete()
                        throw java.io.IOException("Download aborted: File exceeded maximum permitted size of 200 MB.")
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime >= 250) {
                        val elapsedSec = (now - lastEmitTime) / 1000.0
                        if (elapsedSec > 0) {
                            speedBps = ((bytesCopied - lastBytesCopied) / elapsedSec).toLong()
                        }
                        lastBytesCopied = bytesCopied
                        lastEmitTime = now

                        val percent = if (contentLength > 0) {
                            ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }

                        emit(
                            DownloadProgress(
                                bytesDownloaded = bytesCopied,
                                totalBytes = contentLength,
                                percentage = percent,
                                speedBytesPerSec = speedBps,
                                speedFormatted = "${formatBytes(speedBps)}/s",
                                downloadedFormatted = formatBytes(bytesCopied),
                                totalFormatted = if (contentLength > 0) formatBytes(contentLength) else "Unknown"
                            )
                        )
                    }
                }
                output.flush()
            }
        }

        // Final 100% emission
        val finalPercent = if (contentLength > 0) 100 else 100
        emit(
            DownloadProgress(
                bytesDownloaded = bytesCopied,
                totalBytes = bytesCopied,
                percentage = finalPercent,
                speedBytesPerSec = 0L,
                speedFormatted = "Complete",
                downloadedFormatted = formatBytes(bytesCopied),
                totalFormatted = formatBytes(bytesCopied)
            )
        )
    }.flowOn(Dispatchers.IO)
}
