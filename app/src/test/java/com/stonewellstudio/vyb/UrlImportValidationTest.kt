package com.stonewellstudio.vyb

import com.stonewellstudio.vyb.importer.UrlValidationResult
import com.stonewellstudio.vyb.importer.UrlValidator
import org.junit.Assert.*
import org.junit.Test

class UrlImportValidationTest {

    @Test
    fun testValidDirectUrls_PassPreValidation() {
        val validUrls = listOf(
            "https://example.com/audio/song.mp3",
            "https://example.com/files/track.m4a",
            "https://example.com/sound/sample.flac",
            "https://cdn.example.org/music/theme.wav",
            "https://example.com/music/test.ogg"
        )

        for (url in validUrls) {
            val result = UrlValidator.preValidateUrl(url)
            assertNull("Expected $url to pass pre-validation", result)
        }
    }

    @Test
    fun testInvalidSchemes_Rejected() {
        val invalidSchemes = listOf(
            "ftp://example.com/song.mp3",
            "file:///sdcard/music/song.mp3",
            "content://media/external/audio/12",
            "javascript:alert(1)",
            "not_a_url",
            ""
        )

        for (url in invalidSchemes) {
            val result = UrlValidator.preValidateUrl(url)
            assertNotNull("Expected $url to be rejected", result)
            assertTrue(result is UrlValidationResult.Invalid)
        }
    }

    @Test
    fun testStreamingServices_StrictlyRejected() {
        val streamingUrls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=12345",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://music.apple.com/us/album/test/123456",
            "https://soundcloud.com/artist/track",
            "https://deezer.com/track/123456"
        )

        for (url in streamingUrls) {
            val result = UrlValidator.preValidateUrl(url)
            assertNotNull("Streaming URL $url must be rejected", result)
            assertTrue(result is UrlValidationResult.Invalid)
            val invalid = result as UrlValidationResult.Invalid
            assertTrue("Must flag as streaming site: $url", invalid.isStreamingSite)
            assertTrue(invalid.reason.contains("streaming services", ignoreCase = true) ||
                       invalid.reason.contains("YouTube", ignoreCase = true))
        }
    }

    @Test
    fun testFileNameSanitization_RemovesPathTraversal() {
        val traversalInput = "../../etc/passwd.mp3"
        val sanitized = UrlValidator.sanitizeFileName(traversalInput)
        assertFalse(sanitized.contains("../"))
        assertFalse(sanitized.contains("/"))
        assertTrue(sanitized.endsWith(".mp3"))
    }

    @Test
    fun testFileNameSanitization_RemovesIllegalCharacters() {
        val dangerousInput = "artist: \"great*song\" <2024>?|cool.mp3"
        val sanitized = UrlValidator.sanitizeFileName(dangerousInput)
        val illegalChars = listOf(':', '*', '?', '"', '<', '>', '|', '/', '\\')
        for (char in illegalChars) {
            assertFalse("Sanitized name must not contain '$char'", sanitized.contains(char))
        }
        assertTrue(sanitized.endsWith(".mp3"))
    }

    @Test
    fun testFileNameSanitization_PreservesValidExtensions() {
        assertEquals("test_song.flac", UrlValidator.sanitizeFileName("test_song.flac"))
        assertEquals("podcast_ep1.m4a", UrlValidator.sanitizeFileName("podcast_ep1.m4a"))
        assertEquals("audio_sample.wav", UrlValidator.sanitizeFileName("audio_sample.wav"))
    }

    @Test
    fun testFileNameSanitization_AppendsDefaultMp3IfNoExtension() {
        val sanitized = UrlValidator.sanitizeFileName("my_cool_audio")
        assertTrue(sanitized.endsWith(".mp3"))
    }

    @Test
    fun testMaxImportSizeConstant() {
        assertEquals(200 * 1024 * 1024L, UrlValidator.MAX_IMPORT_SIZE_BYTES)
    }
}
