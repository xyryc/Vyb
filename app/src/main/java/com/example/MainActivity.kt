package com.example

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeAccent
import com.example.player.LyricsUiState
import com.example.player.LyricsService
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.ripple.rememberRipple
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyGrey
import com.example.ui.theme.SpotifySurface
import com.example.ui.theme.SpotifySurfaceVariant
import com.example.ui.theme.SpotifyWhite
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.ScreenState
import com.example.player.ArtworkProcessor
import java.util.Calendar
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

import com.example.localization.AppLanguage
import com.example.localization.LocalAppLanguage
import com.example.localization.Translator
import com.example.localization.t
import com.example.localization.getLanguageWithFlag
import com.example.localization.localizePlaylistName
import com.example.localization.localizePlaylistDescription

val SpotifyGreen: Color
    @Composable
    get() = com.example.ui.theme.LocalAccentColor.current

private fun getScreenOrdinal(screen: ScreenState): Int {
    return when (screen) {
        is ScreenState.Home -> 0
        is ScreenState.Search -> 1
        is ScreenState.Library -> 2
        is ScreenState.PlaylistDetail -> 3
        is ScreenState.Settings -> 4
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            val context = LocalContext.current
            val viewModel: MusicViewModel = viewModel(
                factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    context.applicationContext as Application
                )
            )
            val themeAccent by viewModel.currentThemeAccent.collectAsState()

            MyApplicationTheme(primaryColor = themeAccent.color) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        stopDynamicIsland()
    }

    override fun onResume() {
        super.onResume()
        stopDynamicIsland()
    }

    private fun stopDynamicIsland() {
        try {
            stopService(Intent(this, com.example.player.DynamicIslandOverlayService::class.java))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = com.example.player.AudioPlayerManager.instance
        if (player != null && player.isPlaying.value && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, com.example.player.DynamicIslandOverlayService::class.java).apply {
                action = com.example.player.DynamicIslandOverlayService.ACTION_SHOW
            }
            try {
                startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

fun formatSleepTimer(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun MainAppScreen(
    viewModel: MusicViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("music_player_settings", android.content.Context.MODE_PRIVATE)
    }
    var language by remember {
        val saved = sharedPrefs.getString("pref_language", "English 🇮🇪") ?: "English 🇮🇪"
        mutableStateOf(getLanguageWithFlag(saved))
    }
    var showOnboarding by remember {
        mutableStateOf(!sharedPrefs.getBoolean("onboarding_completed", false))
    }
    val currentAppLanguage = AppLanguage.fromString(language)
    val layoutDirection = if (currentAppLanguage == AppLanguage.PALESTINE_ARABIC || 
                              currentAppLanguage == AppLanguage.FARSI) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.importLocalMp3(it)
        }
    }

    val directoryPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.importLocalFolder(it)
        }
    }

    val currentScreen by viewModel.currentScreen.collectAsState()
    val allTracks by viewModel.allTracks.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val selectedPlaylistTracks by viewModel.selectedPlaylistTracks.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()

    var hasInitializedPlayerState by remember { mutableStateOf(false) }
    LaunchedEffect(isPlayerExpanded) {
        if (hasInitializedPlayerState) {
            triggerHapticFeedback(context, "snap")
        } else {
            hasInitializedPlayerState = true
        }
    }
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsState()
    val showSleepTimerDialog by viewModel.showSleepTimerDialog.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val lyricsUiState by viewModel.lyricsUiState.collectAsState()
    val themeAccent by viewModel.currentThemeAccent.collectAsState()
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()
    val controlsOpacity by viewModel.controlsOpacity.collectAsState()
    val ambientGlowEnabled by viewModel.ambientGlowEnabled.collectAsState()
    val blurIntensity by viewModel.blurIntensity.collectAsState()

    // Drag-to-expand states
    val density = LocalDensity.current
    val displayMetrics = context.resources.displayMetrics
    var screenHeightPx by remember { mutableStateOf(displayMetrics.heightPixels.toFloat()) }

    var dragOffset by remember { mutableStateOf<Float?>(null) }
    val targetOffset = if (isPlayerExpanded) 0f else screenHeightPx
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffset ?: targetOffset,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "PlayerOffset"
    )
    val miniPlayerAlpha = (animatedOffset / screenHeightPx).coerceIn(0f, 1f)

    // Player states
    val currentTrack by viewModel.playerManager.currentTrack.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val playbackPosition by viewModel.playerManager.playbackPosition.collectAsState()
    val playbackDuration by viewModel.playerManager.playbackDuration.collectAsState()
    val isBuffering by viewModel.playerManager.isBuffering.collectAsState()
    val isShuffleEnabled by viewModel.playerManager.isShuffleEnabled.collectAsState()
    val isRepeatEnabled by viewModel.playerManager.isRepeatEnabled.collectAsState()
    var showEqualizer by remember { mutableStateOf(false) }

    // Dynamic Gradient Background states
    var dominantColor by remember { mutableStateOf(SpotifyBlack) }
    var secondaryColor by remember { mutableStateOf(SpotifyBlack) }

    LaunchedEffect(currentTrack?.coverUrl) {
        val coverUrl = currentTrack?.coverUrl
        if (!coverUrl.isNullOrEmpty()) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .allowHardware(false) // Required for Palette to extract pixels
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        val bitmap = drawable.bitmap
                        Palette.from(bitmap).generate { palette ->
                            val dom = palette?.getDominantColor(android.graphics.Color.BLACK) ?: android.graphics.Color.BLACK
                            val vibrant = palette?.getVibrantColor(android.graphics.Color.BLACK) ?: dom
                            val darkMuted = palette?.getDarkMutedColor(android.graphics.Color.BLACK) ?: android.graphics.Color.BLACK
                            
                            dominantColor = Color(vibrant)
                            secondaryColor = Color(darkMuted)
                        }
                    }
                }
            } catch (e: Exception) {
                dominantColor = SpotifyBlack
                secondaryColor = SpotifyBlack
            }
        } else {
            dominantColor = SpotifyBlack
            secondaryColor = SpotifyBlack
        }
    }

    val animatedDominantColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1000)
    )
    val animatedSecondaryColor by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(durationMillis = 1000)
    )

    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalLayoutDirection provides layoutDirection
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpotifyBlack)
                .onSizeChanged { size ->
                    screenHeightPx = size.height.toFloat()
                }
        ) {
        // Dynamic Backdrop Gradient
        if (currentTrack != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedDominantColor.copy(alpha = 0.45f),
                                animatedSecondaryColor.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SpotifyBlack.copy(alpha = 0.85f),
                    modifier = Modifier.testTag("bottom_nav")
                ) {
                    NavigationBarItem(
                        selected = currentScreen is ScreenState.Home,
                        onClick = { viewModel.navigateTo(ScreenState.Home) },
                        icon = { Icon(if (currentScreen is ScreenState.Home) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = "Home") },
                        label = { Text(t("home", language), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifyGrey,
                            unselectedTextColor = SpotifyGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen is ScreenState.Search,
                        onClick = { viewModel.navigateTo(ScreenState.Search) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        label = { Text(t("search", language), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifyGrey,
                            unselectedTextColor = SpotifyGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen is ScreenState.Library || currentScreen is ScreenState.PlaylistDetail,
                        onClick = { viewModel.navigateTo(ScreenState.Library) },
                        icon = { Icon(if (currentScreen is ScreenState.Library) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Outlined.List, contentDescription = "Library") },
                        label = { Text(t("library", language), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifyGrey,
                            unselectedTextColor = SpotifyGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen is ScreenState.Settings,
                        onClick = { viewModel.navigateTo(ScreenState.Settings) },
                        icon = { Icon(if (currentScreen is ScreenState.Settings) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Settings") },
                        label = { Text(t("settings", language), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SpotifyGreen,
                            selectedTextColor = SpotifyGreen,
                            unselectedIconColor = SpotifyGrey,
                            unselectedTextColor = SpotifyGrey,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            },
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Screen Content
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val initialOrdinal = getScreenOrdinal(initialState)
                        val targetOrdinal = getScreenOrdinal(targetState)
                        if (targetOrdinal > initialOrdinal) {
                            // Slide in from right to left (new screen slides in from right, old slides out to left)
                            (slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(300))).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(300))
                            )
                        } else {
                            // Slide in from left to right (new screen slides in from left, old slides out to right)
                            (slideInHorizontally { width -> -width } + fadeIn(animationSpec = tween(300))).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut(animationSpec = tween(300))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        is ScreenState.Home -> HomeScreen(
                            tracks = allTracks,
                            onTrackClick = { track -> viewModel.playTrack(track, allTracks) },
                            onPlaylistClick = { viewModel.navigateTo(ScreenState.PlaylistDetail(it)) },
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            onLikeClick = { viewModel.toggleLike(it) }
                        )

                        is ScreenState.Search -> SearchScreen(
                            searchQuery = searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            searchResults = searchResults,
                            onTrackClick = { track -> viewModel.playTrack(track, searchResults) },
                            currentTrack = currentTrack,
                            onLikeClick = { viewModel.toggleLike(it) }
                        )

                        is ScreenState.Library -> LibraryScreen(
                            playlists = playlists,
                            likedTracks = likedTracks,
                            allTracks = allTracks,
                            viewModel = viewModel,
                            onPlaylistClick = { viewModel.navigateTo(ScreenState.PlaylistDetail(it)) },
                            onTrackClick = { track -> viewModel.playTrack(track, likedTracks) },
                            onCreatePlaylistClick = { viewModel.showCreatePlaylistDialog(true) },
                            onImportFileClick = { filePickerLauncher.launch("audio/*") },
                            onImportFolderClick = { directoryPickerLauncher.launch(null) },
                            currentTrack = currentTrack,
                            onLikeClick = { viewModel.toggleLike(it) }
                        )

                        is ScreenState.PlaylistDetail -> PlaylistDetailScreen(
                            playlist = screen.playlist,
                            tracks = selectedPlaylistTracks,
                            allTracks = allTracks,
                            language = language,
                            onTrackClick = { track -> viewModel.playTrack(track, selectedPlaylistTracks) },
                            currentTrack = currentTrack,
                            isPlaying = isPlaying,
                            onDeletePlaylist = { viewModel.deletePlaylist(screen.playlist.id) },
                            onRemoveTrack = { track -> viewModel.removeTrackFromPlaylist(screen.playlist.id, track.id) },
                            onAddTrack = { track -> viewModel.addTrackToPlaylist(screen.playlist.id, track.id) },
                            onLikeClick = { viewModel.toggleLike(it) },
                            onBackClick = { viewModel.navigateTo(ScreenState.Library) }
                        )

                        is ScreenState.Settings -> SettingsScreen(
                            viewModel = viewModel,
                            language = language,
                            onLanguageChange = { language = it },
                            onReplayOnboarding = {
                                sharedPrefs.edit().putBoolean("onboarding_completed", false).apply()
                                showOnboarding = true
                            }
                        )
                    }
                }

                 // Mini Player (Only visible if a track is selected and player is not expanded)
                if (currentTrack != null && miniPlayerAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                            .graphicsLayer {
                                alpha = miniPlayerAlpha
                                translationY = (1f - miniPlayerAlpha) * 100f
                            }
                            .draggable(
                                state = rememberDraggableState { delta ->
                                    val current = dragOffset ?: screenHeightPx
                                    dragOffset = (current + delta).coerceIn(0f, screenHeightPx)
                                },
                                orientation = Orientation.Vertical,
                                onDragStarted = {
                                    dragOffset = screenHeightPx
                                },
                                onDragStopped = { velocity ->
                                    val current = dragOffset ?: screenHeightPx
                                    if (current < screenHeightPx * 0.8f || velocity < -500f) {
                                        viewModel.setPlayerExpanded(true)
                                    } else {
                                        viewModel.setPlayerExpanded(false)
                                    }
                                    dragOffset = null
                                }
                            )
                    ) {
                        MiniPlayer(
                            track = currentTrack!!,
                            isPlaying = isPlaying,
                            position = playbackPosition,
                            duration = playbackDuration,
                            isBuffering = isBuffering,
                            onPlayPauseClick = { viewModel.playerManager.togglePlayPause() },
                            onLikeClick = { viewModel.toggleLike(currentTrack!!) },
                            onSkipNextClick = { viewModel.playerManager.skipToNext() },
                            onSkipPreviousClick = { viewModel.playerManager.skipToPrevious() },
                            onSeek = { viewModel.playerManager.seekTo(it) },
                            onClick = { if (miniPlayerAlpha > 0.8f) viewModel.setPlayerExpanded(true) },
                            controlsOpacity = controlsOpacity
                        )
                    }
                }
            }
        }

        // Expanded full-screen player with drag support
        if (currentTrack != null && animatedOffset < screenHeightPx) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, animatedOffset.toInt()) }
            ) {
                ExpandedPlayerScreen(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    position = playbackPosition,
                    duration = playbackDuration,
                    isBuffering = isBuffering,
                    isShuffleEnabled = isShuffleEnabled,
                    isRepeatEnabled = isRepeatEnabled,
                    playlists = playlists,
                    lyricsState = lyricsUiState,
                    themeAccent = themeAccent,
                    visualizerStyle = visualizerStyle,
                    onSeek = { viewModel.playerManager.seekTo(it) },
                    onPlayPauseClick = { viewModel.playerManager.togglePlayPause() },
                    onPreviousClick = { viewModel.playerManager.skipToPrevious() },
                    onNextClick = { viewModel.playerManager.skipToNext() },
                    onShuffleClick = { viewModel.playerManager.toggleShuffle() },
                    onRepeatClick = { viewModel.playerManager.toggleRepeat() },
                    onLikeClick = { viewModel.toggleLike(currentTrack!!) },
                    onAddToPlaylistClick = { viewModel.showAddToPlaylistDialog(currentTrack) },
                    onEqualizerClick = { showEqualizer = true },
                    onCollapse = { viewModel.setPlayerExpanded(false) },
                    onFetchAlbumArtClick = { viewModel.manualDownloadAlbumArt(currentTrack!!) },
                    sleepTimerRemaining = sleepTimerRemaining,
                    onSleepTimerClick = { viewModel.showSleepTimerDialog(true) },
                    dominantColor = animatedDominantColor,
                    secondaryColor = animatedSecondaryColor,
                    headerModifier = Modifier.draggable(
                        state = rememberDraggableState { delta ->
                            val current = dragOffset ?: 0f
                            dragOffset = (current + delta).coerceIn(0f, screenHeightPx)
                        },
                        orientation = Orientation.Vertical,
                        onDragStarted = {
                            dragOffset = 0f
                        },
                        onDragStopped = { velocity ->
                            val current = dragOffset ?: 0f
                            if (current > screenHeightPx * 0.2f || velocity > 500f) {
                                viewModel.setPlayerExpanded(false)
                            } else {
                                viewModel.setPlayerExpanded(true)
                            }
                            dragOffset = null
                        }
                    ),
                    controlsOpacity = controlsOpacity,
                    ambientGlowEnabled = ambientGlowEnabled,
                    blurIntensity = blurIntensity
                )
            }
        }

        // Dialogs
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { viewModel.showCreatePlaylistDialog(false) },
                onConfirm = { name, desc -> viewModel.createPlaylist(name, desc) }
            )
        }

        if (showAddToPlaylistDialog != null) {
            AddToPlaylistDialog(
                track = showAddToPlaylistDialog!!,
                playlists = playlists,
                onDismiss = { viewModel.showAddToPlaylistDialog(null) },
                onPlaylistSelected = { playlistId ->
                    viewModel.addTrackToPlaylist(playlistId, showAddToPlaylistDialog!!.id)
                }
            )
        }

        if (showEqualizer) {
            EqualizerDialog(
                viewModel = viewModel,
                onDismiss = { showEqualizer = false }
            )
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                onDismiss = { viewModel.showSleepTimerDialog(false) },
                onSelectTimer = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    viewModel.showSleepTimerDialog(false)
                },
                onCancelTimer = {
                    viewModel.cancelSleepTimer()
                    viewModel.showSleepTimerDialog(false)
                },
                currentRemainingMs = sleepTimerRemaining,
                accentColor = themeAccent.color
            )
        }

        if (showOnboarding) {
            OnboardingScreen(
                language = language,
                onDismiss = {
                    sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
                    showOnboarding = false
                }
            )
        }
    }
    }
}

