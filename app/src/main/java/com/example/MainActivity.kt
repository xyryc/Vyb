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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyGrey
import com.example.ui.theme.SpotifySurface
import com.example.ui.theme.SpotifySurfaceVariant
import com.example.ui.theme.SpotifyWhite
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.ScreenState
import java.util.Calendar
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

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
    override fun attachBaseContext(newBase: android.content.Context?) {
        if (newBase != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            super.attachBaseContext(newBase.createAttributionContext("music_playback"))
        } else {
            super.attachBaseContext(newBase)
        }
    }

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
    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    val showAddToPlaylistDialog by viewModel.showAddToPlaylistDialog.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
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
                        label = { Text("Home", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
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
                        label = { Text("Search", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
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
                        label = { Text("Your Library", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
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
                        label = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
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
                            viewModel = viewModel
                        )
                    }
                }

                 // Mini Player (Only visible if a track is selected and player is not expanded)
                if (currentTrack != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
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
                            onClick = { viewModel.setPlayerExpanded(true) }
                        )
                    }
                }
            }
        }

        // Expanded full-screen player
        AnimatedVisibility(
            visible = isPlayerExpanded && currentTrack != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(400)
            ) + fadeIn(animationSpec = tween(400)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(350)
            ) + fadeOut(animationSpec = tween(350))
        ) {
            if (currentTrack != null) {
                ExpandedPlayerScreen(
                    track = currentTrack!!,
                    isPlaying = isPlaying,
                    position = playbackPosition,
                    duration = playbackDuration,
                    isBuffering = isBuffering,
                    isShuffleEnabled = isShuffleEnabled,
                    isRepeatEnabled = isRepeatEnabled,
                    playlists = playlists,
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
                    dominantColor = animatedDominantColor,
                    secondaryColor = animatedSecondaryColor
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
    }
}

@Composable
fun TrackCoverImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = url,
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

    val greetingText = when (hour) {
        in 5..11 -> "Good morning 🌅"
        in 12..16 -> "Good afternoon ☀️"
        in 17..21 -> "Good evening 🌇"
        else -> "Late night vibes 🌌"
    }

    val greetingSubtitle = when (hour) {
        in 5..11 -> "Ready to start your day with some tunes?"
        in 12..16 -> "Keep the energy high and the music playing!"
        in 17..21 -> "Time to unwind and find your evening groove."
        else -> "Winding down, or chasing late-night ideas?"
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
                    text = "Made For You",
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
                            onClick = { onPlaylistClick(mix) }
                        )
                    }
                }
            }
        }

        // Featured Songs list
        item {
            Text(
                text = "More of what you like",
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
                        text = "SMART MIX",
                        color = SpotifyWhite.copy(alpha = 0.8f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Column {
                    Text(
                        text = playlist.name,
                        color = SpotifyWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlist.description,
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
            text = "Search",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyWhite,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
        )

        // Search Bar
        TextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("What do you want to listen to?", color = SpotifyGrey) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = SpotifyGrey) },
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
                text = "Browse all",
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
                                text = "No results found for \"$searchQuery\"",
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
            text = genre,
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
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportFileClick: () -> Unit,
    onImportFolderClick: () -> Unit,
    currentTrack: TrackEntity?,
    onLikeClick: (TrackEntity) -> Unit
) {
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
                text = "Your Library",
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
                            contentDescription = "Import MP3 Options",
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
                            text = { Text("Import MP3 File", color = SpotifyWhite) },
                            onClick = {
                                showImportMenu = false
                                onImportFileClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SpotifyGreen) }
                        )
                        DropdownMenuItem(
                            text = { Text("Import Entire Folder", color = SpotifyWhite) },
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
                        contentDescription = "Create Playlist",
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
                    "Playlists",
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
                    "Liked Songs",
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
                    "Smart Folders",
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
                    "Insights",
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
                            text = "Made For You",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    items(getSmartMixes()) { playlist ->
                        PlaylistListItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }

                    item {
                        Text(
                            text = "Your Playlists",
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
                                        text = "No custom playlists yet",
                                        color = SpotifyGrey,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onCreatePlaylistClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                                    ) {
                                        Text("Create playlist", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        items(playlists) { playlist ->
                            PlaylistListItem(
                                playlist = playlist,
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
                                "Songs you like will appear here",
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
                text = "Organize SoundWave By:",
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
                    Pair(0, "📁 Folders"),
                    Pair(1, "👤 Artists"),
                    Pair(2, "🎶 Genres"),
                    Pair(3, "📅 Import Dates")
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
                            "No tracks found to group",
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
                                        Text(
                                            text = groupKey,
                                            fontWeight = FontWeight.Bold,
                                            color = SpotifyWhite,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${groupTracks.size} ${if (groupTracks.size == 1) "track" else "tracks"}",
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
                        contentDescription = "Back to Folders",
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
                                0 -> "Physical Folder"
                                1 -> "Artist Smart Folder"
                                2 -> "Genre Smart Folder"
                                else -> "Time Group"
                            },
                            color = SpotifyGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = currentGroupKey,
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
                    text = playlist.name,
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
                            text = "Smart Mix",
                            color = SpotifyGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = if (playlist.description.isEmpty()) "Playlist" else playlist.description,
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
                            text = playlist.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite
                        )
                        if (playlist.description.isNotEmpty()) {
                            Text(
                                text = playlist.description,
                                fontSize = 14.sp,
                                color = SpotifyGrey,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = if (isSmart) "Auto-curated mix" else "${tracks.size} songs",
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
                            Text("Play", color = SpotifyBlack, fontWeight = FontWeight.Bold)
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
                            Text("Add Songs", color = SpotifyWhite, fontSize = 13.sp)
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
                                "Listen to more tracks and like songs to populate this mix!"
                            } else {
                                "This playlist has no songs yet.\nClick 'Add Songs' below to start building!"
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
                            Text("Add Songs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SpotifyWhite)
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
                                Text("All database songs are already in this playlist!", color = SpotifyGrey, textAlign = TextAlign.Center)
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
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SpotifySurface),
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
                    onClick = onLikeClick,
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
    dominantColor: Color = SpotifySurface,
    secondaryColor: Color = SpotifyBlack
) {
    var showVisualizer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        dominantColor,
                        secondaryColor,
                        SpotifyBlack
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .testTag("expanded_player")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(2f)
                )

                Row(
                    modifier = Modifier.weight(1.2f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showVisualizer = !showVisualizer },
                        modifier = Modifier.testTag("player_visualizer_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (showVisualizer) Icons.Filled.GraphicEq else Icons.Outlined.GraphicEq,
                            contentDescription = "Toggle Visualizer",
                            tint = if (showVisualizer) SpotifyGreen else SpotifyWhite
                        )
                    }
                    IconButton(
                        onClick = onEqualizerClick,
                        modifier = Modifier.testTag("player_equalizer_btn")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "Equalizer", tint = SpotifyWhite)
                    }
                    IconButton(
                        onClick = onAddToPlaylistClick,
                        modifier = Modifier.testTag("player_add_playlist_btn")
                    ) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = SpotifyWhite)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Giant Cover Art / Fluid Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .testTag("player_center_art_area"),
                contentAlignment = Alignment.Center
            ) {
                if (showVisualizer) {
                    com.example.player.FluidVisualizer(
                        track = track,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shadow(8.dp)
                            .clickable { showVisualizer = true }
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
                    onClick = onLikeClick,
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

            Spacer(modifier = Modifier.height(24.dp))

            // Progress Slider
            var isDragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableStateOf(0f) }
            val displayPosition = if (isDragging) {
                (dragValue * duration).toLong()
            } else {
                position
            }

            Slider(
                value = if (isDragging) dragValue else (if (duration > 0) position.toFloat() / duration else 0f),
                onValueChange = {
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

            Spacer(modifier = Modifier.weight(0.5f))

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
                        .size(72.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(SpotifyWhite)
                        .clickable(onClick = onPlayPauseClick)
                        .testTag("player_play_pause_btn")
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = SpotifyBlack,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = SpotifyBlack,
                            modifier = Modifier.size(36.dp)
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
    currentRemainingMs: Long
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Sleep Timer",
                color = SpotifyWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentRemainingMs > 0) {
                    Text(
                        text = "Current timer: ${formatSleepTimer(currentRemainingMs)} remaining",
                        color = SpotifyGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        text = "Stop audio playback after a set time.",
                        color = SpotifyGrey,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val presets = listOf(
                    5 to "5 Minutes",
                    15 to "15 Minutes",
                    30 to "30 Minutes",
                    45 to "45 Minutes",
                    60 to "60 Minutes"
                )

                presets.forEach { (minutes, label) ->
                    TextButton(
                        onClick = { onSelectTimer(minutes) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sleep_timer_preset_$minutes")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = SpotifyWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        containerColor = SpotifySurface,
        confirmButton = {
            if (currentRemainingMs > 0) {
                Button(
                    onClick = onCancelTimer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.testTag("sleep_timer_cancel_btn")
                ) {
                    Text("Turn Off Timer", color = SpotifyWhite, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("sleep_timer_dismiss_btn")
            ) {
                Text("Close", color = SpotifyWhite)
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
                        text = "Enable Dynamic Island",
                        fontWeight = FontWeight.Bold,
                        color = SpotifyWhite,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Get sleek iOS-style floating controls when you minimize the app. Tap to set up!",
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
fun SettingsScreen(
    viewModel: MusicViewModel,
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
    var islandOpacity by remember {
        mutableStateOf(sharedPrefs.getFloat("island_opacity", 0.95f))
    }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = SpotifyWhite,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Theme Accent Colors Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SpotifyGreen.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = "Theme Accent",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Theme Accent Colors",
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Select your neon brand flavor for the player",
                            color = SpotifyGrey,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Smooth horizontal carousel of available brand styles
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                ) {
                    items(com.example.ui.theme.ThemeAccent.values()) { accent ->
                        val isSelected = currentAccent == accent
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.04f else 0.96f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
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
                                .clickable { viewModel.setThemeAccent(accent) }
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
                                // Double circular ring for beautiful depth
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(accent.color.copy(alpha = 0.2f), CircleShape)
                                        .border(1.dp, accent.color.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(accent.color, CircleShape)
                                    )
                                }
                                Text(
                                    text = accent.label,
                                    color = if (isSelected) SpotifyWhite else SpotifyGrey,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Sleep Timer Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SpotifyGreen.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Sleep Timer",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Sleep Timer",
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (sleepTimerRemaining > 0) {
                                "Stops playback in ${formatSleepTimer(sleepTimerRemaining)}"
                            } else {
                                "Automatically stop audio playback"
                            },
                            color = if (sleepTimerRemaining > 0) SpotifyGreen else SpotifyGrey,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                            text = "ACTIVE COUNTDOWN",
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
                            color = SpotifyGreen,
                            trackColor = SpotifySurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(18.dp))
                        
                        Text(
                            text = "Extend Timer",
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
                                        .clickable { viewModel.extendSleepTimer(extMinutes) }
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
                            onClick = { viewModel.cancelSleepTimer() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_sleep_timer_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel Sleep Timer", color = SpotifyWhite, fontWeight = FontWeight.Bold)
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
                                text = "Custom Duration",
                                color = SpotifyWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${customMinutes.toInt()} min",
                                color = SpotifyGreen,
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
                                thumbColor = SpotifyGreen,
                                activeTrackColor = SpotifyGreen,
                                inactiveTrackColor = SpotifySurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.setSleepTimer(customMinutes.toInt()) },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Start Sleep Timer",
                                color = SpotifyBlack,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Real Equalizer Settings Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(currentAccent.color.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Equalizer",
                            tint = currentAccent.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acoustic Equalizer",
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            fontSize = 16.sp
                        )
                        val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
                        Text(
                            text = if (isEqualizerEnabled) "Equalizer & Sound Effects Active" else "Enhance your offline listening experience",
                            color = if (isEqualizerEnabled) currentAccent.color else SpotifyGrey,
                            fontSize = 12.sp
                        )
                    }
                    val isEqualizerEnabled by viewModel.isEqualizerEnabled.collectAsState()
                    Switch(
                        checked = isEqualizerEnabled,
                        onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyBlack,
                            checkedTrackColor = currentAccent.color,
                            uncheckedThumbColor = SpotifyGrey,
                            uncheckedTrackColor = SpotifySurfaceVariant
                        ),
                        modifier = Modifier.graphicsLayer {
                            scaleX = 0.85f
                            scaleY = 0.85f
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                var showSettingsEqualizer by remember { mutableStateOf(false) }

                Button(
                    onClick = { showSettingsEqualizer = true },
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
                            text = "Open Equalizer Studio",
                            color = SpotifyWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (showSettingsEqualizer) {
                    EqualizerDialog(
                        viewModel = viewModel,
                        onDismiss = { showSettingsEqualizer = false }
                    )
                }
            }
        }

        // Floating Dynamic Island Customization
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SpotifyGreen.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Layers,
                            contentDescription = "Dynamic Island Settings",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Floating Dynamic Island",
                            fontWeight = FontWeight.Bold,
                            color = SpotifyWhite,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Behavior & Overlay Styling",
                            color = SpotifyGrey,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Permission Warning & Trigger button
                val hasPermission = Settings.canDrawOverlays(context)
                if (!hasPermission) {
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
                                    text = "System Overlay Permission Required",
                                    color = Color(0xFFFF9800),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap here to authorize displaying the float bar.",
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
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SpotifyGreen.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Active",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Overlay Permission is active",
                            color = SpotifyGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Mode Selector Title
                Text(
                    text = "Overlay Modes",
                    color = SpotifyWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Layout Modes Options: COMPACT, EXPANDED, AUTO
                val modes = listOf(
                    Triple("COMPACT", "Compact Pill", "Always small & unobtrusive"),
                    Triple("EXPANDED", "Expanded Card", "Always shows controls and details"),
                    Triple("AUTO", "Auto-expanding", "Expands on long-press interaction")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                    color = if (isSelected) SpotifyGreen else SpotifySurfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    islandMode = modeKey
                                    sharedPrefs.edit().putString("island_mode", modeKey).apply()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    islandMode = modeKey
                                    sharedPrefs.edit().putString("island_mode", modeKey).apply()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = SpotifyGreen,
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

                Spacer(modifier = Modifier.height(20.dp))

                // Transparency Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Background Opacity",
                        color = SpotifyWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(islandOpacity * 100).toInt()}%",
                        color = SpotifyGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = islandOpacity,
                    onValueChange = {
                        islandOpacity = it
                        sharedPrefs.edit().putFloat("island_opacity", it).apply()
                    },
                    valueRange = 0.3f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = SpotifyGreen,
                        activeTrackColor = SpotifyGreen,
                        inactiveTrackColor = SpotifySurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ListeningInsightsDashboard(
    tracks: List<TrackEntity>,
    onTrackClick: (TrackEntity) -> Unit,
    onLikeClick: (TrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val totalSeconds = remember { mutableStateOf(com.example.data.ListeningStatsManager.getTotalListeningTimeSeconds(context)) }
    val topGenres = remember { mutableStateOf(com.example.data.ListeningStatsManager.getTopGenres(context)) }
    val topArtists = remember { mutableStateOf(com.example.data.ListeningStatsManager.getTopArtists(context)) }

    LaunchedEffect(Unit) {
        totalSeconds.value = com.example.data.ListeningStatsManager.getTotalListeningTimeSeconds(context)
        topGenres.value = com.example.data.ListeningStatsManager.getTopGenres(context)
        topArtists.value = com.example.data.ListeningStatsManager.getTopArtists(context)
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
                .padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifySurfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Listening Time",
                    color = SpotifyGrey,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatListeningTime(totalSeconds.value),
                    color = SpotifyGreen,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Your music habits are looking great! Here is a visual overview of your music journey.",
                    color = SpotifyWhite.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            }
        }

        // Top Genres Section
        Text(
            text = "Top Genres",
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
                    Text("No genre stats recorded yet", color = SpotifyGrey, fontSize = 13.sp)
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
                                    text = "${seconds / 60}m",
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
            text = "Favorite Artists",
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
                    Text("No artist stats recorded yet", color = SpotifyGrey, fontSize = 13.sp)
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
                                    text = "Listened: ${seconds / 60} minutes",
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
                text = "Most Played Tracks",
                color = SpotifyWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

fun formatListeningTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours} hr ${minutes} min"
        else -> "${minutes} min"
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
                        text = "Sound Control",
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
                        text = "Acoustic Profiles (Presets)",
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
                                    text = "Custom",
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
                        text = "Dynamic Frequency Response",
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
                        text = "Acoustic Effects Boosters",
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
                            label = "Deep Bass Boost",
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
                            label = "Spatial 3D Surround",
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
                            text = "💡 Toggle the switch ON at the top to activate custom frequency bands, acoustic presets, deep bass boost, and spatial 3D audio!",
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
                Text("Close", color = SpotifyBlack, fontWeight = FontWeight.Bold)
            }
        }
    )
}
