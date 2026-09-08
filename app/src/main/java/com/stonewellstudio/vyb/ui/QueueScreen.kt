package com.stonewellstudio.vyb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.stonewellstudio.vyb.TrackCoverImage
import com.stonewellstudio.vyb.data.TrackEntity
import com.stonewellstudio.vyb.formatDuration
import com.stonewellstudio.vyb.localization.t
import com.stonewellstudio.vyb.triggerHapticFeedback
import com.stonewellstudio.vyb.ui.theme.VybBlack
import com.stonewellstudio.vyb.ui.theme.VybGreen
import com.stonewellstudio.vyb.ui.theme.VybGrey
import com.stonewellstudio.vyb.ui.theme.VybSurface
import com.stonewellstudio.vyb.ui.theme.VybSurfaceVariant
import com.stonewellstudio.vyb.ui.theme.VybWhite

/**
 * High-fidelity Queue Modal Screen featuring real-time Drag-and-Drop Reordering,
 * Swipe/tap remove, Now Playing equalizer badge, and session history.
 */
@Composable
fun QueueModalScreen(
    currentTrack: TrackEntity?,
    queue: List<TrackEntity>,
    isPlaying: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    language: String,
    onClose: () -> Unit,
    onTrackClick: (TrackEntity) -> Unit,
    onReorderQueue: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveFromQueue: (index: Int) -> Unit,
    onClearQueue: () -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onLikeClick: (TrackEntity) -> Unit,
    accentColor: Color = VybGreen
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val currentIndex = remember(queue, currentTrack) {
        if (currentTrack != null) queue.indexOfFirst { it.id == currentTrack.id } else -1
    }

    val historyTracks = remember(queue, currentIndex) {
        if (currentIndex > 0) queue.subList(0, currentIndex) else emptyList()
    }

    val upNextTracks = remember(queue, currentIndex) {
        if (currentIndex != -1 && currentIndex < queue.size - 1) {
            queue.subList(currentIndex + 1, queue.size)
        } else if (currentIndex == -1) {
            queue
        } else {
            emptyList()
        }
    }

    // Drag-and-Drop State tracking for Up Next list
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var draggingItemIndex by remember { mutableIntStateOf(-1) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }

    var showHistory by remember { mutableStateOf(false) }

    val totalUpNextDurationMs = remember(upNextTracks) {
        upNextTracks.sumOf { it.durationMs }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VybBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("queue_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        triggerHapticFeedback(context, "snap")
                        onClose()
                    },
                    modifier = Modifier.testTag("queue_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close Queue",
                        tint = VybWhite,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = t("queue", language),
                        color = VybWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (upNextTracks.isNotEmpty()) {
                        val minutes = (totalUpNextDurationMs / 60000).toInt()
                        Text(
                            text = "${upNextTracks.size} tracks • ${minutes}m",
                            color = VybGrey,
                            fontSize = 12.sp
                        )
                    }
                }

                if (upNextTracks.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            triggerHapticFeedback(context, "double_pulse")
                            onClearQueue()
                        },
                        modifier = Modifier.testTag("queue_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = t("clear_queue", language),
                            tint = VybGrey,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // SECTION: Now Playing
                if (currentTrack != null) {
                    item(key = "header_now_playing") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accentColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = t("now_playing", language).uppercase(),
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    item(key = "item_now_playing_${currentTrack.id}") {
                        NowPlayingQueueCard(
                            track = currentTrack,
                            isPlaying = isPlaying,
                            playbackPosition = playbackPosition,
                            playbackDuration = playbackDuration,
                            accentColor = accentColor,
                            onLikeClick = { onLikeClick(currentTrack) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        )
                    }
                }

                // SECTION: Up Next (Reorderable)
                item(key = "header_up_next") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = t("up_next", language),
                                color = VybWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (upNextTracks.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(VybSurfaceVariant)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${upNextTracks.size}",
                                        color = VybWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (upNextTracks.isNotEmpty()) {
                            Text(
                                text = t("drag_to_reorder", language),
                                color = VybGrey,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (upNextTracks.isEmpty()) {
                    item(key = "empty_up_next") {
                        EmptyQueueCard(language = language)
                    }
                } else {
                    itemsIndexed(
                        items = upNextTracks,
                        key = { _, track -> "up_next_${track.id}" }
                    ) { upNextIndex, track ->
                        val isBeingDragged = draggingItemId == track.id
                        val itemHeightPx = with(density) { 68.dp.toPx() }

                        val translationY = if (isBeingDragged) dragAccumulatedY else 0f
                        val zIndex = if (isBeingDragged) 10f else 1f
                        val elevation = if (isBeingDragged) 10.dp else 0.dp
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isBeingDragged) 1.03f else 1.0f,
                            animationSpec = tween(durationMillis = 150)
                        )

                        val actualQueueIndex = (if (currentIndex != -1) currentIndex + 1 else 0) + upNextIndex

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .zIndex(zIndex)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    this.scaleX = animatedScale
                                    this.scaleY = animatedScale
                                    this.shadowElevation = elevation.toPx()
                                }
                        ) {
                            QueueTrackRow(
                                track = track,
                                indexNumber = upNextIndex + 1,
                                isBeingDragged = isBeingDragged,
                                accentColor = accentColor,
                                onClick = {
                                    triggerHapticFeedback(context, "snap")
                                    onTrackClick(track)
                                },
                                onRemove = {
                                    triggerHapticFeedback(context, "snap")
                                    onRemoveFromQueue(actualQueueIndex)
                                },
                                onPlayNext = {
                                    triggerHapticFeedback(context, "snap")
                                    onPlayNext(track)
                                },
                                dragHandleModifier = Modifier
                                    .pointerInput(track.id, upNextIndex, upNextTracks.size) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingItemId = track.id
                                                draggingItemIndex = upNextIndex
                                                dragAccumulatedY = 0f
                                                triggerHapticFeedback(context, "snap")
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumulatedY += dragAmount.y

                                                val curIdx = draggingItemIndex
                                                if (curIdx >= 0 && curIdx < upNextTracks.size) {
                                                    val slotOffset = (dragAccumulatedY / itemHeightPx).toInt()
                                                    val targetIdx = (curIdx + slotOffset).coerceIn(0, upNextTracks.lastIndex)

                                                    if (targetIdx != curIdx) {
                                                        val actualFrom = (if (currentIndex != -1) currentIndex + 1 else 0) + curIdx
                                                        val actualTo = (if (currentIndex != -1) currentIndex + 1 else 0) + targetIdx

                                                        onReorderQueue(actualFrom, actualTo)
                                                        draggingItemIndex = targetIdx
                                                        dragAccumulatedY -= (targetIdx - curIdx) * itemHeightPx
                                                        triggerHapticFeedback(context, "tick")
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggingItemId = null
                                                draggingItemIndex = -1
                                                dragAccumulatedY = 0f
                                                triggerHapticFeedback(context, "double_pulse")
                                            },
                                            onDragCancel = {
                                                draggingItemId = null
                                                draggingItemIndex = -1
                                                dragAccumulatedY = 0f
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }

                // SECTION: Previously Played (History)
                if (historyTracks.isNotEmpty()) {
                    item(key = "header_history") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showHistory = !showHistory }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Previously Played (${historyTracks.size})",
                                color = VybGrey,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (showHistory) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showHistory) "Collapse history" else "Expand history",
                                tint = VybGrey
                            )
                        }
                    }

                    if (showHistory) {
                        itemsIndexed(
                            items = historyTracks,
                            key = { _, track -> "history_${track.id}" }
                        ) { historyIndex, track ->
                            HistoryTrackRow(
                                track = track,
                                onClick = {
                                    triggerHapticFeedback(context, "snap")
                                    onTrackClick(track)
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

/**
 * Prominent card displaying the currently playing track with animated equalizers
 */
@Composable
fun NowPlayingQueueCard(
    track: TrackEntity,
    isPlaying: Boolean,
    playbackPosition: Long,
    playbackDuration: Long,
    accentColor: Color,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VybSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = modifier.testTag("queue_now_playing_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shadow(4.dp)
            ) {
                TrackCoverImage(
                    url = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize()
                )
                // Equalizer overlay indicator
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedEqualizerMini(accentColor = accentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    color = VybWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = VybGrey,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (playbackDuration > 0) {
                    Text(
                        text = "${formatDuration(playbackPosition)} / ${formatDuration(playbackDuration)}",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = onLikeClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (track.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (track.isLiked) accentColor else VybGrey,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Individual track row in the "Up Next" queue, equipped with a dedicated drag handle
 */
@Composable
fun QueueTrackRow(
    track: TrackEntity,
    indexNumber: Int,
    isBeingDragged: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onPlayNext: () -> Unit,
    dragHandleModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isBeingDragged) VybSurfaceVariant else VybSurface.copy(alpha = 0.6f)
    val borderColor = if (isBeingDragged) accentColor.copy(alpha = 0.8f) else Color.Transparent

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("queue_track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index Number
            Text(
                text = "$indexNumber",
                color = VybGrey,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Thumbnail
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                TrackCoverImage(
                    url = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artist
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    color = VybWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = VybGrey,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Duration
            Text(
                text = formatDuration(track.durationMs),
                color = VybGrey,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // Remove Button
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("queue_remove_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove from Queue",
                    tint = VybGrey,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Drag Handle with minimum 48dp touch target
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .then(dragHandleModifier)
                    .testTag("queue_drag_handle_${track.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = if (isBeingDragged) accentColor else VybGrey,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Compact row for previously played tracks
 */
@Composable
fun HistoryTrackRow(
    track: TrackEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            TrackCoverImage(
                url = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = VybGrey,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = VybGrey.copy(alpha = 0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Replay",
            tint = VybGrey,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Empty queue state indicator
 */
@Composable
fun EmptyQueueCard(
    language: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = VybSurface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = VybGrey,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = t("empty_queue", language),
                color = VybWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Add songs to queue or pick a playlist to keep the music playing.",
                color = VybGrey,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 3-bar animated equalizer graphic indicating active playback
 */
@Composable
fun AnimatedEqualizerMini(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer_bars")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar1Height.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar2Height.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(bar3Height.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accentColor)
        )
    }
}
