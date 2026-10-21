package com.platinum20.dashboard.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableFloatStateOf
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.video.VideoFrameDecoder
import com.platinum20.dashboard.media.VideoEntry
import com.platinum20.dashboard.media.VideoLibrary
import com.platinum20.dashboard.media.youtubeWatchUrl

/** Player per video e audio del telefono o da un link (mp4, webm, m3u8...). */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    onOpenYouTube: (String) -> Unit,
) {
    val context = LocalContext.current
    VideoLibrary.init(context)
    val recent by VideoLibrary.recent.collectAsStateWithLifecycle()

    var current by remember { mutableStateOf<VideoEntry?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var subtitleName by remember { mutableStateOf<String?>(null) }

    val player = remember { ExoPlayer.Builder(context).build() }
    val thumbnails = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    val savePosition = {
        current?.let { VideoLibrary.updatePosition(it.uri, player.currentPosition, player.duration.coerceAtLeast(0)) }
    }
    val play = { uri: Uri ->
        savePosition()
        val entry = VideoLibrary.open(context, uri)
        current = entry
        error = null
        subtitleName = null
        player.setMediaItem(MediaItem.fromUri(uri), entry.positionMs)
        player.prepare()
        player.play()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(play)
    }
    // Sottotitoli esterni (.srt, .vtt): il video riparte dallo stesso punto con la traccia aggiunta.
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val entry = current ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val name = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(if (name.endsWith(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
            .setLanguage("und")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val item = MediaItem.Builder().setUri(entry.uri).setSubtitleConfigurations(listOf(subtitle)).build()
        val position = player.currentPosition
        player.setMediaItem(item, position)
        player.prepare()
        player.play()
        subtitleName = name.substringBeforeLast('.').ifBlank { "Sottotitoli" }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connessione non riuscita"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "File non trovato"
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Permesso di lettura scaduto: riapri il file"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Formato non supportato"
                    else -> "Impossibile riprodurre (${e.errorCodeName})"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            savePosition()
            player.removeListener(listener)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        player.pause()
        savePosition()
    }

    FullscreenEffect(enabled = fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    Scaffold(
        topBar = {
            if (!fullscreen) {
                TopAppBar(
                    title = {
                        Text(
                            current?.title ?: "Video",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                )
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(if (fullscreen) PaddingValues() else inner)
        ) {
            if (current != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.aspectRatio(16f / 9f))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                keepScreenOn = true
                                setFullscreenButtonClickListener { fullscreen = it }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    error?.let {
                        Text(
                            it,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f), MaterialTheme.shapes.medium)
                                .padding(12.dp),
                        )
                    }
                }
            }

            if (!fullscreen) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (current == null) {
                        item { EmptyPlayer() }
                    } else {
                        item {
                            PlayerOptions(
                                speed = speed,
                                onSpeed = {
                                    speed = it
                                    player.setPlaybackSpeed(it)
                                },
                                subtitleName = subtitleName,
                                onPickSubtitles = {
                                    subtitlePicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/*", "application/octet-stream"))
                                },
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(
                                onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Dal telefono")
                            }
                            OutlinedButton(
                                onClick = { showLinkDialog = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Da un link")
                            }
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Recenti",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp),
                                )
                                TextButton(onClick = { VideoLibrary.clear() }) { Text("Svuota") }
                            }
                        }
                        items(recent, key = { it.uri }) { entry ->
                            RecentVideo(
                                entry = entry,
                                playing = entry.uri == current?.uri,
                                thumbnails = thumbnails,
                                onClick = { play(Uri.parse(entry.uri)) },
                                onRemove = { VideoLibrary.remove(entry.uri) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLinkDialog) {
        LinkDialog(
            onDismiss = { showLinkDialog = false },
            onConfirm = { link ->
                showLinkDialog = false
                val youtube = youtubeWatchUrl(link)
                if (youtube != null) onOpenYouTube(youtube) else play(Uri.parse(link.trim()))
            },
        )
    }
}

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private fun speedLabel(speed: Float) = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().replace('.', ',')) + "×"

@Composable
private fun PlayerOptions(
    speed: Float,
    onSpeed: (Float) -> Unit,
    subtitleName: String?,
    onPickSubtitles: () -> Unit,
) {
    var speedMenu by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            AssistChip(
                onClick = { speedMenu = true },
                label = { Text("Velocità ${speedLabel(speed)}") },
                leadingIcon = { Icon(Icons.Outlined.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                speeds.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(speedLabel(option)) },
                        onClick = {
                            speedMenu = false
                            onSpeed(option)
                        },
                    )
                }
            }
        }
        AssistChip(
            onClick = onPickSubtitles,
            label = { Text(subtitleName ?: "Sottotitoli", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun EmptyPlayer() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Scegli cosa guardare", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Video e musica del telefono, oppure un link: mp4, webm, streaming m3u8. " +
                    "I link di YouTube si aprono nel player YouTube senza pubblicità.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentVideo(
    entry: VideoEntry,
    playing: Boolean,
    thumbnails: ImageLoader,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (playing) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Box(
                        Modifier
                            .size(width = 80.dp, height = 45.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (entry.isLocal) Icons.Outlined.Movie else Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.isLocal) {
                            AsyncImage(
                                model = entry.uri,
                                imageLoader = thumbnails,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                },
                headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        when {
                            playing -> "In riproduzione"
                            entry.positionMs > 0 -> "Riprendi da ${formatDuration(entry.positionMs)}"
                            entry.isLocal -> "Dal telefono"
                            else -> Uri.parse(entry.uri).host.orEmpty()
                        },
                        maxLines = 1,
                    )
                },
                trailingContent = {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Outlined.Close, contentDescription = "Togli dai recenti")
                    }
                },
            )
            if (entry.progress > 0f) {
                LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
        title = { Text("Apri un link") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("https://…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.trim().startsWith("http"),
            ) { Text("Riproduci") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