@Composable
fun TrackCoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val processedSource = remember(url) {
        ArtworkProcessor.getProcessedArtworkSource(context, url)
    }
    AsyncImage(
        model = processedSource,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        error = painterResource(id = R.drawable.ic_launcher_foreground)
    )
}

@Composable
fun HomeScreen(
    tracks: List<TrackEntity>,
    onTrackClick: (TrackEntity) -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    onLikeClick: (TrackEntity) -> Unit
) {
    var hour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }

    // Periodically update the hour of the day in case the app stays open
    LaunchedEffect(Unit) {
        while (true) {
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(30000) // Update every 30 seconds
        }
    }

    val language = LocalAppLanguage.current
    val greetingText = when (hour) {
        in 5..11 -> t("good_morning", language) + " 🌅"
        in 12..16 -> t("good_afternoon", language) + " ☀️"
        in 17..21 -> t("good_evening", language) + " 🌇"
        else -> t("late_night_vibes", language)
    }

    val greetingSubtitle = when (hour) {
        in 5..11 -> t("morning_sub", language)
        in 12..16 -> t("afternoon_sub", language)
        in 17..21 -> t("evening_sub", language)
        else -> t("night_sub", language)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    text = greetingText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = greetingSubtitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = SpotifyGrey
                )
            }
        }

        item {
            DynamicIslandPermissionBanner()
        }

        // Quick Grid recommendations (like Spotify's top grid)
        if (tracks.isNotEmpty()) {
            item {
                val gridTracks = tracks.take(6)
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    for (i in gridTracks.indices step 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val trackOne = gridTracks[i]
                            QuickGridItem(
                                track = trackOne,
                                isCurrent = currentTrack?.id == trackOne.id,
                                isPlaying = isPlaying && currentTrack?.id == trackOne.id,
                                onClick = { onTrackClick(trackOne) },
                                modifier = Modifier.weight(1f)
                            )
                            if (i + 1 < gridTracks.size) {
                                val trackTwo = gridTracks[i + 1]
                                QuickGridItem(
                                    track = trackTwo,
                                    isCurrent = currentTrack?.id == trackTwo.id,
                                    isPlaying = isPlaying && currentTrack?.id == trackTwo.id,
                                    onClick = { onTrackClick(trackTwo) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Made For You: AI Smart Mixes
        item {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = t("made_for_you", language),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyWhite,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    getSmartMixes().forEach { mix ->
                        SmartMixCard(
                            playlist = mix,
                            language = language,
                            onClick = { onPlaylistClick(mix) }
                        )
                    }
                }
            }
        }

        // Featured Songs list
        item {
            Text(
                text = t("more_what_like", language),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        items(tracks) { track ->
            TrackListItem(
                track = track,
                isCurrent = currentTrack?.id == track.id,
                isPlaying = isPlaying && currentTrack?.id == track.id,
                onClick = { onTrackClick(track) },
                onLikeClick = { onLikeClick(track) }
            )
        }
    }
}

fun getSmartMixes(): List<PlaylistEntity> {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val (timeName, timeDesc) = when (hour) {
        in 6..11 -> Pair("Morning Acoustic & Chill", "Gentle acoustic strings and lofi vibes to start your day.")
        in 12..17 -> Pair("Afternoon Energy Mix", "High-tempo Synthwave and Techno to fuel your focus.")
        else -> Pair("Late Night Relax Mix", "Ambient soundscapes and vaporwave echoes for winding down.")
    }
    return listOf(
        PlaylistEntity(
            id = -1,
            name = "Heavy Rotation",
            description = "Your absolute most-played anthems, updated live"
        ),
        PlaylistEntity(
            id = -2,
            name = "Forgotten Favorites",
            description = "Beloved tracks that deserve another listen"
        ),
        PlaylistEntity(
            id = -3,
            name = timeName,
            description = timeDesc
        )
    )
}



@Composable
fun SmartMixCard(
    playlist: PlaylistEntity,
    language: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradient = when (playlist.id) {
        -1 -> Brush.linearGradient(
            colors = listOf(Color(0xFFEC008C), Color(0xFFFC6767))
        )
        -2 -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF8C00), Color(0xFF8B0000))
        )
        else -> {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..11 -> Brush.linearGradient(
                    colors = listOf(Color(0xFFFF9A9E), Color(0xFFFECFEF), Color(0xFFFEC107))
                )
                in 12..17 -> Brush.linearGradient(
                    colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
                )
                else -> Brush.linearGradient(
                    colors = listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                )
            }
        }
    }

    val icon = when (playlist.id) {
        -1 -> Icons.Filled.Whatshot
        -2 -> Icons.Filled.History
        else -> Icons.Filled.AccessTime
    }

    Card(
        modifier = modifier
            .width(180.dp)
            .height(200.dp)
            .clickable(onClick = onClick)
            .testTag("smart_mix_card_${playlist.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = SpotifyWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        text = t("smart_mix_badge", language).uppercase(),
                        color = SpotifyWhite.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Column {
                    Text(
                        text = localizePlaylistName(playlist.name, language),
                        color = SpotifyWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = localizePlaylistDescription(playlist.description, language),
                        color = SpotifyWhite.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun QuickGridItem(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = SpotifySurface),
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick)
            .testTag("quick_item_${track.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            TrackCoverImage(
                url = track.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
            )
            Text(
                text = track.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) SpotifyGreen else SpotifyWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = "Playing",
                    tint = SpotifyGreen,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(16.dp)
                )
            }
        }
    }
}

