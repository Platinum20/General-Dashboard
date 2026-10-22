package com.platinum20.dashboard.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.NowPlaying
import com.platinum20.dashboard.media.PlayerCommand
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtVideo
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Riproduzione di un video di YouTube come schermata dell'app: dalla pagina del sito si mostra
 * solo il video, mentre titolo, comandi e video successivi sono dell'app. La pagina resta quella
 * condivisa con la bolla e la notifica, quindi il video continua anche uscendo da qui.
 */
@Composable
fun YouTubeWatchScreen(url: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    var current by rememberSaveable { mutableStateOf(url) }
    val webView = remember { MediaWebViews.obtain(context, MediaService.YOUTUBE, url) }
    val hostParent = remember { arrayOfNulls<android.view.ViewParent>(1) }
    val tracks by NowPlaying.tracks.collectAsState()
    val track = tracks[MediaService.YOUTUBE]
    val feed by YouTubeStore.subscriptions.collectAsState()
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(0f) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { MediaWebViews.detach(MediaService.YOUTUBE, hostParent[0]) }
    }
    // Solo il video, senza il resto della pagina: lo stesso stile della bolla, a tutta larghezza.
    LaunchedEffect(Unit) {
        while (true) {
            MediaWebViews.setMiniMode(MediaService.YOUTUBE, true)
            webView.evaluateJavascript(PROGRESS_SCRIPT) { raw ->
                val json = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull() ?: return@evaluateJavascript
                position = json.optDouble("t", 0.0).toFloat()
                duration = json.optDouble("d", 0.0).toFloat().takeIf { !it.isNaN() && it.isFinite() } ?: 0f
            }
            delay(500)
        }
    }

    FullscreenEffect(enabled = fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    fun play(video: YtVideo) {
        current = video.watchUrl
        webView.loadUrl(video.watchUrl)
    }

    val upNext = feed.data.orEmpty().filter { !it.isShort && current?.contains(it.id) != true }.take(30)

    Surface(color = if (fullscreen) Color.Black else MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding()) {
            Box(
                (if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView
                    },
                    update = { hostParent[0] = it.parent },
                    modifier = Modifier.fillMaxSize(),
                )
                if (!fullscreen) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                    }
                }
                IconButton(onClick = { fullscreen = !fullscreen }, modifier = Modifier.align(Alignment.BottomEnd)) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (fullscreen) "Esci da schermo intero" else "Schermo intero",
                        tint = Color.White,
                    )
                }
            }
            if (fullscreen) return@Column

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        track?.title ?: "Caricamento…",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!track?.artist.isNullOrBlank()) {
                        Text(track!!.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Column {
                        Slider(
                            value = seeking ?: position,
                            onValueChange = { seeking = it },
                            onValueChangeFinished = {
                                seeking?.let { webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=$it;})()", null) }
                                seeking = null
                            },
                            valueRange = 0f..maxOf(duration, 1f),
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatTime(seeking ?: position), style = MaterialTheme.typography.labelSmall)
                            Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { seekBy(webView, -10) }) { Icon(Icons.Filled.Replay10, contentDescription = "Indietro di 10 secondi") }
                            Spacer(Modifier.width(16.dp))
                            FilledIconButton(onClick = { NowPlaying.command(MediaService.YOUTUBE, PlayerCommand.TOGGLE) }) {
                                val playing = track?.playing == true
                                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pausa" else "Riproduci")
                            }
                            Spacer(Modifier.width(16.dp))
                            IconButton(onClick = { seekBy(webView, 10) }) { Icon(Icons.Filled.Forward10, contentDescription = "Avanti di 10 secondi") }
                            Spacer(Modifier.width(16.dp))
                            IconButton(enabled = upNext.isNotEmpty(), onClick = { upNext.firstOrNull()?.let(::play) }) {
                                Icon(Icons.Filled.SkipNext, contentDescription = "Prossimo video")
                            }
                        }
                    }
                }
                if (upNext.isNotEmpty()) {
                    item { Text("Prossimi dai canali che segui", style = MaterialTheme.typography.titleMedium) }
                    items(upNext, key = { it.id }) { video -> UpNextRow(video, onClick = { play(video) }) }
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

private fun seekBy(webView: android.webkit.WebView, seconds: Int) {
    webView.evaluateJavascript("(function(){var v=document.querySelector('video');if(v)v.currentTime=Math.max(0,v.currentTime+($seconds));})()", null)
}

private fun formatTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private const val PROGRESS_SCRIPT =
    "(function(){var v=document.querySelector('video');return v?JSON.stringify({t:v.currentTime,d:v.duration}):'{}';})()"
