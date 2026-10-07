package com.stonewellstudio.vyb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stonewellstudio.vyb.data.TrackEntity
import com.stonewellstudio.vyb.importer.ImportHistoryItem
import com.stonewellstudio.vyb.importer.ImportStatus
import com.stonewellstudio.vyb.importer.ImportUrlUiState
import com.stonewellstudio.vyb.localization.LocalAppLanguage
import com.stonewellstudio.vyb.localization.t
import com.stonewellstudio.vyb.ui.theme.*
import com.stonewellstudio.vyb.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportFromUrlScreen(
    viewModel: MusicViewModel,
    onBackClick: () -> Unit,
    onOpenTrack: (TrackEntity) -> Unit
) {
    BackHandler {
        onBackClick()
    }

    val language = LocalAppLanguage.current
    val accentColor = LocalAccentColor.current
    val uiState by viewModel.urlImportUiState.collectAsState()
    val history by viewModel.urlImportHistory.collectAsState()

    var urlInput by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        containerColor = VybBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = t("import_from_url", language),
                        color = VybWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("import_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VybWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VybBlack
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header Description
            item {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    Text(
                        text = "Download audio directly to your Vyb library.",
                        color = VybWhite.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Paste a direct link to a supported audio file (.mp3, .m4a, .flac, .wav, .ogg, .opus).",
                        color = VybGrey,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // URL Input Field & Actions
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("url_input"),
                        placeholder = {
                            Text(
                                text = "https://example.com/audio/song.mp3",
                                color = VybGrey.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (urlInput.isNotEmpty()) {
                                    IconButton(onClick = { urlInput = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear URL",
                                            tint = VybGrey
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            urlInput = clipText.trim()
                                        }
                                    },
                                    modifier = Modifier.testTag("paste_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        tint = accentColor
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = VybSurfaceVariant,
                            focusedTextColor = VybWhite,
                            unfocusedTextColor = VybWhite,
                            focusedContainerColor = VybSurface,
                            unfocusedContainerColor = VybSurface,
                            cursorColor = accentColor
                        ),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                if (urlInput.isNotBlank()) {
                                    viewModel.startUrlImport(urlInput)
                                }
                            }
                        )
                    )

                    // Download Action Button
                    val isBusy = uiState is ImportUrlUiState.Validating || uiState is ImportUrlUiState.Downloading
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            if (urlInput.isNotBlank()) {
                                viewModel.startUrlImport(urlInput)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("download_button"),
                        enabled = urlInput.isNotBlank() && !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = VybBlack,
                            disabledContainerColor = accentColor.copy(alpha = 0.35f),
                            disabledContentColor = VybBlack.copy(alpha = 0.5f)
                        )
                    ) {
                        if (uiState is ImportUrlUiState.Validating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = VybBlack,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Validating Link...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = t("download", language),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // Legal & Authorization Notice Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = VybSurface.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, VybSurfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = "Authorization policy",
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Only use URLs for audio files you are authorized to download. Direct links (.mp3, .m4a, .flac, etc.) only.",
                            color = VybGrey,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Dynamic State Cards: Progress, Success, Duplicate, or Error
            item {
                AnimatedContent(
                    targetState = uiState,
                    label = "import_state_transition"
                ) { state ->
                    when (state) {
                        is ImportUrlUiState.Idle -> {
                            Spacer(modifier = Modifier.height(0.dp))
                        }

                        is ImportUrlUiState.Validating -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("validating_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = VybSurface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Checking audio resource...",
                                            color = VybWhite,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Inspecting stream headers and format",
                                            color = VybGrey,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        is ImportUrlUiState.Downloading -> {
                            val animatedProgress by animateFloatAsState(
                                targetValue = (state.progress / 100f).coerceIn(0f, 1f),
                                label = "progress_anim"
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_progress_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = VybSurface),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Download,
                                                contentDescription = null,
                                                tint = accentColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Downloading...",
                                                color = accentColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Text(
                                            text = "${state.progress}%",
                                            color = VybWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }

                                    Text(
                                        text = state.fileName,
                                        color = VybWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = accentColor,
                                        trackColor = VybSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${state.downloadedFormatted} / ${state.totalFormatted}",
                                            color = VybGrey,
                                            fontSize = 12.sp
                                        )

                                        if (state.speedFormatted.isNotBlank()) {
                                            Text(
                                                text = state.speedFormatted,
                                                color = VybGrey,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.cancelUrlImport() },
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .testTag("cancel_download_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFEF4444)
                                        ),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                                    ) {
                                        Text("Cancel", fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        is ImportUrlUiState.DuplicateFound -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("duplicate_dialog"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = VybSurface),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "This song is already in your library.",
                                            color = VybWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }

                                    // Existing track preview
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(VybSurfaceVariant, RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = state.existingTrack.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = state.existingTrack.title,
                                                color = VybWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = state.existingTrack.artist,
                                                color = VybGrey,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onOpenTrack(state.existingTrack)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                        ) {
                                            Text("Open Song", color = VybBlack, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                viewModel.startUrlImport(state.url, forceReimport = true)
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Import Again", color = VybWhite)
                                        }

                                        IconButton(onClick = { viewModel.dismissUrlImportResult() }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = VybGrey)
                                        }
                                    }
                                }
                            }
                        }

                        is ImportUrlUiState.Success -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("success_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = VybSurface),
                                border = BorderStroke(1.dp, accentColor)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "✓ Added to Vyb",
                                            color = accentColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }

                                    // Imported track details
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(VybSurfaceVariant, RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = state.track.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = state.track.title,
                                                color = VybWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = state.track.artist,
                                                color = VybGrey,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (state.track.album.isNotBlank() && state.track.album != "URL Import") {
                                                Text(
                                                    text = state.track.album,
                                                    color = VybGrey.copy(alpha = 0.8f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { onOpenTrack(state.track) },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                        ) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = VybBlack,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Play Now", color = VybBlack, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = onBackClick,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("View Library", color = VybWhite)
                                        }
                                    }
                                }
                            }
                        }

                        is ImportUrlUiState.Error -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("error_card"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = VybSurface),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.ErrorOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Download failed",
                                            color = Color(0xFFEF4444),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }

                                    Text(
                                        text = state.message,
                                        color = VybWhite.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (state.canRetry) {
                                            Button(
                                                onClick = { viewModel.retryLastUrlImport() },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                            ) {
                                                Text("Retry", color = VybBlack, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        TextButton(onClick = { viewModel.dismissUrlImportResult() }) {
                                            Text("Dismiss", color = VybGrey)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Imports / History Section
            if (history.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent URL Imports",
                            color = VybWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )

                        TextButton(onClick = { viewModel.clearUrlImportHistory() }) {
                            Text("Clear", color = VybGrey, fontSize = 12.sp)
                        }
                    }
                }

                items(history, key = { it.id }) { item ->
                    ImportHistoryRow(
                        item = item,
                        accentColor = accentColor,
                        onOpenTrackId = { trackId ->
                            val track = viewModel.allTracks.value.find { it.id == trackId }
                            if (track != null) {
                                onOpenTrack(track)
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun ImportHistoryRow(
    item: ImportHistoryItem,
    accentColor: Color,
    onOpenTrackId: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = VybSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = when (item.status) {
                ImportStatus.COMPLETED -> Icons.Filled.CheckCircle to accentColor
                ImportStatus.FAILED -> Icons.Filled.ErrorOutline to Color(0xFFEF4444)
                ImportStatus.CANCELLED -> Icons.Filled.Cancel to VybGrey
                ImportStatus.DOWNLOADING -> Icons.Filled.Download to accentColor
                ImportStatus.VALIDATING, ImportStatus.QUEUED -> Icons.Filled.HourglassEmpty to VybGrey
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = VybWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.errorMessage != null) item.errorMessage else item.url,
                    color = if (item.errorMessage != null) Color(0xFFEF4444).copy(alpha = 0.8f) else VybGrey,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.status == ImportStatus.COMPLETED && item.trackId != null) {
                IconButton(
                    onClick = { onOpenTrackId(item.trackId) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = accentColor
                    )
                }
            }
        }
    }
}
