package com.example.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.example.MainActivity
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.*

class MediaPlaybackService : Service() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastArtworkUrl: String? = null
    private var lastArtworkBitmap: Bitmap? = null
    private var lastTitle: String? = null
    private var lastArtist: String? = null
    private var lastDuration: Long = -1L
    private var defaultLargeIcon: Bitmap? = null

    companion object {
        const val CHANNEL_ID = "music_player_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.example.ACTION_PLAY"
        const val ACTION_PAUSE = "com.example.ACTION_PAUSE"
        const val ACTION_PREVIOUS = "com.example.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.ACTION_NEXT"
        const val ACTION_STOP = "com.example.ACTION_STOP"
        const val ACTION_LIKE = "com.example.ACTION_LIKE"

        var onPlay: (() -> Unit)? = null
        var onPause: (() -> Unit)? = null
        var onPrevious: (() -> Unit)? = null
        var onNext: (() -> Unit)? = null
        var onSeekTo: ((Long) -> Unit)? = null
        var onLike: (() -> Unit)? = null

        @Volatile
        var isServiceRunning = false

        @Volatile
        var isForeground = false
    }

    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                val prefs = context?.getSharedPreferences("music_player_settings", Context.MODE_PRIVATE)
                val pauseOnUnplug = prefs?.getBoolean("pref_pause_on_unplug", true) ?: true
                if (pauseOnUnplug) {
                    onPause?.invoke()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()
        try {
            defaultLargeIcon = BitmapFactory.decodeResource(resources, android.R.drawable.ic_media_play)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                noisyReceiver,
                android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(noisyReceiver, android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }

        mediaSession = MediaSession(this, "MusicPlayerSession").apply {
            isActive = true
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    MediaPlaybackService.onPlay?.invoke()
                }

                override fun onPause() {
                    MediaPlaybackService.onPause?.invoke()
                }

                override fun onSkipToNext() {
                    MediaPlaybackService.onNext?.invoke()
                }

                override fun onSkipToPrevious() {
                    MediaPlaybackService.onPrevious?.invoke()
                }

                override fun onSeekTo(pos: Long) {
                    MediaPlaybackService.onSeekTo?.invoke(pos)
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_PLAY -> onPlay?.invoke()
            ACTION_PAUSE -> onPause?.invoke()
            ACTION_PREVIOUS -> onPrevious?.invoke()
            ACTION_NEXT -> onNext?.invoke()
            ACTION_LIKE -> onLike?.invoke()
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
        }

        val trackId = intent?.getStringExtra("track_id")
        if (trackId != null) {
            val title = intent?.getStringExtra("track_title") ?: "Unknown Title"
            val artist = intent?.getStringExtra("track_artist") ?: "Unknown Artist"
            val artworkUrl = intent?.getStringExtra("track_artwork") ?: ""
            val isPlaying = intent?.getBooleanExtra("is_playing", false) ?: false
            val position = intent?.getLongExtra("track_position", 0L) ?: 0L
            val duration = intent?.getLongExtra("track_duration", 0L) ?: 0L
            val isLiked = intent?.getBooleanExtra("track_liked", false) ?: false

            updateMediaSessionAndNotification(title, artist, artworkUrl, isPlaying, position, duration, isLiked)
        }

        return START_STICKY
    }

    private fun updateSessionMetadata(
        title: String,
        artist: String,
        artwork: Bitmap?,
        duration: Long
    ) {
        if (lastTitle == title && lastArtist == artist && lastDuration == duration && lastArtworkBitmap == artwork) {
            return
        }
        lastTitle = title
        lastArtist = artist
        lastDuration = duration
        lastArtworkBitmap = artwork

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)

        if (artwork != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, artwork)
        }

        mediaSession?.setMetadata(metadataBuilder.build())
    }

    private fun updateMediaSessionAndNotification(
        title: String,
        artist: String,
        artworkUrl: String,
        isPlaying: Boolean,
        position: Long,
        duration: Long,
        isLiked: Boolean
    ) {
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                position,
                if (isPlaying) 1.0f else 0.0f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())

        updateSessionMetadata(title, artist, lastArtworkBitmap, duration)

        if (artworkUrl.isNotEmpty() && artworkUrl != lastArtworkUrl) {
            lastArtworkUrl = artworkUrl
            serviceScope.launch {
                val bitmap = loadBitmapFromUrl(artworkUrl)
                lastArtworkBitmap = bitmap
                updateSessionMetadata(title, artist, bitmap, duration)
                showNotification(title, artist, isPlaying, bitmap, isLiked, position, duration)
            }
        } else {
            showNotification(title, artist, isPlaying, lastArtworkBitmap, isLiked, position, duration)
        }
    }

    private suspend fun loadBitmapFromUrl(urlStr: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (urlStr.startsWith("http")) {
                val url = URL(urlStr)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input: InputStream = connection.inputStream
                BitmapFactory.decodeStream(input)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun showNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        artwork: Bitmap?,
        isLiked: Boolean,
        position: Long,
        duration: Long
    ) {
        val playPauseIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this,
            2,
            previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val likeIntent = Intent(this, MediaPlaybackService::class.java).apply {
            action = ACTION_LIKE
        }
        val likePendingIntent = PendingIntent.getService(
            this,
            4,
            likeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText("${formatDuration(position)} / ${formatDuration(duration)}")
            .setLargeIcon(artwork ?: defaultLargeIcon)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    previousPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Play",
                    playPausePendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Next",
                    nextPendingIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    if (isLiked) android.R.drawable.star_on else android.R.drawable.star_off,
                    if (isLiked) "Unlike" else "Like",
                    likePendingIntent
                ).build()
            )

        val notification = notificationBuilder.build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isPlaying) {
            if (!isForeground) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID, 
                            notification, 
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    isForeground = true
                } catch (e: Exception) {
                    android.util.Log.e("MediaPlaybackService", "Failed to startForeground", e)
                    manager.notify(NOTIFICATION_ID, notification)
                }
            } else {
                manager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            if (isForeground) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(false)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                isForeground = false
            }
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isForeground = false
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows music playback media controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        isForeground = false
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        isServiceRunning = false
        isForeground = false
        mediaSession?.isActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