@Composable
fun TrackListItem(
    track: TrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("track_item_${track.id}")
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            TrackCoverImage(
                url = track.coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Playing",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCurrent) SpotifyGreen else SpotifyWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                fontSize = 13.sp,
                color = SpotifyGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingContent != null) {
            trailingContent()
        } else {
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.testTag("like_btn_${track.id}")
            ) {
                Icon(
                    imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (track.isLiked) SpotifyGreen else SpotifyGrey,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<TrackEntity>,
    onTrackClick: (TrackEntity) -> Unit,
    currentTrack: TrackEntity?,
    onLikeClick: (TrackEntity) -> Unit
) {
    val language = LocalAppLanguage.current
    val categories = listOf(
        Pair("Synthwave", Color(0xFFE91E63)),
        Pair("Vaporwave", Color(0xFF9C27B0)),
        Pair("Acoustic", Color(0xFF4CAF50)),
        Pair("Techno", Color(0xFF00BCD4)),
        Pair("Lofi", Color(0xFFFF9800)),
        Pair("Ambient", Color(0xFF3F51B5)),
        Pair("Electronic", Color(0xFF009688)),
        Pair("Rock", Color(0xFF795548))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = t("search", language),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyWhite,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text(t("what_listen", language), color = SpotifyGrey) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = t("search", language), tint = SpotifyGrey) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = SpotifyGrey)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SpotifySurface,
                unfocusedContainerColor = SpotifySurface,
                disabledContainerColor = SpotifySurface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = SpotifyWhite,
                unfocusedTextColor = SpotifyWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("search_field")
        )

        if (searchQuery.isEmpty()) {
            Text(
                text = t("browse_all", language),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(categories) { category ->
                    GenreCategoryCard(
                        genre = category.first,
                        backgroundColor = category.second,
                        onClick = { onQueryChange(category.first) }
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (searchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t("no_results_found", language).replace("%s", searchQuery),
                                color = SpotifyGrey,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(searchResults) { track ->
                        TrackListItem(
                            track = track,
                            isCurrent = currentTrack?.id == track.id,
                            isPlaying = false,
                            onClick = { onTrackClick(track) },
                            onLikeClick = { onLikeClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GenreCategoryCard(
    genre: String,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    val language = LocalAppLanguage.current
    val translatedGenre = t("genre_${genre.lowercase()}", language)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("genre_card_$genre")
    ) {
        Text(
            text = translatedGenre,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyWhite,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Icon(
            imageVector = Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.25f),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.BottomEnd)
        )
    }
}

@Composable
fun LibraryScreen(
    playlists: List<PlaylistEntity>,
    likedTracks: List<TrackEntity>,
    allTracks: List<TrackEntity>,
    viewModel: MusicViewModel,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onImportFolderClick: () -> Unit,
    currentTrack: TrackEntity?,
    onLikeClick: (TrackEntity) -> Unit
) {
    val language = LocalAppLanguage.current
    var selectedTab by remember { mutableStateOf(0) } // 0 = Playlists, 1 = Liked Songs, 2 = Insights, 3 = Smart Folders
    var showImportMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp)
        ) {
            Text(
                text = t("library", language),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { showImportMenu = true },
                        modifier = Modifier.testTag("import_music_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Publish,
                            contentDescription = t("import_songs", language),
                            tint = SpotifyGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showImportMenu,
                        onDismissRequest = { showImportMenu = false },
                        modifier = Modifier.background(SpotifySurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text(t("import_songs", language), color = SpotifyWhite) },
                            onClick = {
                                showImportMenu = false
                                onImportFileClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SpotifyGreen) }
                        )
                        DropdownMenuItem(
                            text = { Text(t("import_folder", language), color = SpotifyWhite) },
                            onClick = {
                                showImportMenu = false
                                onImportFolderClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, tint = SpotifyGreen) }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onCreatePlaylistClick,
                    modifier = Modifier.testTag("create_playlist_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = t("create_playlist", language),
                        tint = SpotifyWhite,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Custom tabs with horizontal scroll to support multi-tab layouts gracefully
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {
            Button(
                onClick = { selectedTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 0) SpotifyGreen else SpotifySurface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("playlists_tab")
            ) {
                Text(
                    t("playlists", language),
                    color = if (selectedTab == 0) SpotifyBlack else SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { selectedTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 1) SpotifyGreen else SpotifySurface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("liked_songs_tab")
            ) {
                Text(
                    t("liked_songs", language),
                    color = if (selectedTab == 1) SpotifyBlack else SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { selectedTab = 3 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 3) SpotifyGreen else SpotifySurface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .testTag("folders_tab")
            ) {
                Text(
                    t("smart_folders", language),
                    color = if (selectedTab == 3) SpotifyBlack else SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = { selectedTab = 2 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == 2) SpotifyGreen else SpotifySurface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("insights_tab")
            ) {
                Text(
                    t("insights", language),
                    color = if (selectedTab == 2) SpotifyBlack else SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Playlists List with Smart Mixes always visible
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = t("made_for_you", language),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    items(getSmartMixes()) { playlist ->
                        PlaylistListItem(
                            playlist = playlist,
                            language = language,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }

                    item {
                        Text(
                            text = t("your_playlists", language),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        )
                    }

                    if (playlists.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = SpotifySurface),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = t("no_custom_playlists", language),
                                        color = SpotifyGrey,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onCreatePlaylistClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                                    ) {
                                        Text(t("create_playlist", language), color = SpotifyBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(playlists) { playlist ->
                            PlaylistListItem(
                                playlist = playlist,
                                language = language,
                                onClick = { onPlaylistClick(playlist) }
                            )
                        }
                    }
                }
            }
            1 -> {
                // Liked Songs List
                if (likedTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = SpotifyGrey, modifier = Modifier.size(64.dp))
                            Text(
                                t("liked_songs_empty", language),
                                color = SpotifyGrey,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(likedTracks) { track ->
                            TrackListItem(
                                track = track,
                                isCurrent = currentTrack?.id == track.id,
                                isPlaying = false,
                                onClick = { onTrackClick(track) },
                                onLikeClick = { onLikeClick(track) }
                            )
                        }
                    }
                }
            }
            2 -> {
                ListeningInsightsDashboard(
                    tracks = allTracks,
                    viewModel = viewModel,
                    onTrackClick = onTrackClick,
                    onLikeClick = onLikeClick
                )
            }
            3 -> {
                SmartFoldersSection(
                    tracks = allTracks,
                    onTrackClick = onTrackClick,
                    onLikeClick = onLikeClick,
                    currentTrack = currentTrack
                )
            }
        }
    }
}

@Composable
fun SmartFoldersSection(
    tracks: List<TrackEntity>,
    onTrackClick: (TrackEntity) -> Unit,
    onLikeClick: (TrackEntity) -> Unit,
    currentTrack: TrackEntity?
) {
    val language = LocalAppLanguage.current
    // Grouping modes: 0 = Physical Folders, 1 = Artist, 2 = Genre, 3 = Import Date
    var groupingMode by remember { mutableStateOf(0) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    // Group the tracks dynamically based on groupingMode
    val groupedTracks = remember(tracks, groupingMode) {
        when (groupingMode) {
            0 -> {
                // Physical Folders
                tracks.groupBy { 
                    if (it.folderName.isEmpty()) "Prepopulated Collection" else it.folderName 
                }
            }
            1 -> {
                // Smart Folder: Artist
                tracks.groupBy { it.artist }
            }
            2 -> {
                // Smart Folder: Genre
                tracks.groupBy { it.genre }
            }
            else -> {
                // Smart Folder: Import Date
                tracks.groupBy { track ->
                    if (track.importDate == 0L) {
                        "Prepopulated Collection"
                    } else {
                        val diffMs = System.currentTimeMillis() - track.importDate
                        val diffHours = diffMs / (1000 * 60 * 60)
                        when {
                            diffHours < 24 -> "Today"
                            diffHours < 168 -> "This Week"
                            diffHours < 720 -> "This Month"
                            else -> "Earlier This Year"
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedGroup == null) {
            // Group Selection Grid/List
            Text(
                text = t("organize_by", language),
                color = SpotifyGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair(0, t("folders_label", language)),
                    Pair(1, t("artists_label", language)),
                    Pair(2, t("genres_label", language)),
                    Pair(3, t("import_dates_label", language))
                ).forEach { (mode, label) ->
                    val isSelected = groupingMode == mode
                    FilterChip(
                        selected = isSelected,
                        onClick = { groupingMode = mode },
                        label = { Text(label, color = if (isSelected) SpotifyBlack else SpotifyWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            containerColor = SpotifySurface
                        ),
                        border = null,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (groupedTracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = SpotifyGrey, modifier = Modifier.size(64.dp))
                        Text(
                            t("no_tracks_found_group", language),
                            color = SpotifyGrey,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(groupedTracks.keys.toList().sorted()) { groupKey ->
                        val groupTracks = groupedTracks[groupKey] ?: emptyList()
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGroup = groupKey },
                            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (groupingMode) {
                                            0 -> Icons.Filled.Folder
                                            1 -> Icons.Filled.Person
                                            2 -> Icons.Filled.MusicNote
                                            else -> Icons.Filled.CalendarToday
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        val displayKey = when (groupKey) {
                                            "Prepopulated Collection" -> t("prepopulated_collection", language)
                                            "Today" -> t("today", language)
                                            "This Week" -> t("this_week", language)
                                            "This Month" -> t("this_month", language)
                                            "Earlier This Year" -> t("earlier_this_year", language)
                                            else -> groupKey
                                        }
                                        Text(
                                            text = displayKey,
                                            fontWeight = FontWeight.Bold,
                                            color = SpotifyWhite,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${groupTracks.size} ${if (groupTracks.size == 1) t("track_singular", language) else t("track_plural", language)}",
                                            color = SpotifyGrey,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = SpotifyGrey,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Nested track list for the selected group
            val currentGroupKey = selectedGroup!!
            val groupTracks = groupedTracks[currentGroupKey] ?: emptyList()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectedGroup = null },
                    modifier = Modifier
                        .size(36.dp)
                        .background(SpotifySurface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = t("back", language),
                        tint = SpotifyWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (groupingMode) {
                                0 -> Icons.Filled.Folder
                                1 -> Icons.Filled.Person
                                2 -> Icons.Filled.MusicNote
                                else -> Icons.Filled.CalendarToday
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (groupingMode) {
                                0 -> t("physical_folder", language)
                                1 -> t("artist_smart_folder", language)
                                2 -> t("genre_smart_folder", language)
                                else -> t("time_group", language)
                            },
                            color = SpotifyGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val displayGroupKey = when (currentGroupKey) {
                        "Prepopulated Collection" -> t("prepopulated_collection", language)
                        "Today" -> t("today", language)
                        "This Week" -> t("this_week", language)
                        "This Month" -> t("this_month", language)
                        "Earlier This Year" -> t("earlier_this_year", language)
                        else -> currentGroupKey
                    }
                    Text(
                        text = displayGroupKey,
                        color = SpotifyWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(groupTracks) { track ->
                    TrackListItem(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = false,
                        onClick = { onTrackClick(track) },
                        onLikeClick = { onLikeClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistListItem(
    playlist: PlaylistEntity,
    language: String,
    onClick: () -> Unit
) {
    val isSmart = playlist.id < 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("playlist_item_${playlist.id}")
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isSmart) {
                        when (playlist.id) {
                            -1 -> Brush.linearGradient(colors = listOf(Color(0xFFEC008C), Color(0xFFFC6767)))
                            -2 -> Brush.linearGradient(colors = listOf(Color(0xFFFF8C00), Color(0xFF8B0000)))
                            else -> Brush.linearGradient(colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
                        }
                    } else {
                        Brush.linearGradient(colors = listOf(SpotifySurface, SpotifySurface))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSmart) {
                val icon = when (playlist.id) {
                    -1 -> Icons.Filled.Whatshot
                    -2 -> Icons.Filled.History
                    else -> Icons.Filled.AccessTime
                }
                Icon(icon, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(28.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = localizePlaylistName(playlist.name, language),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SpotifyWhite,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isSmart) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SpotifyGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = t("smart_mix_badge", language),
                            color = SpotifyGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = if (playlist.description.isEmpty()) t("playlist_badge", language) else localizePlaylistDescription(playlist.description, language),
                fontSize = 13.sp,
                color = SpotifyGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    tracks: List<TrackEntity>,
    allTracks: List<TrackEntity>,
    language: String,
    onTrackClick: (TrackEntity) -> Unit,
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: (TrackEntity) -> Unit,
    onAddTrack: (TrackEntity) -> Unit,
    onLikeClick: (TrackEntity) -> Unit,
    onBackClick: () -> Unit
) {
    var showAddSongsSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = SpotifyWhite)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (playlist.id >= 0) {
                        IconButton(
                            onClick = onDeletePlaylist,
                            modifier = Modifier.testTag("delete_playlist_btn")
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = Color.Red)
                        }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    val isSmart = playlist.id < 0
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSmart) {
                                    when (playlist.id) {
                                        -1 -> Brush.linearGradient(colors = listOf(Color(0xFFEC008C), Color(0xFFFC6767)))
                                        -2 -> Brush.linearGradient(colors = listOf(Color(0xFFFF8C00), Color(0xFF8B0000)))
                                        else -> Brush.linearGradient(colors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
                                    }
                                } else {
                                    Brush.linearGradient(colors = listOf(SpotifySurface, SpotifySurface))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSmart) {
                            val icon = when (playlist.id) {
                                -1 -> Icons.Filled.Whatshot
                                -2 -> Icons.Filled.History
                                else -> Icons.Filled.AccessTime
                            }
                            Icon(icon, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(48.dp))
                        } else if (tracks.isNotEmpty()) {
                            TrackCoverImage(
                                url = tracks[0].coverUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(56.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = localizePlaylistName(playlist.name, language),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite
                        )
                        if (playlist.description.isNotEmpty()) {
                            Text(
                                text = localizePlaylistDescription(playlist.description, language),
                                fontSize = 14.sp,
                                color = SpotifyGrey,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = if (isSmart) t("auto_curated_mix", language) else "${tracks.size} ${t(if (tracks.size == 1) "song_count" else "songs_count", language)}",
                            fontSize = 13.sp,
                            color = SpotifyGrey,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    if (tracks.isNotEmpty()) {
                        Button(
                            onClick = { onTrackClick(tracks[0]) },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("playlist_play_btn")
                        ) {
                            Icon(
                                imageVector = if (isPlaying && tracks.any { it.id == currentTrack?.id }) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play All",
                                tint = SpotifyBlack,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(t("play_button", language), color = SpotifyBlack, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (playlist.id >= 0) {
                        Button(
                            onClick = { showAddSongsSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifySurface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("add_songs_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(t("add_songs", language), color = SpotifyWhite, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (playlist.id < 0) {
                                t("populate_mix_prompt", language)
                            } else {
                                t("empty_playlist_prompt", language)
                            },
                            color = SpotifyGrey,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                items(tracks) { track ->
                    TrackListItem(
                        track = track,
                        isCurrent = currentTrack?.id == track.id,
                        isPlaying = isPlaying && currentTrack?.id == track.id,
                        onClick = { onTrackClick(track) },
                        onLikeClick = { onLikeClick(track) },
                        trailingContent = if (playlist.id >= 0) {
                            {
                                IconButton(
                                    onClick = { onRemoveTrack(track) },
                                    modifier = Modifier.testTag("remove_track_${track.id}")
                                ) {
                                    Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove", tint = SpotifyGrey)
                                }
                            }
                        } else null
                    )
                }
            }
        }

        // Overlay/Sheet to add songs
        if (showAddSongsSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showAddSongsSheet = false }
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SpotifySurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f)
                        .clickable(enabled = false) { }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(t("add_songs", language), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { showAddSongsSheet = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = SpotifyWhite)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val songsToAdd = allTracks.filter { track -> !tracks.any { it.id == track.id } }

                        if (songsToAdd.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(t("all_songs_added", language), color = SpotifyGrey, textAlign = TextAlign.Center)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                items(songsToAdd) { track ->
                                    TrackListItem(
                                        track = track,
                                        isCurrent = false,
                                        isPlaying = false,
                                        onClick = { onAddTrack(track) },
                                        onLikeClick = { },
                                        trailingContent = {
                                            IconButton(
                                                onClick = { onAddTrack(track) },
                                                modifier = Modifier.testTag("add_track_to_list_${track.id}")
                                            ) {
                                                Icon(Icons.Filled.AddCircleOutline, contentDescription = "Add", tint = SpotifyGreen)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: TrackEntity,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onLikeClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onClick: () -> Unit,
    controlsOpacity: Float = 0.95f
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SpotifySurface.copy(alpha = controlsOpacity)),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("mini_player")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp, start = 8.dp, end = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    TrackCoverImage(
                        url = track.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        color = SpotifyGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        triggerHapticFeedback(context, if (track.isLiked) "snap" else "double_pulse")
                        onLikeClick()
                    },
                    modifier = Modifier.testTag("mini_like_btn")
                ) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (track.isLiked) SpotifyGreen else SpotifyGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onSkipPreviousClick,
                    modifier = Modifier.testTag("mini_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.testTag("mini_play_pause_btn")
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = SpotifyGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = SpotifyWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNextClick,
                    modifier = Modifier.testTag("mini_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = SpotifyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Interactive Progress Slider for the music
            var isDragging by remember { mutableStateOf(false) }
            var dragPosition by remember { mutableStateOf(0f) }
            val currentProgress = if (isDragging) dragPosition else (if (duration > 0) position.toFloat() / duration else 0f)

            Slider(
                value = currentProgress.coerceIn(0f, 1f),
                onValueChange = {
                    val oldPercent = (currentProgress * 100).toInt()
                    val newPercent = (it * 100).toInt()
                    if (newPercent != oldPercent) {
                        triggerHapticFeedback(context, "tick")
                    }
                    isDragging = true
                    dragPosition = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek((dragPosition * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = SpotifyGreen,
                    inactiveTrackColor = SpotifySurfaceVariant,
                    thumbColor = SpotifyGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .testTag("mini_player_slider")
            )
        }
    }
}

@Composable
fun ExpandedPlayerScreen(
    track: TrackEntity,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isBuffering: Boolean,
    isShuffleEnabled: Boolean,
    isRepeatEnabled: Boolean,
    playlists: List<PlaylistEntity>,
    lyricsState: LyricsUiState,
    themeAccent: ThemeAccent,
    visualizerStyle: String,
    onSeek: (Long) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onCollapse: () -> Unit,
    onFetchAlbumArtClick: suspend () -> Boolean = { false },
    sleepTimerRemaining: Long = 0L,
    onSleepTimerClick: () -> Unit = {},
    dominantColor: Color = SpotifySurface,
    secondaryColor: Color = SpotifyBlack,
    headerModifier: Modifier = Modifier,
    controlsOpacity: Float = 0.95f,
    ambientGlowEnabled: Boolean = true,
    blurIntensity: Float = 25f
) {
    var showVisualizer by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val language = LocalAppLanguage.current

    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val coverSize = if (screenHeightDp < 700) 220.dp else if (screenHeightDp < 800) 260.dp else 320.dp

    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val radians = angle * (PI.toFloat() / 180f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("expanded_player")
    ) {
        // Blur background layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurIntensity.dp)
                .drawBehind {
                    val width = size.width
                    val height = size.height

                    // Draw base dark color
                    drawRect(color = SpotifyBlack)

                    if (ambientGlowEnabled) {
                        val blurFactor = (blurIntensity / 25f).coerceIn(0.2f, 2.0f)

                        // Circle 1 (Dominant color) - slow circular orbital path
                        val radius1 = width * 1.3f * blurFactor
                        val center1X = width / 2f + (width * 0.35f * cos(radians))
                        val center1Y = height / 3f + (height * 0.15f * sin(radians))
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(dominantColor.copy(alpha = 0.5f), Color.Transparent),
                                center = Offset(center1X, center1Y),
                                radius = radius1
                            ),
                            radius = radius1,
                            center = Offset(center1X, center1Y)
                        )

                        // Circle 2 (Secondary color) - opposite orbital path
                        val radius2 = width * 1.1f * blurFactor
                        val center2X = width / 2f - (width * 0.3f * cos(radians + PI.toFloat() / 2f))
                        val center2Y = height * 2f / 3f - (height * 0.12f * sin(radians + PI.toFloat() / 2f))
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(secondaryColor.copy(alpha = 0.45f), Color.Transparent),
                                center = Offset(center2X, center2Y),
                                radius = radius2
                            ),
                            radius = radius2,
                            center = Offset(center2X, center2Y)
                        )

                        // Circle 3 (Theme Accent Color) - dynamic sinusoidal shift
                        val radius3 = width * 0.9f * blurFactor
                        val center3X = width / 2f + (width * 0.25f * sin(radians * 1.3f))
                        val center3Y = height / 2f + (height * 0.1f * cos(radians * 1.3f))
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(themeAccent.color.copy(alpha = 0.3f), Color.Transparent),
                                center = Offset(center3X, center3Y),
                                radius = radius3
                            ),
                            radius = radius3,
                            center = Offset(center3X, center3Y)
                        )
                    } else {
                        // Subtle fallback vertical gradient if ambient glow is disabled
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(dominantColor.copy(alpha = 0.15f), SpotifyBlack)
                            )
                        )
                    }
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp, top = 8.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(headerModifier)
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.testTag("player_collapse_btn")
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = SpotifyWhite, modifier = Modifier.size(32.dp))
                    }
                }
                
                Text(
                    text = track.album,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { 
                            showLyrics = !showLyrics
                            if (showLyrics) showVisualizer = false
                        },
                        modifier = Modifier.testTag("player_lyrics_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (showLyrics) Icons.Filled.Lyrics else Icons.Outlined.Lyrics,
                            contentDescription = "Toggle Lyrics",
                            tint = if (showLyrics) themeAccent.color else SpotifyWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = { 
                            showVisualizer = !showVisualizer 
                            if (showVisualizer) showLyrics = false
                        },
                        modifier = Modifier.testTag("player_visualizer_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (showVisualizer) Icons.Filled.GraphicEq else Icons.Outlined.GraphicEq,
                            contentDescription = "Toggle Visualizer",
                            tint = if (showVisualizer) SpotifyGreen else SpotifyWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(
                        onClick = onEqualizerClick,
                        modifier = Modifier.testTag("player_equalizer_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Equalizer",
                            tint = SpotifyWhite,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Giant Cover Art / Fluid Visualizer / Live Lyrics
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverSize)
                    .testTag("player_center_art_area"),
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    LyricsPanel(
                        lyricsState = lyricsState,
                        position = position,
                        onLineClick = onSeek,
                        accentColor = themeAccent.color,
                        modifier = Modifier.fillMaxSize(),
                        controlsOpacity = controlsOpacity * 0.6f
                    )
                } else if (showVisualizer) {
                    com.example.player.FluidVisualizer(
                        track = track,
                        isPlaying = isPlaying,
                        visualizerStyle = visualizerStyle,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .clip(RoundedCornerShape(8.dp))
                            .shadow(8.dp)
                            .clickable { showLyrics = true }
                            .testTag("player_album_art_container")
                    ) {
                        TrackCoverImage(
                            url = track.coverUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Track details (Title / Artist)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = track.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        fontSize = 16.sp,
                        color = SpotifyGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        triggerHapticFeedback(context, if (track.isLiked) "snap" else "double_pulse")
                        onLikeClick()
                    },
                    modifier = Modifier.testTag("player_like_btn")
                ) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (track.isLiked) SpotifyGreen else SpotifyWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Slider
            var isDragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableStateOf(0f) }
            val displayPosition = if (isDragging) {
                (dragValue * duration).toLong()
            } else {
                position
            }

            val currentProgressValue = if (isDragging) dragValue else (if (duration > 0) position.toFloat() / duration else 0f)
            Slider(
                value = currentProgressValue,
                onValueChange = {
                    val oldPercent = (currentProgressValue * 100).toInt()
                    val newPercent = (it * 100).toInt()
                    if (newPercent != oldPercent) {
                        triggerHapticFeedback(context, "tick")
                    }
                    isDragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek((dragValue * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = SpotifyGreen,
                    inactiveTrackColor = SpotifySurfaceVariant,
                    thumbColor = SpotifyGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .testTag("player_slider")
            )

            // Progress times
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(formatDuration(displayPosition), color = SpotifyGrey, fontSize = 12.sp)
                Text(formatDuration(duration), color = SpotifyGrey, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onShuffleClick,
                    modifier = Modifier.testTag("player_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) SpotifyGreen else SpotifyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier.testTag("player_prev_btn")
                ) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = SpotifyWhite, modifier = Modifier.size(40.dp))
                }

                // Play / Pause Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(SpotifyWhite)
                        .clickable(onClick = onPlayPauseClick)
                        .testTag("player_play_pause_btn")
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = SpotifyBlack,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = SpotifyBlack,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.testTag("player_next_btn")
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = SpotifyWhite, modifier = Modifier.size(40.dp))
                }

                IconButton(
                    onClick = onRepeatClick,
                    modifier = Modifier.testTag("player_repeat_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (isRepeatEnabled) SpotifyGreen else SpotifyWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist Name", color = SpotifyGrey) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpotifySurface,
                        unfocusedContainerColor = SpotifySurface,
                        focusedTextColor = SpotifyWhite,
                        unfocusedTextColor = SpotifyWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                )
                TextField(
                    value = desc,
                    onValueChange = { desc = it },
                    placeholder = { Text("Description (Optional)", color = SpotifyGrey) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpotifySurface,
                        unfocusedContainerColor = SpotifySurface,
                        focusedTextColor = SpotifyWhite,
                        unfocusedTextColor = SpotifyWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("playlist_desc_input")
                )
            }
        },
        containerColor = SpotifySurface,
        confirmButton = {
            Button(
                onClick = { onConfirm(name, desc) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                modifier = Modifier.testTag("dialog_create_btn")
            ) {
                Text("Create", color = SpotifyBlack, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_btn")
            ) {
                Text("Cancel", color = SpotifyWhite)
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    track: TrackEntity,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist", color = SpotifyWhite, fontWeight = FontWeight.Bold) },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists found. Create a playlist first!", color = SpotifyGrey)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
                ) {
                    items(playlists) { playlist ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlaylistSelected(playlist.id) }
                                .padding(vertical = 12.dp)
                                .testTag("select_playlist_${playlist.id}")
                        ) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(24.dp))
                            Text(
                                text = playlist.name,
                                color = SpotifyWhite,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = SpotifySurface,
        confirmButton = { },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SpotifyWhite)
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onSelectTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    currentRemainingMs: Long,
    accentColor: Color = SpotifyGreen
) {
    val language = LocalAppLanguage.current
    var customMinutes by remember { mutableStateOf(30f) }
    var maxTimerDuration by remember { mutableStateOf(currentRemainingMs) }
    
    LaunchedEffect(currentRemainingMs) {
        if (currentRemainingMs > maxTimerDuration) {
            maxTimerDuration = currentRemainingMs
        }
    }

    val progress = remember(currentRemainingMs, maxTimerDuration) {
        if (maxTimerDuration > 0) currentRemainingMs.toFloat() / maxTimerDuration else 0f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = SpotifySurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = t("sleep_timer", language),
                    color = SpotifyWhite,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.5).sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                if (currentRemainingMs > 0) {
                    // Active Timer Mode: Large aesthetic circular countdown
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(140.dp)
                            .padding(8.dp)
                    ) {
                        // Background circle track
                        CircularProgressIndicator(
                            progress = 1f,
                            color = SpotifySurfaceVariant,
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Foreground dynamic indicator
                        CircularProgressIndicator(
                            progress = progress,
                            color = accentColor,
                            strokeWidth = 6.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Inner content
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = formatSleepTimer(currentRemainingMs),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyWhite,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            Text(
                                text = t("remaining", language).ifEmpty { "Remaining" },
                                fontSize = 10.sp,
                                color = SpotifyGrey,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Quick Extensions Chips Row
                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = t("extend_timer", language),
                            fontSize = 11.sp,
                            color = SpotifyGrey,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(5, 10, 15).forEach { extMinutes ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(SpotifySurfaceVariant, RoundedCornerShape(10.dp))
                                        .border(1.dp, SpotifySurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            val currentRemainingMin = (currentRemainingMs / 60000).toInt()
                                            onSelectTimer(currentRemainingMin + extMinutes)
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${extMinutes}m",
                                        color = SpotifyWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Non-Active Setup Mode: Elegant preset list and custom duration
                    Text(
                        text = t("sleep_timer_desc", language),
                        color = SpotifyGrey,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    // Presets arranged in a modern grid/row
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val row1 = listOf(5, 15, 30)
                        val row2 = listOf(45, 60)

                        // Render row 1
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row1.forEach { minutes ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(SpotifySurfaceVariant, RoundedCornerShape(12.dp))
                                        .border(1.dp, SpotifySurfaceVariant, RoundedCornerShape(12.dp))
                                        .clickable { onSelectTimer(minutes) }
                                        .padding(vertical = 12.dp)
                                        .testTag("sleep_timer_preset_$minutes"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$minutes Min",
                                        color = SpotifyWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Render row 2
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row2.forEach { minutes ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(SpotifySurfaceVariant, RoundedCornerShape(12.dp))
                                        .border(1.dp, SpotifySurfaceVariant, RoundedCornerShape(12.dp))
                                        .clickable { onSelectTimer(minutes) }
                                        .padding(vertical = 12.dp)
                                        .testTag("sleep_timer_preset_$minutes"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$minutes Min",
                                        color = SpotifyWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))

                    // Custom Slider Duration
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = t("custom_duration", language),
                                color = SpotifyWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${customMinutes.toInt()} Min",
                                color = accentColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Slider(
                            value = customMinutes,
                            onValueChange = { customMinutes = it },
                            valueRange = 1f..120f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = SpotifySurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { onSelectTimer(customMinutes.toInt()) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = t("start_sleep_timer_btn", language),
                                color = SpotifyBlack,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (currentRemainingMs > 0) {
                Button(
                    onClick = onCancelTimer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sleep_timer_cancel_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = t("cancel_sleep_timer", language),
                        color = SpotifyWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sleep_timer_dismiss_btn")
            ) {
                Text(
                    text = t("close_btn", language),
                    color = SpotifyGrey,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun AudioVisualizerBars(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = if (isPlaying) {
        listOf(
            transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar1"
            ),
            transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(350, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar2"
            ),
            transition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar3"
            )
        )
    } else {
        listOf(
            remember { mutableStateOf(0.3f) },
            remember { mutableStateOf(0.2f) },
            remember { mutableStateOf(0.3f) }
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightVal.value)
                    .background(SpotifyGreen, shape = RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
fun DynamicIslandPlayer(
    track: TrackEntity,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onLikeClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val animatedWidth by animateDpAsState(
        targetValue = if (isExpanded) 340.dp else 190.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "width"
    )
    val animatedHeight by animateDpAsState(
        targetValue = if (isExpanded) 120.dp else 40.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "height"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = modifier
            .width(animatedWidth)
            .height(animatedHeight)
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clickable { isExpanded = !isExpanded }
            .animateContentSize()
            .testTag("dynamic_island")
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
                    TrackCoverImage(
                        url = track.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = track.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center
                )

                AudioVisualizerBars(
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .width(15.dp)
                        .height(14.dp)
                )
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
                                onClick()
                            }
                    ) {
                        TrackCoverImage(
                            url = track.coverUrl,
                            contentDescription = "Expand Full Player",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = track.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            fontSize = 11.sp,
                            color = SpotifyGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onLikeClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (track.isLiked) SpotifyGreen else SpotifyGrey,
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
                        text = formatDuration(position),
                        fontSize = 10.sp,
                        color = SpotifyGrey,
                        modifier = Modifier.width(36.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSkipPreviousClick,
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
                            onClick = onPlayPauseClick,
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
                            onClick = onSkipNextClick,
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
                        text = formatDuration(duration),
                        fontSize = 10.sp,
                        color = SpotifyGrey,
                        modifier = Modifier.width(36.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val progress = if (duration > 0) position.toFloat() / duration else 0f
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    color = SpotifyGreen,
                    trackColor = SpotifySurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                )
            }
        }
    }
}

@Composable
fun DynamicIslandPermissionBanner() {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    var hasPermission by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermission = android.provider.Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasPermission) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .clickable {
                    try {
                        val intent = Intent(
                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .testTag("dynamic_island_permission_banner")
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SpotifyGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = SpotifyGreen
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("enable_dynamic_island", language),
                        fontWeight = FontWeight.Bold,
                        color = SpotifyWhite,
                        fontSize = 15.sp
                    )
                    Text(
                        text = t("dynamic_island_desc", language),
                        color = SpotifyGrey,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = SpotifyGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isExpanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = SpotifySurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) iconColor.copy(alpha = 0.3f) else SpotifySurfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyWhite,
                        fontSize = 16.sp
                    )
                    Text(
                        text = subtitle,
                        color = SpotifyGrey,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = SpotifyGrey,
                    modifier = Modifier.size(24.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                ) {
                    HorizontalDivider(
                        color = SpotifySurfaceVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    content()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    language: String,
    onLanguageChange: (String) -> Unit,
    onReplayOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val currentAccent by viewModel.currentThemeAccent.collectAsState()
    val context = LocalContext.current

    val sharedPrefs = remember(context) {
        context.getSharedPreferences("music_player_settings", android.content.Context.MODE_PRIVATE)
    }

    var islandMode by remember {
        mutableStateOf(sharedPrefs.getString("island_mode", "AUTO") ?: "AUTO")
    }
    val islandOpacity by viewModel.controlsOpacity.collectAsState()

    var customMinutes by remember { mutableStateOf(30f) }
    var maxTimerDuration by remember { mutableStateOf(0L) }

    LaunchedEffect(sleepTimerRemaining) {
        if (sleepTimerRemaining > maxTimerDuration) {
            maxTimerDuration = sleepTimerRemaining
        } else if (sleepTimerRemaining == 0L) {
            maxTimerDuration = 0L
        }
    }

    val progress = if (maxTimerDuration > 0L) {
        sleepTimerRemaining.toFloat() / maxTimerDuration
    } else {
        0f
    }

    // New configuration states mapping the reference layout
    var showNotifications by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_notifications", true))
    }
    var crossfadeDuration by remember {
        mutableStateOf(sharedPrefs.getFloat("pref_crossfade_duration", 0f))
    }
    var replayGainEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_replay_gain", false))
    }
    val visualizerStyle by viewModel.visualizerStyle.collectAsState()
    val ambientGlowEnabled by viewModel.ambientGlowEnabled.collectAsState()
    val blurIntensity by viewModel.blurIntensity.collectAsState()
    var downloadWifiOnly by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_wifi_only", false))
    }
    var autoDownloadAlbumArt by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_auto_download_album_art", true))
    }
    var highQualityArt by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_hq_art", true))
    }
    var librarySortOrder by remember {
        mutableStateOf(sharedPrefs.getString("pref_sort_order", "Title") ?: "Title")
    }
    var libraryFolders by remember {
        mutableStateOf(sharedPrefs.getStringSet("pref_library_folders", emptySet())?.toSet() ?: emptySet())
    }
    val settingsFolderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val folderFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, it)
            val folderName = folderFile?.name ?: "Imported Folder"
            val entry = "${it.toString()}|$folderName"
            
            val updatedSet = libraryFolders.toMutableSet().apply { add(entry) }
            libraryFolders = updatedSet
            sharedPrefs.edit().putStringSet("pref_library_folders", updatedSet).apply()
            
            viewModel.importLocalFolder(it)
            android.widget.Toast.makeText(context, t("folder_added_toast", language), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var pauseOnUnplug by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_pause_on_unplug", true))
    }
    var resumeOnConnect by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_resume_on_connect", false))
    }
    val availableDevices by viewModel.availableOutputDevices.collectAsState()
    val selectedDevice by viewModel.selectedOutputDevice.collectAsState()
    var showDeviceMenu by remember { mutableStateOf(false) }

    var lockScreenWidgetEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_lockscreen_widget", true))
    }
    var hapticFeedbackIntensity by remember {
        mutableStateOf(sharedPrefs.getString("pref_haptic_intensity", "Crisp") ?: "Crisp")
    }

    var expandedCategory by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = t("settings", language),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyWhite,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 1. LOOK AND FEEL CATEGORY
        SettingsCategoryCard(
            title = t("look_feel", language),
            subtitle = t("look_feel_sub", language),
            icon = Icons.Default.Palette,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "look_and_feel",
            onClick = {
                expandedCategory = if (expandedCategory == "look_and_feel") null else "look_and_feel"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            Text(
                text = t("theme_accent_title", language),
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = t("theme_accent_desc", language),
                color = SpotifyGrey,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
            ) {
                items(com.example.ui.theme.ThemeAccent.values()) { accent ->
                    val isSelected = currentAccent == accent
                    val animatedScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.04f else 0.96f,
                        label = "accentScale"
                    )
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.7f,
                        label = "accentAlpha"
                    )

                    Card(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                                alpha = animatedAlpha
                            }
                            .clickable {
                                viewModel.setThemeAccent(accent)
                                triggerHapticFeedback(context, "snap")
                            }
                            .testTag("theme_accent_${accent.name.lowercase()}"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) SpotifySurfaceVariant else SpotifyBlack.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accent.color else SpotifySurfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(accent.color.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, accent.color.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(accent.color, CircleShape)
                                )
                            }
                            Text(
                                text = t("accent_${accent.name.lowercase()}", language),
                                color = if (isSelected) SpotifyWhite else SpotifyGrey,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Floating Dynamic Island Overlay Mode
            Text(
                text = t("floating_island_title", language),
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = t("floating_island_desc", language),
                color = SpotifyGrey,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            val modes = listOf(
                Triple("COMPACT", t("compact_pill", language), t("compact_pill_desc", language)),
                Triple("EXPANDED", t("expanded_card", language), t("expanded_card_desc", language)),
                Triple("AUTO", t("auto_expanding", language), t("auto_expanding_desc", language))
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                modes.forEach { (modeKey, modeTitle, modeDesc) ->
                    val isSelected = islandMode == modeKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) SpotifySurfaceVariant else SpotifyBlack.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) currentAccent.color else SpotifySurfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                islandMode = modeKey
                                sharedPrefs.edit().putString("island_mode", modeKey).apply()
                                triggerHapticFeedback(context, "snap")
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                islandMode = modeKey
                                sharedPrefs.edit().putString("island_mode", modeKey).apply()
                                triggerHapticFeedback(context, "snap")
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = currentAccent.color,
                                unselectedColor = SpotifyGrey
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = modeTitle,
                                color = SpotifyWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = modeDesc,
                                color = SpotifyGrey,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // System Permission Box for Overlay
            val hasOverlayPermission = Settings.canDrawOverlays(context)
            if (!hasOverlayPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .clickable {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Permission Required",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = t("sys_overlay_perm_title", language),
                                color = Color(0xFFFF9800),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = t("sys_overlay_perm_desc", language),
                                color = SpotifyGrey,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Language Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("app_language", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("lang_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                var showLangDropdown by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { showLangDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifySurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = language, color = SpotifyWhite, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showLangDropdown,
                        onDismissRequest = { showLangDropdown = false },
                        modifier = Modifier.background(SpotifySurfaceVariant)
                    ) {
                        listOf(
                            "English 🇮🇪",
                            "Español 🇪🇸",
                            "العربية (فلسطين) 🇵🇸",
                            "فارسی 🇮🇷",
                            "Русский 🇷🇺",
                            "中文 🇨🇳",
                            "Bahasa Indonesia 🇮🇩",
                            "Bahasa Melayu 🇲🇾",
                            "বাংলা 🇧🇩",
                            "Português (Brasil) 🇧🇷"
                        ).forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(text = lang, color = SpotifyWhite) },
                                onClick = {
                                    onLanguageChange(lang)
                                    sharedPrefs.edit().putString("pref_language", lang).apply()
                                    showLangDropdown = false
                                    triggerHapticFeedback(context, "tick")
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // System Notification Control Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("sys_drawer_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("sys_drawer_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = showNotifications,
                    onCheckedChange = {
                        showNotifications = it
                        sharedPrefs.edit().putBoolean("pref_notifications", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }
        }

        // 2. AUDIO CATEGORY
        SettingsCategoryCard(
            title = t("audio_playback", language),
            subtitle = t("audio_playback_sub", language),
            icon = Icons.Default.Tune,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "audio",
            onClick = {
                expandedCategory = if (expandedCategory == "audio") null else "audio"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            // Acoustic Equalizer Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("acoustic_eq_title", language),
                        fontWeight = FontWeight.Bold,
                        color = SpotifyWhite,
                        fontSize = 14.sp
                    )
                    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
                    Text(
                        text = if (isEqualizerEnabled) t("eq_active", language) else t("eq_inactive", language),
                        color = if (isEqualizerEnabled) currentAccent.color else SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
                Switch(
                    checked = isEqualizerEnabled,
                    onCheckedChange = {
                        viewModel.setEqualizerEnabled(it)
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            var showSettingsEqualizer by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    showSettingsEqualizer = true
                    triggerHapticFeedback(context, "snap")
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifySurfaceVariant),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = SpotifyWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t("open_eq_studio", language),
                        color = SpotifyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (showSettingsEqualizer) {
                EqualizerDialog(
                    viewModel = viewModel,
                    onDismiss = { showSettingsEqualizer = false }
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Crossfade Duration
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("track_crossfade", language),
                            color = SpotifyWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = t("track_crossfade_desc", language),
                            color = SpotifyGrey,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = if (crossfadeDuration > 0f) "${String.format("%.1f", crossfadeDuration)}s" else t("crossfade_off", language),
                        color = currentAccent.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = crossfadeDuration,
                    onValueChange = {
                        crossfadeDuration = it
                        sharedPrefs.edit().putFloat("pref_crossfade_duration", it).apply()
                    },
                    valueRange = 0f..10f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentAccent.color,
                        activeTrackColor = currentAccent.color,
                        inactiveTrackColor = SpotifySurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Replay Gain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("replay_gain_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("replay_gain_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = replayGainEnabled,
                    onCheckedChange = {
                        replayGainEnabled = it
                        sharedPrefs.edit().putBoolean("pref_replay_gain", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Output Device Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("audio_output_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("audio_output_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Box {
                    Text(
                        text = selectedDevice?.name ?: t("audio_driver_label", language),
                        color = SpotifyWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(SpotifySurfaceVariant, RoundedCornerShape(6.dp))
                            .clickable {
                                viewModel.updateAvailableDevices()
                                showDeviceMenu = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                    DropdownMenu(
                        expanded = showDeviceMenu,
                        onDismissRequest = { showDeviceMenu = false },
                        modifier = Modifier.background(SpotifySurfaceVariant)
                    ) {
                        availableDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name, color = SpotifyWhite, fontSize = 13.sp) },
                                onClick = {
                                    viewModel.selectOutputDevice(device)
                                    showDeviceMenu = false
                                    triggerHapticFeedback(context, "snap")
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. VISUALIZATION CATEGORY
        SettingsCategoryCard(
            title = t("vis_style_title", language),
            subtitle = t("vis_style_desc", language),
            icon = Icons.Default.GraphicEq,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "visualization",
            onClick = {
                expandedCategory = if (expandedCategory == "visualization") null else "visualization"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            Text(
                text = t("acoustic_wave_title", language),
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val styles = listOf("Fluid Particles", "Sine Wave", "Bar Spectrum")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                styles.forEach { style ->
                    val isSel = visualizerStyle == style
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSel) currentAccent.color.copy(alpha = 0.15f) else SpotifyBlack.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSel) currentAccent.color else SpotifySurfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                viewModel.setVisualizerStyle(style)
                                triggerHapticFeedback(context, "tick")
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val styleKey = "style_${style.lowercase().replace(" ", "_")}"
                        Text(
                            text = t(styleKey, language),
                            color = if (isSel) currentAccent.color else SpotifyWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Background Opacity / Transparency
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("controls_opacity_title", language),
                            color = SpotifyWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = t("controls_opacity_desc", language),
                            color = SpotifyGrey,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${(islandOpacity * 100).toInt()}%",
                        color = currentAccent.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = islandOpacity,
                    onValueChange = {
                        viewModel.setControlsOpacity(it)
                    },
                    valueRange = 0.3f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentAccent.color,
                        activeTrackColor = currentAccent.color,
                        inactiveTrackColor = SpotifySurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 4. BACKGROUND CATEGORY
        SettingsCategoryCard(
            title = t("bg_title", language),
            subtitle = t("bg_desc", language),
            icon = Icons.Default.Image,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "background",
            onClick = {
                expandedCategory = if (expandedCategory == "background") null else "background"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            // Ambient Glow Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("ambient_glow_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("ambient_glow_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = ambientGlowEnabled,
                    onCheckedChange = {
                        viewModel.setAmbientGlowEnabled(it)
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Blur Intensity slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = t("blur_intensity_title", language),
                            color = SpotifyWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = t("blur_intensity_desc", language),
                            color = SpotifyGrey,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${blurIntensity.toInt()}dp",
                        color = currentAccent.color,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = blurIntensity,
                    onValueChange = {
                        viewModel.setBlurIntensity(it)
                    },
                    valueRange = 10f..50f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentAccent.color,
                        activeTrackColor = currentAccent.color,
                        inactiveTrackColor = SpotifySurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 5. ALBUM ART CATEGORY
        SettingsCategoryCard(
            title = t("album_art_title", language),
            subtitle = t("album_art_desc", language),
            icon = Icons.Default.Album,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "album_art",
            onClick = {
                expandedCategory = if (expandedCategory == "album_art") null else "album_art"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            // Download Over Wi-Fi Only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("download_wifi_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("download_wifi_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = downloadWifiOnly,
                    onCheckedChange = {
                        downloadWifiOnly = it
                        sharedPrefs.edit().putBoolean("pref_wifi_only", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // High Quality Art
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("high_def_art_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("high_def_art_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = highQualityArt,
                    onCheckedChange = {
                        highQualityArt = it
                        sharedPrefs.edit().putBoolean("pref_hq_art", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Auto-Download Album Art
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("auto_download_art", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("auto_download_art_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = autoDownloadAlbumArt,
                    onCheckedChange = {
                        autoDownloadAlbumArt = it
                        sharedPrefs.edit().putBoolean("pref_auto_download_album_art", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Clear Cache Button
            Button(
                onClick = {
                    triggerHapticFeedback(context, "double_pulse")
                    try {
                        context.imageLoader.memoryCache?.clear()
                        context.imageLoader.diskCache?.clear()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    android.widget.Toast.makeText(context, t("art_cache_cleared_toast", language), android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifySurfaceVariant),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(t("clear_art_cache_btn", language), color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // 6. LIBRARY CATEGORY
        SettingsCategoryCard(
            title = t("lib_folders_title", language),
            subtitle = t("lib_folders_sub", language),
            icon = Icons.AutoMirrored.Filled.List,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "library",
            onClick = {
                expandedCategory = if (expandedCategory == "library") null else "library"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            // Sort Order
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("library_sort_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("library_sort_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                var showSortDropdown by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { showSortDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifySurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val sortLabelKey = when (librarySortOrder) {
                            "Title" -> "sort_track_title"
                            "Artist" -> "sort_artist_name"
                            "Date Added" -> "sort_import_date"
                            else -> "sort_track_title"
                        }
                        Text(text = t(sortLabelKey, language), color = SpotifyWhite, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false },
                        modifier = Modifier.background(SpotifySurfaceVariant)
                    ) {
                        listOf("Title", "Artist", "Date Added").forEach { sort ->
                            val key = when (sort) {
                                "Title" -> "sort_track_title"
                                "Artist" -> "sort_artist_name"
                                "Date Added" -> "sort_import_date"
                                else -> "sort_track_title"
                            }
                            DropdownMenuItem(
                                text = { Text(text = t(key, language), color = SpotifyWhite) },
                                onClick = {
                                    librarySortOrder = sort
                                    sharedPrefs.edit().putString("pref_sort_order", sort).apply()
                                    showSortDropdown = false
                                    triggerHapticFeedback(context, "tick")
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Active Music Folders title
            Text(
                text = t("active_folders_title", language),
                color = SpotifyWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (libraryFolders.isEmpty()) {
                Text(
                    text = t("no_custom_folders", language),
                    color = SpotifyGrey,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    libraryFolders.forEach { folderEntry ->
                        val parts = folderEntry.split("|")
                        val uriStr = parts.getOrNull(0) ?: ""
                        val folderName = parts.getOrNull(1) ?: "Custom Folder"
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SpotifySurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = currentAccent.color,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = folderName,
                                        color = SpotifyWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (uriStr.length > 40) "..." + uriStr.takeLast(37) else uriStr,
                                        color = SpotifyGrey,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            Row {
                                IconButton(
                                    onClick = {
                                        try {
                                            val uri = android.net.Uri.parse(uriStr)
                                            viewModel.importLocalFolder(uri)
                                            android.widget.Toast.makeText(context, t("folder_added_toast", language), android.widget.Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Rescan folder",
                                        tint = SpotifyWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        val updatedSet = libraryFolders.toMutableSet().apply { remove(folderEntry) }
                                        libraryFolders = updatedSet
                                        sharedPrefs.edit().putStringSet("pref_library_folders", updatedSet).apply()
                                        android.widget.Toast.makeText(context, t("folder_removed_toast", language), android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove folder",
                                        tint = SpotifyGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Add Custom Folder Button
            Button(
                onClick = {
                    try {
                        settingsFolderLauncher.launch(null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifySurfaceVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = SpotifyWhite,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(t("add_folder_btn", language), color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Rescan library with actual scan progress
            val isScanning by viewModel.isScanning.collectAsState()
            val scanProgress by viewModel.scanProgress.collectAsState()
            val scannedCount by viewModel.scannedCount.collectAsState()

            var wasScanning by remember { mutableStateOf(false) }
            LaunchedEffect(isScanning) {
                if (wasScanning && !isScanning) {
                    triggerHapticFeedback(context, "double_pulse")
                    android.widget.Toast.makeText(context, t("rescan_completed_toast", language), android.widget.Toast.LENGTH_SHORT).show()
                }
                wasScanning = isScanning
            }

            if (isScanning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpotifyBlack.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, currentAccent.color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("scanning_sys_dirs", language), color = SpotifyGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(t("scanned_files_count", language).replace("%d", scannedCount.toString()), color = currentAccent.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = scanProgress,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = currentAccent.color,
                        trackColor = SpotifySurfaceVariant
                    )
                }
            } else {
                Button(
                    onClick = {
                        triggerHapticFeedback(context, "snap")
                        viewModel.rescanLibraryFolders(libraryFolders)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = currentAccent.color),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(t("rescan_folders_btn", language), color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 7. HEADSET/BLUETOOTH CATEGORY
        SettingsCategoryCard(
            title = t("headset_bt_title", language),
            subtitle = t("headset_bt_sub", language),
            icon = Icons.Default.Headset,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "headset_bluetooth",
            onClick = {
                expandedCategory = if (expandedCategory == "headset_bluetooth") null else "headset_bluetooth"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            // Pause on unplug
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("pause_on_disconnect_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("pause_on_disconnect_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = pauseOnUnplug,
                    onCheckedChange = {
                        pauseOnUnplug = it
                        sharedPrefs.edit().putBoolean("pref_pause_on_unplug", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Resume on connect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("resume_on_connection_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("resume_on_connection_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = resumeOnConnect,
                    onCheckedChange = {
                        resumeOnConnect = it
                        sharedPrefs.edit().putBoolean("pref_resume_on_connect", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }
        }

        // 8. SLEEP TIMER & MISC CATEGORY
        SettingsCategoryCard(
            title = t("sleep_lock_title", language),
            subtitle = t("sleep_lock_sub", language),
            icon = Icons.Default.AccessTime,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "sleep_timer_misc",
            onClick = {
                expandedCategory = if (expandedCategory == "sleep_timer_misc") null else "sleep_timer_misc"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = currentAccent.color,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = t("sleep_timer_countdown", language),
                    fontWeight = FontWeight.Bold,
                    color = SpotifyWhite,
                    fontSize = 14.sp
                )
            }
            Text(
                text = if (sleepTimerRemaining > 0) {
                    t("stops_playback_in", language).replace("%s", formatSleepTimer(sleepTimerRemaining))
                } else {
                    t("stops_playback_duration", language)
                },
                color = if (sleepTimerRemaining > 0) currentAccent.color else SpotifyGrey,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (sleepTimerRemaining > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SpotifyBlack.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, SpotifySurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = t("active_countdown", language),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatSleepTimer(sleepTimerRemaining),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SpotifyWhite,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = currentAccent.color,
                        trackColor = SpotifySurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = t("extend_timer", language),
                        fontSize = 11.sp,
                        color = SpotifyGrey,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(5, 10, 15).forEach { extMinutes ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(SpotifySurfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.extendSleepTimer(extMinutes)
                                        triggerHapticFeedback(context, "tick")
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${extMinutes}m",
                                    color = SpotifyWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.cancelSleepTimer()
                            triggerHapticFeedback(context, "double_pulse")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel_sleep_timer_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(t("cancel_sleep_timer_btn_text", language), color = SpotifyWhite, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = t("custom_duration", language),
                            color = SpotifyWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = t("custom_min_duration", language).replace("%d", customMinutes.toInt().toString()),
                            color = currentAccent.color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = customMinutes,
                        onValueChange = { customMinutes = it },
                        valueRange = 1f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = currentAccent.color,
                            activeTrackColor = currentAccent.color,
                            inactiveTrackColor = SpotifySurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            viewModel.setSleepTimer(customMinutes.toInt())
                            triggerHapticFeedback(context, "snap")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = currentAccent.color),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = t("start_sleep_timer_btn", language),
                            color = SpotifyBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Custom Lock Screen Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("lockscreen_controls_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("lockscreen_controls_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = lockScreenWidgetEnabled,
                    onCheckedChange = {
                        lockScreenWidgetEnabled = it
                        sharedPrefs.edit().putBoolean("pref_lockscreen_widget", it).apply()
                        triggerHapticFeedback(context, "snap")
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SpotifyBlack,
                        checkedTrackColor = currentAccent.color,
                        uncheckedThumbColor = SpotifyGrey,
                        uncheckedTrackColor = SpotifySurfaceVariant
                    )
                )
            }

            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

            // Haptic Feedback Intensity Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("haptic_response_title", language),
                        color = SpotifyWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = t("haptic_response_desc", language),
                        color = SpotifyGrey,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                var showHapticDropdown by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { showHapticDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifySurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        val hapticLabelKey = when (hapticFeedbackIntensity) {
                            "Off" -> "haptic_off"
                            "Soft" -> "haptic_soft"
                            "Crisp" -> "haptic_crisp"
                            "Strong" -> "haptic_strong"
                            else -> "haptic_crisp"
                        }
                        Text(text = t(hapticLabelKey, language), color = SpotifyWhite, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = SpotifyWhite, modifier = Modifier.size(16.dp))
                    }

                    DropdownMenu(
                        expanded = showHapticDropdown,
                        onDismissRequest = { showHapticDropdown = false },
                        modifier = Modifier.background(SpotifySurfaceVariant)
                    ) {
                        listOf("Off", "Soft", "Crisp", "Strong").forEach { lvl ->
                            val key = when (lvl) {
                                "Off" -> "haptic_off"
                                "Soft" -> "haptic_soft"
                                "Crisp" -> "haptic_crisp"
                                "Strong" -> "haptic_strong"
                                else -> "haptic_crisp"
                            }
                            DropdownMenuItem(
                                text = { Text(text = t(key, language), color = SpotifyWhite) },
                                onClick = {
                                    hapticFeedbackIntensity = lvl
                                    sharedPrefs.edit().putString("pref_haptic_intensity", lvl).apply()
                                    showHapticDropdown = false
                                    triggerHapticFeedback(context, if (lvl == "Strong") "double_pulse" else "snap")
                                }
                            )
                        }
                    }
                }
            }
        }

        // 9. HELP & APP TOUR CATEGORY
        SettingsCategoryCard(
            title = "Help & App Tour",
            subtitle = "Replay the interactive onboarding tour or view instructions",
            icon = Icons.Default.Info,
            iconColor = currentAccent.color,
            isExpanded = expandedCategory == "help_app_tour",
            onClick = {
                expandedCategory = if (expandedCategory == "help_app_tour") null else "help_app_tour"
                triggerHapticFeedback(context, "snap")
            }
        ) {
            Text(
                text = "Interactive Onboarding Tour",
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = "Review the gorgeous, step-by-step feature walkthrough explaining Lossless Audio, Advanced Music Insights, and Dynamic Floating Overlays.",
                color = SpotifyGrey,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = {
                    triggerHapticFeedback(context, "double_pulse")
                    onReplayOnboarding()
                },
                colors = ButtonDefaults.buttonColors(containerColor = currentAccent.color),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Launch App Guide",
                    color = SpotifyBlack,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun LiveEqualizerPulse(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "EQ")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EQ1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EQ2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EQ3"
    )

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(height1).background(SpotifyGreen, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(height2).background(SpotifyGreen, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(height3).background(SpotifyGreen, RoundedCornerShape(1.dp)))
    }
}

@Composable
fun AnimatedSoundwaveIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "SoundwaveIllustration")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    val green = SpotifyGreen

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212), RoundedCornerShape(24.dp))
            .border(1.dp, SpotifyGrey.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            val width = size.width
            val height = size.height
            val barCount = 12
            val spacing = 8.dp.toPx()
            val totalSpacing = spacing * (barCount - 1)
            val barWidth = (width - totalSpacing) / barCount
            
            for (i in 0 until barCount) {
                val angle = phase + (i * 0.5f)
                val scale = 0.3f + 0.6f * ((sin(angle) + 1f) / 2f)
                val barHeight = height * scale
                val x = i * (barWidth + spacing)
                val y = (height - barHeight) / 2f
                
                drawRoundRect(
                    color = if (i % 2 == 0) green else green.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
            }
        }
    }
}

@Composable
fun AnimatedInsightsIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "InsightsIllustration")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val progressAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progressAngle"
    )
    val green = SpotifyGreen

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212), RoundedCornerShape(24.dp))
            .border(1.dp, SpotifyGrey.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            val width = size.width
            val height = size.height
            val center = androidx.compose.ui.geometry.Offset(width / 2f, height * 0.45f)
            val radius = size.minDimension * 0.28f * pulseScale

            drawCircle(
                color = SpotifyGrey.copy(alpha = 0.1f),
                radius = radius,
                center = center,
                style = Stroke(width = 12.dp.toPx())
            )

            drawArc(
                color = green,
                startAngle = -90f,
                sweepAngle = progressAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            drawCircle(
                color = green.copy(alpha = 0.15f),
                radius = radius * 0.7f,
                center = center
            )

            val barYStart = height * 0.82f
            val barHeight = 8.dp.toPx()
            val totalBars = 3
            val barSpacing = 12.dp.toPx()

            for (i in 0 until totalBars) {
                val y = barYStart + i * (barHeight + barSpacing)
                val percentage = when(i) {
                    0 -> 0.85f
                    1 -> 0.65f
                    else -> 0.4f
                }
                
                drawRoundRect(
                    color = SpotifyGrey.copy(alpha = 0.15f),
                    topLeft = androidx.compose.ui.geometry.Offset(width * 0.1f, y),
                    size = androidx.compose.ui.geometry.Size(width * 0.8f, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                )

                drawRoundRect(
                    color = if (i == 0) green else SpotifyGrey.copy(alpha = 0.6f),
                    topLeft = androidx.compose.ui.geometry.Offset(width * 0.1f, y),
                    size = androidx.compose.ui.geometry.Size(width * 0.8f * percentage * (pulseScale + 0.05f).coerceAtMost(1f), barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f)
                )
            }
        }
    }
}

@Composable
fun AnimatedDynamicIslandIllustration(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "DynamicIslandIllustration")
    val islandWidthScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "islandWidthScale"
    )
    val phaseY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseY"
    )
    val green = SpotifyGreen

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212), RoundedCornerShape(24.dp))
            .border(1.dp, SpotifyGrey.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val width = size.width
            val height = size.height

            drawRoundRect(
                color = SpotifyGrey.copy(alpha = 0.1f),
                topLeft = androidx.compose.ui.geometry.Offset(width * 0.05f, height * 0.05f),
                size = androidx.compose.ui.geometry.Size(width * 0.9f, height * 0.9f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            val islandW = width * 0.6f * islandWidthScale
            val islandH = 42.dp.toPx()
            val islandX = (width - islandW) / 2f
            val islandY = height * 0.15f

            drawRoundRect(
                color = Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(islandX, islandY),
                size = androidx.compose.ui.geometry.Size(islandW, islandH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(islandH / 2f)
            )

            drawRoundRect(
                color = green.copy(alpha = 0.3f),
                topLeft = androidx.compose.ui.geometry.Offset(islandX, islandY),
                size = androidx.compose.ui.geometry.Size(islandW, islandH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(islandH / 2f),
                style = Stroke(width = 1.5.dp.toPx())
            )

            val artSize = 24.dp.toPx()
            val artX = islandX + 12.dp.toPx()
            val artY = islandY + (islandH - artSize) / 2f
            drawRoundRect(
                color = green,
                topLeft = androidx.compose.ui.geometry.Offset(artX, artY),
                size = androidx.compose.ui.geometry.Size(artSize, artSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )

            drawCircle(
                color = Color.Black,
                radius = 3.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(artX + artSize * 0.4f, artY + artSize * 0.6f)
            )

            val waveW = 32.dp.toPx()
            val waveX = islandX + islandW - waveW - 14.dp.toPx()
            val waveYCenter = islandY + islandH / 2f
            val linesCount = 5
            val lineSpacing = 3.dp.toPx()
            val lineWidth = (waveW - (lineSpacing * (linesCount - 1))) / linesCount

            for (i in 0 until linesCount) {
                val lineH = 6.dp.toPx() + 14.dp.toPx() * ((sin(phaseY + i * 0.8f) + 1f) / 2f)
                val lx = waveX + i * (lineWidth + lineSpacing)
                val ly = waveYCenter - lineH / 2f
                
                drawRoundRect(
                    color = green,
                    topLeft = androidx.compose.ui.geometry.Offset(lx, ly),
                    size = androidx.compose.ui.geometry.Size(lineWidth, lineH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(lineWidth / 2f)
                )
            }

            val path = androidx.compose.ui.graphics.Path()
            val startY = height * 0.7f
            path.moveTo(width * 0.1f, startY)
            
            for (xPx in (width * 0.1f).toInt()..(width * 0.9f).toInt()) {
                val relativeX = (xPx - width * 0.1f) / (width * 0.8f)
                val sineVal = sin(phaseY + relativeX * 2 * Math.PI) * 16.dp.toPx()
                path.lineTo(xPx.toFloat(), (startY + sineVal).toFloat())
            }

            drawPath(
                path = path,
                color = green.copy(alpha = 0.15f),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    language: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(0) }
    val pageCount = 3
    val green = SpotifyGreen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090909))
            .testTag("onboarding_container")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(green.copy(alpha = 0.08f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(width * 0.8f, height * 0.2f),
                    radius = size.minDimension * 0.8f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(green.copy(alpha = 0.05f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(width * 0.2f, height * 0.7f),
                    radius = size.minDimension * 0.8f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage < pageCount - 1) {
                    Text(
                        text = t("onboarding_skip", language),
                        color = SpotifyGrey,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                triggerHapticFeedback(context, "double_pulse")
                                onDismiss()
                            }
                            .padding(8.dp)
                            .testTag("onboarding_skip_btn")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(400))).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(400))
                            )
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn(animationSpec = tween(400))).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut(animationSpec = tween(400))
                            )
                        }
                    },
                    label = "OnboardingPageTransition"
                ) { page ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(260.dp)
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (page) {
                                0 -> AnimatedSoundwaveIllustration(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp))
                                1 -> AnimatedInsightsIllustration(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp))
                                2 -> AnimatedDynamicIslandIllustration(modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp))
                            }
                        }

                        val titleKey = when (page) {
                            0 -> "onboarding_welcome_title"
                            1 -> "onboarding_insights_title"
                            else -> "onboarding_island_title"
                        }
                        Text(
                            text = t(titleKey, language),
                            color = SpotifyWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                                .testTag("onboarding_title_page_$page")
                        )

                        val descKey = when (page) {
                            0 -> "onboarding_welcome_desc"
                            1 -> "onboarding_insights_desc"
                            else -> "onboarding_island_desc"
                        }
                        Text(
                            text = t(descKey, language),
                            color = SpotifyGrey,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pageCount) { index ->
                        val isSelected = currentPage == index
                        val widthAnim by animateDpAsState(
                            targetValue = if (isSelected) 20.dp else 6.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                            label = "dotWidth"
                        )
                        val colorAnim by animateColorAsState(
                            targetValue = if (isSelected) SpotifyGreen else SpotifyGrey.copy(alpha = 0.4f),
                            label = "dotColor"
                        )

                        Box(
                            modifier = Modifier
                                .size(width = widthAnim, height = 6.dp)
                                .background(colorAnim, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    triggerHapticFeedback(context, "snap")
                                    currentPage = index
                                }
                                .testTag("onboarding_dot_$index")
                        )
                    }
                }

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1.0f,
                    label = "btnScale"
                )

                Button(
                    onClick = {
                        triggerHapticFeedback(context, "double_pulse")
                        if (currentPage < pageCount - 1) {
                            currentPage++
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboarding_action_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp
                    ),
                    interactionSource = interactionSource
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (currentPage < pageCount - 1) t("onboarding_continue", language) else t("onboarding_get_started", language),
                            color = SpotifyBlack,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (currentPage < pageCount - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                            contentDescription = null,
                            tint = SpotifyBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyActivityChart(
    dailyStats: List<Pair<String, Long>>,
    language: String,
    modifier: Modifier = Modifier
) {
    val maxSeconds = remember(dailyStats) { dailyStats.maxOfOrNull { it.second } ?: 1L }
    val maxVal = if (maxSeconds > 0) maxSeconds.toFloat() else 1f
    
    val accentGreen = SpotifyGreen
    val normalGreen = Color(0xFF2E7D32)
    val normalGreenDark = Color(0xFF1B5E20)
    val accentGrey = SpotifyGrey
    
    val calendar = java.util.Calendar.getInstance()
    val todayName = when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "Mon"
        java.util.Calendar.TUESDAY -> "Tue"
        java.util.Calendar.WEDNESDAY -> "Wed"
        java.util.Calendar.THURSDAY -> "Thu"
        java.util.Calendar.FRIDAY -> "Fri"
        java.util.Calendar.SATURDAY -> "Sat"
        java.util.Calendar.SUNDAY -> "Sun"
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SpotifySurfaceVariant, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Weekly Activity",
                color = SpotifyWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Daily Goal: 30 min",
                color = SpotifyGrey,
                fontSize = 11.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val barSpacing = 12.dp.toPx()
                val numBars = dailyStats.size
                val totalSpacing = barSpacing * (numBars - 1)
                val barWidth = (width - totalSpacing) / numBars
                
                // Draw dynamic dotted goal line (30 mins = 1800s)
                val goalSeconds = 1800f
                if (maxVal > 0) {
                    val goalY = height - (goalSeconds / maxVal).coerceIn(0f, 1f) * (height - 24.dp.toPx()) - 12.dp.toPx()
                    if (goalY in 0f..height) {
                        drawLine(
                            color = accentGrey.copy(alpha = 0.3f),
                            start = androidx.compose.ui.geometry.Offset(0f, goalY),
                            end = androidx.compose.ui.geometry.Offset(width, goalY),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }
                }
                
                dailyStats.forEachIndexed { index, (day, seconds) ->
                    val x = index * (barWidth + barSpacing)
                    val labelSpace = 20.dp.toPx()
                    val chartHeight = height - labelSpace
                    val barHeight = if (maxVal > 0) {
                        (seconds.toFloat() / maxVal) * (chartHeight - 12.dp.toPx())
                    } else {
                        0f
                    }.coerceAtLeast(6.dp.toPx())
                    
                    val y = chartHeight - barHeight
                    
                    val isToday = day == todayName
                    val brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = if (isToday) {
                            listOf(accentGreen, accentGreen.copy(alpha = 0.4f))
                        } else {
                            listOf(normalGreen, normalGreenDark.copy(alpha = 0.2f))
                        }
                    )
                    
                    drawRoundRect(
                        brush = brush,
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    
                    if (isToday) {
                        drawCircle(
                            color = SpotifyWhite,
                            radius = 3.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(x + barWidth / 2, y + 6.dp.toPx())
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dailyStats.forEach { (day, seconds) ->
                    val isToday = day == todayName
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.take(1),
                                color = if (isToday) SpotifyGreen else SpotifyGrey,
                                fontSize = 11.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "${seconds / 60}m",
                                color = if (isToday) SpotifyWhite else SpotifyGrey.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ListeningPersonaCard(topGenre: String, language: String) {
    val (title, emoji, desc, color) = when (topGenre) {
        "Synthwave" -> Quadruple(
            "The Cyberpunk Voyager",
            "⚡",
            "You thrive in neon-soaked soundscapes, late-night driving beats, and retro-futuristic basslines. Your mind runs on 80s nostalgia and digital dreams.",
            Color(0xFFFF007F)
        )
        "Vaporwave" -> Quadruple(
            "The Retro Dreamer",
            "🌴",
            "You are a master of nostalgic aesthetics, slowed beats, and shopping mall elevators. Your listening habits exist in a beautiful, lo-fi purgatory of vintage consumerism.",
            Color(0xFF9C27B0)
        )
        "Lofi" -> Quadruple(
            "The Chill Scholar",
            "📚",
            "You seek peace in ambient crackles, rainfall, and soft jazzy chords. Whether studying or sleeping, you find deep focus in gentle repetitions.",
            Color(0xFFFF9800)
        )
        "Techno" -> Quadruple(
            "The Rave Architect",
            "👽",
            "You love relentless four-on-the-floor rhythms, industrial soundscapes, and hypnotic synth modulation. Your pulse synchronizes perfectly with sub-bass sweeps.",
            Color(0xFF2196F3)
        )
        "Acoustic" -> Quadruple(
            "The Soul Searcher",
            "🌲",
            "You appreciate the raw purity of natural strings, delicate vocals, and unplugged melodies. Your musical heart is warm, authentic, and close to nature.",
            Color(0xFF8D6E63)
        )
        else -> Quadruple(
            "The Infinite Explorer",
            "🎒",
            "You defy simple genre boundaries! Your taste is fluid, curious, and constantly searching for new acoustic horizons and undiscovered rhythms.",
            SpotifyGreen
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(containerColor = SpotifySurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = "YOUR LISTENING PERSONA",
                    color = color,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = title,
                    color = SpotifyWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    color = SpotifyGrey,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun ListeningInsightsDashboard(
    tracks: List<TrackEntity>,
    viewModel: MusicViewModel,
    onTrackClick: (TrackEntity) -> Unit,
    onLikeClick: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    
    var developerTaps by remember { mutableStateOf(0) }
    var showTuning by remember { mutableStateOf(false) }
    
    val totalSeconds = remember { mutableStateOf(com.example.data.ListeningStatsManager.getTotalListeningTimeSeconds(context)) }
    val topGenres = remember { mutableStateOf(com.example.data.ListeningStatsManager.getTopGenres(context)) }
    val topArtists = remember { mutableStateOf(com.example.data.ListeningStatsManager.getTopArtists(context)) }
    val dailyStats = remember { mutableStateOf(com.example.data.ListeningStatsManager.getDailyListeningTime(context)) }

    // Dynamic updates: listen directly to SharedPreferences writes (e.g. from AudioPlayerManager background loop)
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("listening_stats_prefs", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            totalSeconds.value = com.example.data.ListeningStatsManager.getTotalListeningTimeSeconds(context)
            topGenres.value = com.example.data.ListeningStatsManager.getTopGenres(context)
            topArtists.value = com.example.data.ListeningStatsManager.getTopArtists(context)
            dailyStats.value = com.example.data.ListeningStatsManager.getDailyListeningTime(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        // Hero Stats Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .clickable {
                    developerTaps++
                    if (developerTaps >= 5) {
                        showTuning = !showTuning
                        developerTaps = 0
                        triggerHapticFeedback(context, "double_pulse")
                        android.widget.Toast.makeText(
                            context,
                            if (showTuning) "Developer Mode Enabled!" else "Developer Mode Disabled!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            colors = CardDefaults.cardColors(containerColor = SpotifySurfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tracking Status Bar
                if (isPlaying) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(SpotifyGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        LiveEqualizerPulse()
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE TRACKING ACTIVE",
                            color = SpotifyGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(SpotifyGrey.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(SpotifyGrey, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TRACKING IDLE",
                            color = SpotifyGrey,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = t("total_listening_time", language),
                    color = SpotifyGrey,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatListeningTime(totalSeconds.value, language),
                    color = SpotifyGreen,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = t("insights_hero_desc", language),
                    color = SpotifyWhite.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        // Weekly Activity graphical chart
        Text(
            text = "Listening Activity",
            color = SpotifyWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        WeeklyActivityChart(
            dailyStats = dailyStats.value,
            language = language,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Listening Persona profile
        val topGenre = topGenres.value.firstOrNull()?.first ?: ""
        ListeningPersonaCard(topGenre = topGenre, language = language)

        // Top Genres Section
        Text(
            text = t("top_genres", language),
            color = SpotifyWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (topGenres.value.isEmpty()) {
                    Text(t("no_genre_stats", language), color = SpotifyGrey, fontSize = 13.sp)
                } else {
                    val maxSeconds = topGenres.value.firstOrNull()?.second ?: 1L
                    val colors = listOf(SpotifyGreen, Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF9800), Color(0xFFE91E63))
                    
                    topGenres.value.forEachIndexed { index, (genre, seconds) ->
                        val ratio = if (maxSeconds > 0) seconds.toFloat() / maxSeconds.toFloat() else 0f
                        val barColor = colors[index % colors.size]
                        
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = genre, color = SpotifyWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${seconds / 60} ${t("min_unit", language)}",
                                    color = SpotifyGrey,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(SpotifySurfaceVariant, RoundedCornerShape(4.dp))
                             ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio.coerceAtLeast(0.05f))
                                        .fillMaxHeight()
                                        .background(barColor, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Artists Section
        Text(
            text = t("favorite_artists", language),
            color = SpotifyWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (topArtists.value.isEmpty()) {
                    Text(t("no_artist_stats", language), color = SpotifyGrey, fontSize = 13.sp)
                } else {
                    topArtists.value.forEach { (artist, seconds) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(SpotifySurfaceVariant, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = artist.firstOrNull()?.toString() ?: "A"
                                Text(
                                    text = initial,
                                    color = SpotifyGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist,
                                    color = SpotifyWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = t("listened_time", language).replace("%d", (seconds / 60).toString()),
                                    color = SpotifyGrey,
                                    fontSize = 12.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = SpotifyGreen.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Top Tracks section based on play counts
        val sortedTracks = remember(tracks) {
            tracks.sortedByDescending { com.example.data.ListeningStatsManager.getTrackPlayCount(context, it.id) }.take(5)
        }
        
        if (sortedTracks.isNotEmpty()) {
            Text(
                text = t("most_played_tracks", language),
                color = SpotifyWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier.padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedTracks.forEach { track ->
                    TrackListItem(
                        track = track,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { onTrackClick(track) },
                        onLikeClick = { onLikeClick(track) }
                    )
                }
            }
        }

        // Simulation Tuning Actions Section (Hidden behind developer tap shortcut)
        if (showTuning) {
            HorizontalDivider(color = SpotifySurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
            
            Text(
                text = "Tuning & Simulation",
                color = SpotifyWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Use these tools to inject mock statistics and test the responsiveness of the analytics system.",
                color = SpotifyGrey,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        triggerHapticFeedback(context, "snap")
                        com.example.data.ListeningStatsManager.simulateListeningSession(context, tracks, 600L) // Add 10 mins
                        android.widget.Toast.makeText(context, "Simulated 10 minutes of listening across tracks!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(text = "Simulate +10m", color = SpotifyBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                }

                Button(
                    onClick = {
                        triggerHapticFeedback(context, "snap")
                        com.example.data.ListeningStatsManager.loadDemoStats(context)
                        
                        // Manually force updates
                        totalSeconds.value = com.example.data.ListeningStatsManager.getTotalListeningTimeSeconds(context)
                        topGenres.value = com.example.data.ListeningStatsManager.getTopGenres(context)
                        topArtists.value = com.example.data.ListeningStatsManager.getTopArtists(context)
                        dailyStats.value = com.example.data.ListeningStatsManager.getDailyListeningTime(context)
                        
                        android.widget.Toast.makeText(context, "Rich demo statistics loaded!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifySurfaceVariant),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(text = "Load Demo", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                }

                Button(
                    onClick = {
                        triggerHapticFeedback(context, "double_pulse")
                        // Clear stats preferences
                        val prefs = context.getSharedPreferences("listening_stats_prefs", android.content.Context.MODE_PRIVATE)
                        prefs.edit().clear().apply()
                        // Reinitialize to defaults
                        com.example.data.ListeningStatsManager.initializeIfNeeded(context)
                        
                        // Manually force updates in case listener is slow
                        totalSeconds.value = com.example.data.ListeningStatsManager.getTotalListeningTimeSeconds(context)
                        topGenres.value = com.example.data.ListeningStatsManager.getTopGenres(context)
                        topArtists.value = com.example.data.ListeningStatsManager.getTopArtists(context)
                        dailyStats.value = com.example.data.ListeningStatsManager.getDailyListeningTime(context)
                        
                        android.widget.Toast.makeText(context, "Statistics reset to zero!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.8f)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(text = "Reset Stats", color = SpotifyWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

fun formatListeningTime(seconds: Long, language: String): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours} ${t("hr_unit", language)} ${minutes} ${t("min_unit", language)}"
        else -> "${minutes} ${t("min_unit", language)}"
    }
}

@Composable
fun VerticalGainSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = SpotifyGreen
) {
    val rangeMin = valueRange.start
    val rangeMax = valueRange.endInclusive
    val rangeSize = rangeMax - rangeMin

    // Keep track of high-precision float state to prevent integer truncation stickiness
    var localValue by remember(value) { mutableStateOf(value) }

    BoxWithConstraints(
        modifier = modifier
            .width(44.dp)
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        val totalHeightPx = constraints.maxHeight.toFloat()
        
        var isDragging by remember { mutableStateOf(false) }
        
        val dragModifier = if (enabled) {
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            isDragging = true
                            val fraction = ((totalHeightPx - offset.y) / totalHeightPx).coerceIn(0f, 1f)
                            val newValue = rangeMin + fraction * rangeSize
                            localValue = newValue
                            onValueChange(newValue)
                            try {
                                awaitRelease()
                            } finally {
                                isDragging = false
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = ((totalHeightPx - change.position.y) / totalHeightPx).coerceIn(0f, 1f)
                            val newValue = rangeMin + fraction * rangeSize
                            localValue = newValue
                            onValueChange(newValue)
                        }
                    )
                }
        } else {
            Modifier
        }
        
        val progressFraction = ((localValue - rangeMin) / rangeSize).coerceIn(0f, 1f)

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(dragModifier)
        ) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            
            // Draw background track
            val trackWidth = 6.dp.toPx()
            val trackColor = SpotifySurfaceVariant
            
            drawLine(
                color = trackColor,
                start = Offset(centerX, 0f),
                end = Offset(centerX, height),
                strokeWidth = trackWidth,
                cap = StrokeCap.Round
            )
            
            // Draw active track from center (0 dB is at progressFraction = 0.5)
            if (enabled) {
                val centerFraction = 0.5f
                val startY = (1f - centerFraction) * height
                val endY = (1f - progressFraction) * height
                
                drawLine(
                    color = accentColor,
                    start = Offset(centerX, startY),
                    end = Offset(centerX, endY),
                    strokeWidth = trackWidth,
                    cap = StrokeCap.Round
                )
                
                // Draw center notch (0 dB reference line)
                drawLine(
                    color = SpotifyGrey.copy(alpha = 0.5f),
                    start = Offset(centerX - 10.dp.toPx(), height / 2f),
                    end = Offset(centerX + 10.dp.toPx(), height / 2f),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw thumb
            val thumbY = (1f - progressFraction) * height
            val thumbRadius = (if (isDragging) 10.dp else 8.dp).toPx()
            
            if (enabled && isDragging) {
                drawCircle(
                    color = accentColor.copy(alpha = 0.25f),
                    radius = thumbRadius + 6.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
            }
            
            drawCircle(
                color = if (enabled) accentColor else SpotifyGrey,
                radius = thumbRadius,
                center = Offset(centerX, thumbY)
            )
            
            drawCircle(
                color = SpotifyBlack,
                radius = 3.dp.toPx(),
                center = Offset(centerX, thumbY)
            )
        }
    }
}

@Composable
fun TactileKnob(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = SpotifyGreen
) {
    val rangeMin = valueRange.start
    val rangeMax = valueRange.endInclusive
    val rangeSize = rangeMax - rangeMin

    // Keep track of high-precision float state to prevent integer truncation stickiness
    var localValue by remember(value) { mutableStateOf(value) }
    val progressFraction = ((localValue - rangeMin) / rangeSize).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .pointerInput(Unit) {
                    if (enabled) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaFraction = -dragAmount.y / 300f
                                val newValue = (localValue + deltaFraction * rangeSize).coerceIn(rangeMin, rangeMax)
                                localValue = newValue
                                onValueChange(newValue)
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val center = Offset(width / 2f, height / 2f)
                val outerRadius = (width / 2f) - 6.dp.toPx()
                val innerRadius = outerRadius - 8.dp.toPx()

                val startAngle = 135f
                val sweepAngleMax = 270f
                
                // Draw background arc
                drawArc(
                    color = SpotifySurfaceVariant,
                    startAngle = startAngle,
                    sweepAngle = sweepAngleMax,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = size.copy(width = outerRadius * 2, height = outerRadius * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw active value arc
                if (enabled) {
                    drawArc(
                        color = accentColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngleMax * progressFraction,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = size.copy(width = outerRadius * 2, height = outerRadius * 2),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Draw solid knob body
                drawCircle(
                    color = SpotifySurface,
                    radius = innerRadius,
                    center = center
                )

                // Bevel border
                drawCircle(
                    color = if (enabled && isDragging) accentColor.copy(alpha = 0.4f) else SpotifyGrey.copy(alpha = 0.2f),
                    radius = innerRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Indicator line
                val angleRad = (startAngle + sweepAngleMax * progressFraction) * (PI / 180f)
                val notchLength = innerRadius * 0.7f
                val startNotch = Offset(
                    x = center.x + (innerRadius * 0.2f * cos(angleRad)).toFloat(),
                    y = center.y + (innerRadius * 0.2f * sin(angleRad)).toFloat()
                )
                val endNotch = Offset(
                    x = center.x + (notchLength * cos(angleRad)).toFloat(),
                    y = center.y + (notchLength * sin(angleRad)).toFloat()
                )

                drawLine(
                    color = if (enabled) accentColor else SpotifyGrey,
                    start = startNotch,
                    end = endNotch,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            
            Text(
                text = "${(progressFraction * 100).toInt()}%",
                color = if (enabled && progressFraction > 0f) accentColor else SpotifyGrey,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            color = SpotifyGrey,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EqualizerDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val language = LocalAppLanguage.current
    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
    val currentPresetIndex by viewModel.currentPresetIndex.collectAsState()
    val bandGains by viewModel.bandGains.collectAsState()
    val bassBoostStrength by viewModel.bassBoostStrength.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()
    val presetNames by viewModel.presetNames.collectAsState()
    val bandCenterFreqs by viewModel.bandCenterFreqs.collectAsState()
    val themeAccent by viewModel.currentThemeAccent.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Equalizer Icon",
                        tint = themeAccent.color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = t("sound_control", language),
                        color = SpotifyWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEqualizerEnabled) "ON" else "OFF",
                        color = if (isEqualizerEnabled) themeAccent.color else SpotifyGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(
                        checked = isEqualizerEnabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyBlack,
                            checkedTrackColor = themeAccent.color,
                            uncheckedThumbColor = SpotifyGrey,
                            uncheckedTrackColor = SpotifySurfaceVariant
                        ),
                        modifier = Modifier.graphicsLayer {
                            scaleX = 0.85f
                            scaleY = 0.85f
                        }
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column {
                    Text(
                        text = t("presets_title", language),
                        color = SpotifyGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isSelected = currentPresetIndex == -1
                            Card(
                                onClick = { },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) themeAccent.color else SpotifySurfaceVariant
                                ),
                                modifier = Modifier.testTag("preset_custom")
                            ) {
                                Text(
                                    text = t("custom_preset", language),
                                    color = if (isSelected) SpotifyBlack else SpotifyWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        items(presetNames.size) { index ->
                            val name = presetNames[index]
                            val isSelected = currentPresetIndex == index
                            Card(
                                onClick = {
                                    if (isEqualizerEnabled) {
                                        viewModel.setPreset(index)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) themeAccent.color else SpotifySurfaceVariant
                                ),
                                modifier = Modifier
                                    .alpha(if (isEqualizerEnabled) 1f else 0.5f)
                                    .testTag("preset_$index")
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) SpotifyBlack else SpotifyWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = SpotifySurfaceVariant, thickness = 1.dp)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = t("frequency_response", language),
                        color = SpotifyGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isEqualizerEnabled) 1f else 0.5f)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bandCenterFreqs.forEachIndexed { index, freq ->
                            val gain = bandGains.getOrElse(index) { 0 }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (gain > 0) "+$gain" else "$gain",
                                    color = if (gain != 0 && isEqualizerEnabled) themeAccent.color else SpotifyGrey,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                
                                VerticalGainSlider(
                                    value = gain.toFloat(),
                                    onValueChange = {
                                        if (isEqualizerEnabled) {
                                            viewModel.setBandLevel(index, it.toInt())
                                        }
                                    },
                                    valueRange = -15f..15f,
                                    label = "",
                                    enabled = isEqualizerEnabled,
                                    accentColor = themeAccent.color,
                                    modifier = Modifier.testTag("eq_band_$index")
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = if (freq >= 1000) "${freq / 1000}kHz" else "${freq}Hz",
                                    color = SpotifyWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = SpotifySurfaceVariant, thickness = 1.dp)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = t("effects_boosters", language),
                        color = SpotifyGrey,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isEqualizerEnabled) 1f else 0.5f)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TactileKnob(
                            value = bassBoostStrength.toFloat(),
                            onValueChange = {
                                if (isEqualizerEnabled) {
                                    viewModel.setBassBoostStrength(it.toInt())
                                }
                            },
                            valueRange = 0f..1000f,
                            label = t("bass_boost", language),
                            enabled = isEqualizerEnabled,
                            accentColor = themeAccent.color,
                            modifier = Modifier.testTag("bass_boost_slider")
                        )

                        TactileKnob(
                            value = virtualizerStrength.toFloat(),
                            onValueChange = {
                                if (isEqualizerEnabled) {
                                    viewModel.setVirtualizerStrength(it.toInt())
                                }
                            },
                            valueRange = 0f..1000f,
                            label = t("spatial_surround", language),
                            enabled = isEqualizerEnabled,
                            accentColor = themeAccent.color,
                            modifier = Modifier.testTag("virtualizer_slider")
                        )
                    }
                }
                
                if (!isEqualizerEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeAccent.color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(1.dp, themeAccent.color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = t("eq_toggle_tip", language),
                            color = themeAccent.color,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        containerColor = SpotifySurface,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = themeAccent.color),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(t("close_btn", language), color = SpotifyBlack, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun LyricsPanel(
    lyricsState: LyricsUiState,
    position: Long,
    onLineClick: (Long) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    controlsOpacity: Float = 0.55f
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SpotifyBlack.copy(alpha = controlsOpacity))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (lyricsState) {
            is LyricsUiState.Idle -> {
                Text(
                    text = "No track playing",
                    color = SpotifyGrey,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            is LyricsUiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching lyrics online...",
                        color = SpotifyGrey,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            is LyricsUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Subtitles,
                        contentDescription = "No lyrics found",
                        tint = SpotifyGrey,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = lyricsState.message,
                        color = SpotifyGrey,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
            is LyricsUiState.Success -> {
                if (lyricsState.isSynced && lyricsState.syncedLines.isNotEmpty()) {
                    val syncedLines = lyricsState.syncedLines
                    // Find the line that is active
                    val activeLineIndex = syncedLines.indexOfLast { position >= it.timeMs }
                    val listState = rememberLazyListState()

                    // Automatically scroll to active line
                    LaunchedEffect(activeLineIndex) {
                        if (activeLineIndex >= 0) {
                            listState.animateScrollToItem(
                                index = activeLineIndex,
                                scrollOffset = -120 // Centers the active line beautifully
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(syncedLines) { index, line ->
                            val isActive = index == activeLineIndex
                            val isPast = index < activeLineIndex
                            
                            val textColor = when {
                                isActive -> Color.White
                                isPast -> SpotifyWhite.copy(alpha = 0.5f)
                                else -> SpotifyWhite.copy(alpha = 0.25f)
                            }
                            
                            val fontSize = if (isActive) 18.sp else 15.sp
                            val fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLineClick(line.timeMs) }
                                    .padding(vertical = 4.dp)
                                    .testTag("lyric_line_$index")
                            ) {
                                Text(
                                    text = line.text,
                                    color = textColor,
                                    fontSize = fontSize,
                                    fontWeight = fontWeight,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.animateContentSize()
                                )
                            }
                        }
                    }
                } else {
                    // Plain lyrics
                    val plainText = lyricsState.plainLyrics ?: "No text content available"
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Text(
                                text = "Plain Text Lyrics (Not Synced)",
                                color = accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        item {
                            Text(
                                text = plainText,
                                color = SpotifyWhite.copy(alpha = 0.85f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 24.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

fun triggerHapticFeedback(context: android.content.Context, type: String) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
    }

    if (vibrator == null || !vibrator.hasVibrator()) return

    try {
        val sharedPrefs = context.getSharedPreferences("music_player_settings", android.content.Context.MODE_PRIVATE)
        val hapticIntensity = sharedPrefs.getString("pref_haptic_intensity", "Crisp") ?: "Crisp"

        if (hapticIntensity == "Off") return

        val ampValue = when (hapticIntensity) {
            "Soft" -> 60
            "Strong" -> 255
            else -> 140 // Crisp
        }

        when (type) {
            "tick" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val duration = when (hapticIntensity) {
                        "Soft" -> 8L
                        "Strong" -> 16L
                        else -> 12L
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, ampValue))
                } else {
                    val duration = when (hapticIntensity) {
                        "Soft" -> 6L
                        "Strong" -> 15L
                        else -> 10L
                    }
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
            "snap" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val duration = when (hapticIntensity) {
                        "Soft" -> 15L
                        "Strong" -> 35L
                        else -> 25L
                    }
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, ampValue))
                } else {
                    val duration = when (hapticIntensity) {
                        "Soft" -> 12L
                        "Strong" -> 40L
                        else -> 25L
                    }
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
            "double_pulse" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings = when (hapticIntensity) {
                        "Soft" -> longArrayOf(0, 15, 100, 20)
                        "Strong" -> longArrayOf(0, 45, 60, 55)
                        else -> longArrayOf(0, 30, 80, 40)
                    }
                    val amplitudes = when (hapticIntensity) {
                        "Soft" -> intArrayOf(0, 50, 0, 50)
                        "Strong" -> intArrayOf(0, 255, 0, 255)
                        else -> intArrayOf(0, 140, 0, 140)
                    }
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    val timings = when (hapticIntensity) {
                        "Soft" -> longArrayOf(0, 15, 100, 20)
                        "Strong" -> longArrayOf(0, 45, 60, 55)
                        else -> longArrayOf(0, 30, 80, 40)
                    }
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, -1)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

