package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.PlaylistEntity
import com.example.data.TrackEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyGrey
import com.example.ui.theme.SpotifySurface
import com.example.ui.theme.SpotifySurfaceVariant
import com.example.ui.theme.SpotifyWhite
import com.example.viewmodel.MusicViewModel
import com.example.viewmodel.ScreenState
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
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

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val viewModel: MusicViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.importLocalMp3(it)
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

    // Player states
    val currentTrack by viewModel.playerManager.currentTrack.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val playbackPosition by viewModel.playerManager.playbackPosition.collectAsState()
    val playbackDuration by viewModel.playerManager.playbackDuration.collectAsState()
    val isBuffering by viewModel.playerManager.isBuffering.collectAsState()
    val isShuffleEnabled by viewModel.playerManager.isShuffleEnabled.collectAsState()
    val isRepeatEnabled by viewModel.playerManager.isRepeatEnabled.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SpotifyBlack,
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
                }
            },
            containerColor = SpotifyBlack,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Screen Content
                when (val screen = currentScreen) {
                    is ScreenState.Home -> HomeScreen(
                        tracks = allTracks,
                        onTrackClick = { track -> viewModel.playTrack(track, allTracks) },
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
                        onPlaylistClick = { viewModel.navigateTo(ScreenState.PlaylistDetail(it)) },
                        onTrackClick = { track -> viewModel.playTrack(track, likedTracks) },
                        onCreatePlaylistClick = { viewModel.showCreatePlaylistDialog(true) },
                        onImportClick = { filePickerLauncher.launch("audio/*") },
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
                    onCollapse = { viewModel.setPlayerExpanded(false) }
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
    currentTrack: TrackEntity?,
    isPlaying: Boolean,
    onLikeClick: (TrackEntity) -> Unit
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
    ) {
        item {
            Text(
                text = greeting,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = SpotifyWhite,
                modifier = Modifier.padding(bottom = 16.dp)
            )
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
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportClick: () -> Unit,
    currentTrack: TrackEntity?,
    onLikeClick: (TrackEntity) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Playlists, 1 = Liked Songs

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
                IconButton(
                    onClick = onImportClick,
                    modifier = Modifier.testTag("import_music_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Publish,
                        contentDescription = "Import MP3",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(26.dp)
                    )
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

        // Custom tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                modifier = Modifier.testTag("liked_songs_tab")
            ) {
                Text(
                    "Liked Songs",
                    color = if (selectedTab == 1) SpotifyBlack else SpotifyWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        if (selectedTab == 0) {
            // Playlists List
            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = SpotifyGrey, modifier = Modifier.size(64.dp))
                        Text(
                            "No playlists created yet",
                            color = SpotifyGrey,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Button(
                            onClick = onCreatePlaylistClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("Create playlist", color = SpotifyBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(playlists) { playlist ->
                        PlaylistListItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
        } else {
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
    }
}

@Composable
fun PlaylistListItem(
    playlist: PlaylistEntity,
    onClick: () -> Unit
) {
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
                .clip(RoundedCornerShape(4.dp))
                .background(SpotifySurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(28.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = playlist.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyWhite
            )
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
                    IconButton(
                        onClick = onDeletePlaylist,
                        modifier = Modifier.testTag("delete_playlist_btn")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = Color.Red)
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
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifySurface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tracks.isNotEmpty()) {
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
                            text = "${tracks.size} songs",
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

            if (tracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This playlist has no songs yet.\nClick 'Add Songs' below to start building!",
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
                        trailingContent = {
                            IconButton(
                                onClick = { onRemoveTrack(track) },
                                modifier = Modifier.testTag("remove_track_${track.id}")
                            ) {
                                Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Remove", tint = SpotifyGrey)
                            }
                        }
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
    onCollapse: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpotifySurface,
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
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("player_collapse_btn")
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = SpotifyWhite, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
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
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onAddToPlaylistClick,
                    modifier = Modifier.testTag("player_add_playlist_btn")
                ) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = SpotifyWhite)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Giant Cover Art
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shadow(8.dp)
            ) {
                TrackCoverImage(
                    url = track.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
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
            Slider(
                value = if (duration > 0) position.toFloat() / duration else 0f,
                onValueChange = { onSeek((it * duration).toLong()) },
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
                Text(formatDuration(position), color = SpotifyGrey, fontSize = 12.sp)
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
