package com.example.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.*
import kotlinx.coroutines.*

class DynamicIslandOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_SHOW = "com.example.player.action.SHOW"
        const val ACTION_HIDE = "com.example.player.action.HIDE"
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var dismissJob: Job? = null
    private val isFadingOut = mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                showOverlay()
            }
            ACTION_HIDE -> {
                hideOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val player = AudioPlayerManager.instance
        if (player == null || player.currentTrack.value == null) {
            stopSelf()
            return
        }

        isFadingOut.value = false

        if (overlayView == null) {
            val view = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@DynamicIslandOverlayService)
                setViewTreeViewModelStoreOwner(this@DynamicIslandOverlayService)
                setViewTreeSavedStateRegistryOwner(this@DynamicIslandOverlayService)
                setContent {
                    DynamicIslandOverlayContent(
                        player = player,
                        isFadingOut = isFadingOut,
                        onOpenApp = {
                            openApp()
                        },
                        onInteraction = {
                            cancelDismissTimer()
                        },
                        onCollapse = {
                            startDismissTimer()
                        }
                    )
                }
            }

            val density = resources.displayMetrics.density
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarHeight = if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else {
                (24 * density).toInt()
            }
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                // Modify this 'y' offset (in pixels) to shift the dynamic island up or down.
                // Since FLAG_LAYOUT_IN_SCREEN is omitted, y = 0 aligns it right below the status bar.
                y = (4 * density).toInt()
            }

            try {
                windowManager?.addView(view, params)
                overlayView = view
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
                return
            }
        }

        startDismissTimer()
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        overlayView = null
        view.post {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopSelf()
        }
    }

    private fun startDismissTimer() {
        dismissJob?.cancel()
        dismissJob = serviceScope.launch {
            delay(5000) // Stay visible for 5 seconds
            isFadingOut.value = true
            delay(300) // Wait for fade out animation to finish
            hideOverlay()
        }
    }

    private fun cancelDismissTimer() {
        dismissJob?.cancel()
    }

    private fun openApp() {
        try {
            val launchIntent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        hideOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        hideOverlay()
        stopSelf()
    }
}

@Composable
fun DynamicIslandOverlayContent(
    player: AudioPlayerManager,
    isFadingOut: State<Boolean>,
    onOpenApp: () -> Unit,
    onInteraction: () -> Unit,
    onCollapse: () -> Unit
) {
    val track by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val position by player.playbackPosition.collectAsState()
    val duration by player.playbackDuration.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()

    var isExpanded by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf<Float?>(null) }
    val displayPosition = sliderPosition ?: position.toFloat()

    val animatedWidth by animateDpAsState(
        targetValue = if (isExpanded) 340.dp else 190.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "width"
    )
    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) 155.dp else 40.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "height"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isFadingOut.value) 0f else 1f,
        animationSpec = tween(300),
        label = "alpha"
    )

    val currentTrack = track ?: return

    val context = LocalContext.current
    val currentAccent = remember {
        try {
            val sharedPrefs = context.getSharedPreferences("music_player_settings", android.content.Context.MODE_PRIVATE)
            val savedName = sharedPrefs.getString("theme_accent", com.example.ui.theme.ThemeAccent.SUNSET_AMBER.name)
            com.example.ui.theme.ThemeAccent.valueOf(savedName ?: com.example.ui.theme.ThemeAccent.SUNSET_AMBER.name)
        } catch (e: Exception) {
            com.example.ui.theme.ThemeAccent.SUNSET_AMBER
        }
    }
    val SpotifyGreen = currentAccent.color

    val imageRequest = remember(currentTrack.coverUrl) {
        ImageRequest.Builder(context)
            .data(currentTrack.coverUrl)
            .allowHardware(false)
            .build()
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = Modifier
            .width(animatedWidth)
            .height(animatedHeight)
            .alpha(alpha)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .animateContentSize()
            .pointerInput(isExpanded) {
                detectTapGestures(
                    onTap = {
                        if (!isExpanded) {
                            onOpenApp()
                        }
                    },
                    onLongPress = {
                        if (!isExpanded) {
                            isExpanded = true
                            onInteraction()
                        }
                    }
                )
            }
    ) {
        if (!isExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = currentTrack.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier
                        .width(15.dp)
                        .height(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val transition = rememberInfiniteTransition(label = "overlayBars")
                    val heights = if (isPlaying) {
                        listOf(
                            transition.animateFloat(
                                initialValue = 0.2f, targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
                                label = "ob1"
                            ),
                            transition.animateFloat(
                                initialValue = 0.4f, targetValue = 0.8f,
                                animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
                                label = "ob2"
                            ),
                            transition.animateFloat(
                                initialValue = 0.1f, targetValue = 0.9f,
                                animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
                                label = "ob3"
                            )
                        )
                    } else {
                        listOf(
                            remember { mutableStateOf(0.3f) },
                            remember { mutableStateOf(0.2f) },
                            remember { mutableStateOf(0.3f) }
                        )
                    }
                    heights.forEach { h ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(h.value)
                                .background(SpotifyGreen, shape = RoundedCornerShape(1.dp))
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isExpanded = false
                                onCollapse()
                            }
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Collapse Dynamic Island",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = currentTrack.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack.artist,
                            fontSize = 11.sp,
                            color = SpotifyGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            player.onToggleLike?.invoke(currentTrack)
                            onInteraction()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (currentTrack.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (currentTrack.isLiked) SpotifyGreen else SpotifyGrey,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = com.example.formatDuration(displayPosition.toLong()),
                        fontSize = 10.sp,
                        color = SpotifyGrey,
                        modifier = Modifier.width(36.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                player.skipToPrevious()
                                onInteraction()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous",
                                tint = SpotifyWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                player.togglePlayPause()
                                onInteraction()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = SpotifyGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = SpotifyWhite,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                player.skipToNext()
                                onInteraction()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                tint = SpotifyWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = com.example.formatDuration(duration),
                        fontSize = 10.sp,
                        color = SpotifyGrey,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Slider(
                    value = displayPosition.coerceIn(0f, if (duration > 0) duration.toFloat() else 1f),
                    onValueChange = { newPos ->
                        sliderPosition = newPos
                        onInteraction()
                    },
                    onValueChangeFinished = {
                        sliderPosition?.let {
                            player.seekTo(it.toLong())
                        }
                        sliderPosition = null
                        onCollapse()
                    },
                    valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = SpotifySurfaceVariant,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }
}
