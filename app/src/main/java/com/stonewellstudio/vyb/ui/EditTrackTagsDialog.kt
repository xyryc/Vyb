package com.stonewellstudio.vyb.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stonewellstudio.vyb.TrackCoverImage
import com.stonewellstudio.vyb.data.TrackEntity
import com.stonewellstudio.vyb.localization.t
import com.stonewellstudio.vyb.ui.theme.*
import kotlinx.coroutines.launch

/**
 * ID3 Tag & Cover Art Editor Dialog
 *
 * Fully compliant with Google Play Policies:
 * - Uses zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) for cover art updates.
 * - Safely saves custom cover art to the app's secure internal storage without requiring any broad storage permissions.
 * - Allows searching and auto-fetching high-resolution album art from online iTunes API.
 * - Updates track metadata (Title, Artist, Album, Genre) and persists updates seamlessly in the Room database & active queue.
 */
@Composable
fun EditTrackTagsDialog(
    track: TrackEntity,
    language: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, genre: String, newCoverUri: Uri?) -> Unit,
    onFetchOnlineArt: suspend (artist: String, title: String) -> String?
) {
    var title by remember { mutableStateOf(track.title) }
    var artist by remember { mutableStateOf(track.artist) }
    var album by remember { mutableStateOf(track.album) }
    var genre by remember { mutableStateOf(track.genre) }

    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }
    var currentCoverDisplay by remember { mutableStateOf(track.coverUrl) }
    var isFetchingOnlineArt by remember { mutableStateOf(false) }
    var onlineFetchMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Standard Google Play compliant Photo Picker Launcher (PickVisualMedia)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedCoverUri = uri
            currentCoverDisplay = uri.toString()
            onlineFetchMessage = null
        }
    }

    val genres = listOf("Pop", "Rock", "Synthwave", "Vaporwave", "Electronic", "Techno", "Acoustic", "Lofi", "Hip Hop", "R&B", "Classical", "Ambient", "Jazz", "Metal")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .testTag("edit_track_tags_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = VybSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, VybSurfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(accentColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = t("edit_tags_title", language),
                                color = VybWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = t("id3_compliance_badge", language),
                                color = VybGrey,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = VybGrey
                        )
                    }
                }

                HorizontalDivider(
                    color = VybSurfaceVariant,
                    modifier = Modifier.padding(vertical = 14.dp)
                )

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover Art Section
                    Text(
                        text = t("cover_art", language),
                        color = VybWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Art Preview with photo picker click
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("pick_cover_art_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            TrackCoverImage(
                                url = currentCoverDisplay,
                                contentDescription = "Cover Art Preview",
                                modifier = Modifier.fillMaxSize()
                            )

                            // Hover / overlay icon to indicate editability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = "Pick Cover Photo",
                                    tint = VybWhite,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Actions: Safe Photo Picker & Online Search
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VybSurfaceVariant),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("choose_photo_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = VybWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = t("choose_photo_picker", language),
                                        color = VybWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (artist.isNotBlank() || title.isNotBlank()) {
                                        coroutineScope.launch {
                                            isFetchingOnlineArt = true
                                            onlineFetchMessage = null
                                            try {
                                                val fetchedUrl = onFetchOnlineArt(artist.trim(), title.trim())
                                                if (fetchedUrl != null) {
                                                    selectedCoverUri = null
                                                    currentCoverDisplay = fetchedUrl
                                                    onlineFetchMessage = t("download_art_success", language)
                                                } else {
                                                    onlineFetchMessage = t("download_art_fail", language)
                                                }
                                            } catch (e: Exception) {
                                                onlineFetchMessage = t("download_art_fail", language)
                                            } finally {
                                                isFetchingOnlineArt = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isFetchingOnlineArt && (artist.isNotBlank() || title.isNotBlank()),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("fetch_online_art_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isFetchingOnlineArt) {
                                        CircularProgressIndicator(
                                            color = accentColor,
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDownload,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isFetchingOnlineArt) t("download_art_searching", language) else t("search_online_art", language),
                                        color = accentColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (onlineFetchMessage != null) {
                        Text(
                            text = onlineFetchMessage!!,
                            color = if (onlineFetchMessage == t("download_art_success", language)) accentColor else VybGrey,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Metadata Text Fields
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Title
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(t("track_title", language), color = VybGrey, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = VybSurfaceVariant,
                                focusedTextColor = VybWhite,
                                unfocusedTextColor = VybWhite,
                                focusedContainerColor = VybSurfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = VybSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_title_input")
                        )

                        // Artist
                        OutlinedTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            label = { Text(t("artist_name", language), color = VybGrey, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = VybSurfaceVariant,
                                focusedTextColor = VybWhite,
                                unfocusedTextColor = VybWhite,
                                focusedContainerColor = VybSurfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = VybSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_artist_input")
                        )

                        // Album
                        OutlinedTextField(
                            value = album,
                            onValueChange = { album = it },
                            label = { Text(t("album_name", language), color = VybGrey, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = VybSurfaceVariant,
                                focusedTextColor = VybWhite,
                                unfocusedTextColor = VybWhite,
                                focusedContainerColor = VybSurfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = VybSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_album_input")
                        )

                        // Genre
                        OutlinedTextField(
                            value = genre,
                            onValueChange = { genre = it },
                            label = { Text(t("genre_name", language), color = VybGrey, fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentColor,
                                unfocusedBorderColor = VybSurfaceVariant,
                                focusedTextColor = VybWhite,
                                unfocusedTextColor = VybWhite,
                                focusedContainerColor = VybSurfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = VybSurfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_genre_input")
                        )
                    }

                    // Genre Quick-Pick Suggestions
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = t("genre_suggestions", language),
                            color = VybGrey,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(genres.size) { i ->
                                val itemGenre = genres[i]
                                val isSelected = genre.equals(itemGenre, ignoreCase = true)
                                Surface(
                                    color = if (isSelected) accentColor else VybSurfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .clickable { genre = itemGenre }
                                        .testTag("genre_chip_$itemGenre")
                                ) {
                                    Text(
                                        text = itemGenre,
                                        color = if (isSelected) VybBlack else VybWhite,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = VybSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                // Dialog Buttons (Cancel, Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("edit_tags_cancel_btn")
                    ) {
                        Text(
                            text = t("cancel", language),
                            color = VybWhite,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            val finalTitle = title.trim().ifBlank { track.title }
                            val finalArtist = artist.trim().ifBlank { track.artist }
                            val finalAlbum = album.trim().ifBlank { track.album }
                            val finalGenre = genre.trim().ifBlank { track.genre }
                            
                            // If user picked an online image that isn't stored locally yet, or selected a photo
                            onSave(finalTitle, finalArtist, finalAlbum, finalGenre, selectedCoverUri)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("edit_tags_save_btn")
                    ) {
                        Text(
                            text = t("save_tags", language),
                            color = VybBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
