package com.platinum20.dashboard.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.MediaLauncher
import com.platinum20.dashboard.media.PhoneVideo
import com.platinum20.dashboard.media.PhoneVideos
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.video.VideoFrameDecoder
import com.platinum20.dashboard.media.VideoEntry
import com.platinum20.dashboard.media.VideoLibrary
import com.platinum20.dashboard.media.youtubeWatchUrl

/**
 * Video e audio del telefono o da un link (mp4, webm, m3u8...). Qui si sceglie soltanto: suonano nel
 * player unico, con la stessa coda e la stessa notifica di tutto il resto.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    onOpenYouTube: (String) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    VideoLibrary.init(context)
    val recent by VideoLibrary.recent.collectAsStateWithLifecycle()
    val now by CanvasPlayer.now.collectAsStateWithLifecycle()
    var showLinkDialog by remember { mutableStateOf(false) }

    val thumbnails = remember {
        ImageLoader.Builder(context).components { add(VideoFrameDecoder.Factory()) }.build()
    }

    val play = { uri: Uri ->
        MediaLauncher.playVideo(context, uri)
        onOpenPlayer()
    }
    // Video del telefono: si leggono solo dopo il tocco su "Mostra i video del telefono".
    var phoneAllowed by remember { mutableStateOf(PhoneVideos.hasPermission(context)) }
    var phoneVideos by remember { mutableStateOf<List<PhoneVideo>?>(null) }
    // Dopo due "Non consentire" Android non chiede più: la card allora porta alle impostazioni dell'app.
    var phoneInvite by remember { mutableStateOf(PhoneVideos.Invite.ASK) }
    val phonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        phoneAllowed = granted
        val activity = context.findActivity()
        val rationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, PhoneVideos.permission)
        phoneInvite = PhoneVideos.invite(deniedOnce = !granted, rationale = rationale)
    }
    // A ogni ritorno nella pagina si rileggono i video: quelli appena registrati compaiono subito.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        phoneAllowed = PhoneVideos.hasPermission(context)
        resumes++
    }
    LaunchedEffect(phoneAllowed, resumes) { phoneVideos = if (phoneAllowed) PhoneVideos.load(context) else null }
    val phoneShown = remember(phoneVideos, recent) { PhoneVideos.forLibrary(phoneVideos.orEmpty(), recent) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(play)
    }
    Scaffold(
        topBar = { TopLevelBar(title = "Sul telefono", subtitle = "File, link e video registrati", onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            run {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Un solo posto per aprire qualcosa: un file (anche audio) o un link.
                    item {
                        OpenSources(
                            onPickFile = { picker.launch(arrayOf("video/*", "audio/*")) },
                            onOpenLink = { showLinkDialog = true },
                        )
                    }
                    if (recent.isNotEmpty()) {
                        item {
                            SectionHeader("Recenti", modifier = Modifier.padding(top = 4.dp), action = "Svuota", onAction = { VideoLibrary.clear() })
                        }
                        items(recent, key = { it.uri }) { entry ->
                            RecentVideo(
                                entry = entry,
                                playing = entry.uri == now?.item?.key,
                                thumbnails = thumbnails,
                                onClick = { play(Uri.parse(entry.uri)) },
                                onRemove = { VideoLibrary.remove(entry.uri) },
                            )
                        }
                    }
                    item { SectionHeader("Sul telefono", modifier = Modifier.padding(top = 4.dp)) }
                    when {
                        !phoneAllowed -> item {
                            PhoneVideosInvite(
                                denied = phoneInvite == PhoneVideos.Invite.OPEN_SETTINGS,
                                onClick = {
                                    if (phoneInvite == PhoneVideos.Invite.OPEN_SETTINGS) {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    } else {
                                        phonePermission.launch(PhoneVideos.permission)
                                    }
                                },
                            )
                        }
                        phoneVideos != null && phoneShown.isEmpty() -> item {
                            Text(
                                if (phoneVideos.isNullOrEmpty()) "Nessun video sul telefono" else "Sono tutti nei Recenti",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        else -> items(phoneShown.chunked(2), key = { row -> row.first().uri }) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { video ->
                                    PhoneVideoTile(
                                        video = video,
                                        thumbnails = thumbnails,
                                        onClick = { play(Uri.parse(video.uri)) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
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

/** Le due strade per aprire un video che non è nella lista: un file scelto a mano o un link. */
@Composable
private fun OpenSources(onPickFile: () -> Unit, onOpenLink: () -> Unit) {
    CanvasCard(Modifier.fillMaxWidth()) {
        OpenSourceRow(Icons.Outlined.FolderOpen, TileAccent.BLUE, "Scegli un file", "Video o musica, anche dalle cartelle", onPickFile)
        RowDivider()
        OpenSourceRow(Icons.Outlined.Link, TileAccent.ORANGE, "Apri un link", "mp4, streaming; YouTube va nel suo player", onOpenLink)
    }
}

@Composable
private fun OpenSourceRow(icon: ImageVector, accent: TileAccent, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, accent, size = 36.dp, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Invito a mostrare i video del telefono: il permesso si chiede solo da qui. */
@Composable
private fun PhoneVideosInvite(denied: Boolean, onClick: () -> Unit) {
    CanvasCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.VideoLibrary, TileAccent.PURPLE)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (denied) "Permesso negato" else "Mostra i video del telefono", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (denied) "Tocca per aprire le impostazioni di Canvas e consentire i video."
                    else "Registrazioni e download, a portata di tocco. Restano sul telefono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Miniatura di un video del telefono con durata e giorno. */
@Composable
private fun PhoneVideoTile(video: PhoneVideo, thumbnails: ImageLoader, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CanvasCard(modifier.bouncyPress(), onClick = onClick) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(
                model = video.uri,
                imageLoader = thumbnails,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(video.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                PhoneVideos.caption(video),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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
        color = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.card,
        border = if (playing) null else cardBorder(),
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
    CanvasDialog(
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
