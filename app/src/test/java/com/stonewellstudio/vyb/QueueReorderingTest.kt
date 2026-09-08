package com.stonewellstudio.vyb

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.stonewellstudio.vyb.data.TrackEntity
import com.stonewellstudio.vyb.player.AudioPlayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class QueueReorderingTest {

    private lateinit var context: Context
    private lateinit var playerManager: AudioPlayerManager

    private val sampleTracks = listOf(
        TrackEntity(
            id = "t1",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 240000L,
            audioUrl = "http://example.com/1.mp3",
            coverUrl = "http://example.com/1.jpg"
        ),
        TrackEntity(
            id = "t2",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230000L,
            audioUrl = "http://example.com/2.mp3",
            coverUrl = "http://example.com/2.jpg"
        ),
        TrackEntity(
            id = "t3",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200000L,
            audioUrl = "http://example.com/3.mp3",
            coverUrl = "http://example.com/3.jpg"
        ),
        TrackEntity(
            id = "t4",
            title = "Levitating",
            artist = "Dua Lipa",
            album = "Future Nostalgia",
            durationMs = 210000L,
            audioUrl = "http://example.com/4.mp3",
            coverUrl = "http://example.com/4.jpg"
        )
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        playerManager = AudioPlayerManager(context)
        playerManager.setQueue(sampleTracks)
    }

    @Test
    fun testReorderQueue_movesItemCorrectly() {
        // Move item at index 1 ("Starboy") to index 3 (bottom)
        playerManager.reorderQueue(fromIndex = 1, toIndex = 3)
        val updatedQueue = playerManager.playbackQueue.value

        assertEquals(4, updatedQueue.size)
        assertEquals("t1", updatedQueue[0].id)
        assertEquals("t3", updatedQueue[1].id)
        assertEquals("t4", updatedQueue[2].id)
        assertEquals("t2", updatedQueue[3].id)
    }

    @Test
    fun testReorderQueue_moveUpward() {
        // Move item at index 3 ("Levitating") to index 1
        playerManager.reorderQueue(fromIndex = 3, toIndex = 1)
        val updatedQueue = playerManager.playbackQueue.value

        assertEquals(4, updatedQueue.size)
        assertEquals("t1", updatedQueue[0].id)
        assertEquals("t4", updatedQueue[1].id)
        assertEquals("t2", updatedQueue[2].id)
        assertEquals("t3", updatedQueue[3].id)
    }

    @Test
    fun testRemoveFromQueue() {
        playerManager.removeFromQueue(index = 1)
        val updatedQueue = playerManager.playbackQueue.value

        assertEquals(3, updatedQueue.size)
        assertEquals("t1", updatedQueue[0].id)
        assertEquals("t3", updatedQueue[1].id)
        assertEquals("t4", updatedQueue[2].id)
    }

    @Test
    fun testPlayNext_insertsImmediatelyAfterCurrent() {
        // Assume t1 is current
        playerManager.setCurrentTrack(sampleTracks[0])
        val newTrack = TrackEntity(
            id = "t_new",
            title = "After Hours",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 360000L,
            audioUrl = "http://example.com/new.mp3",
            coverUrl = "http://example.com/new.jpg"
        )

        playerManager.playNext(newTrack)
        val updatedQueue = playerManager.playbackQueue.value

        assertEquals(5, updatedQueue.size)
        assertEquals("t1", updatedQueue[0].id)
        assertEquals("t_new", updatedQueue[1].id)
    }

    @Test
    fun testClearQueue_keepsCurrentTrack() {
        playerManager.setCurrentTrack(sampleTracks[0])
        playerManager.clearQueue(keepCurrentTrack = true)
        val updatedQueue = playerManager.playbackQueue.value

        assertEquals(1, updatedQueue.size)
        assertEquals("t1", updatedQueue[0].id)
    }
}
