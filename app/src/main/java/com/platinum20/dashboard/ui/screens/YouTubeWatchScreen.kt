package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtResolver

/**
 * Player di YouTube dell'app: il video arriva direttamente nel player di Android (niente pagina
 * del sito, niente pubblicità). Sotto, titolo, canale e i prossimi video dai canali che segui.
 * Il player è condiviso con la bolla e la notifica, quindi il video continua uscendo da qui.
 */
@OptIn(UnstableApi::class)
@Composable
fun YouTubeWatchScreen(url: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val now by YtPlayer.now.collectAsState()
    val feed by YouTubeStore.subscriptions.collectAsState()
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(url) { url?.let { YtPlayer.play(context, it) } }

    val currentId = now?.stream?.id ?: url?.let { YtResolver.videoId(it) }
    val upNext = feed.data.orEmpty().filter { !it.isShort && it.id != currentId }.take(30)
    // Finito un video parte il successivo della lista.
    LaunchedEffect(upNext.map { it.id }, currentId) {
        YtPlayer.queue = listOfNotNull(currentId?.let { "https://www.youtube.com/watch?v=$it" }) + upNext.map { it.watchUrl }
    }

    val inPip = rememberPictureInPicture(enabled = now?.playing == true)
    FullscreenEffect(enabled = fullscreen && !inPip)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    Surface(color = if (fullscreen || inPip) Color.Black else MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(if (fullscreen || inPip) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                (if (fullscreen || inPip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = YtPlayer.player(ctx)
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            setFullscreenButtonClickListener { fullscreen = it }
                        }
                    },
                    update = { it.useController = !inPip },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
                if (now?.loading == true && now?.error == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
                }
                if (!fullscreen && !inPip) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                    }
                }
            }
            if (fullscreen || inPip) return@Column

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    val stream = now?.stream
                    Text(
                        stream?.title ?: if (now?.error != null) "Video non disponibile" else "Caricamento…",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!stream?.channel.isNullOrBlank()) {
                        Text(
                            stream!!.channel + if (stream.height > 0) " · ${stream.height}p" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    now?.error?.let { error ->
                        Spacer(Modifier.size(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start)
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = { YtPlayer.play(context, now?.link ?: url ?: return@Button) }) { Text("Riprova") }
                    }
                }
                if (upNext.isNotEmpty()) {
                    item { Text("Prossimi dai canali che segui", style = MaterialTheme.typography.titleMedium) }
                    items(upNext, key = { it.id }) { video -> UpNextRow(video, onClick = { YtPlayer.play(context, video.watchUrl) }) }
                }
            }
        }
    }
}

@Composable
private fun UpNextRow(video: YtVideo, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
