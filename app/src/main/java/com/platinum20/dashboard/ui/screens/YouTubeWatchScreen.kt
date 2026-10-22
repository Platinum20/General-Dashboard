package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.player.YtChapter
import com.platinum20.dashboard.media.youtube.player.YtComment
import com.platinum20.dashboard.media.youtube.player.YtNow
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtResolver
import com.platinum20.dashboard.media.youtube.player.YtStream
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Player di YouTube dell'app: il video arriva direttamente nel player di Android (niente sito,
 * niente pubblicità). Comandi nostri sul video (tocco, doppio tocco ±10 s, trascina giù per
 * rimpicciolire), sotto titolo, canale, qualità, velocità, capitoli, descrizione, commenti e i prossimi video.
 * Il player è condiviso con la bolla e la notifica, quindi il video continua uscendo da qui.
 */
@OptIn(UnstableApi::class)
@Composable
fun YouTubeWatchScreen(url: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val now by YtPlayer.now.collectAsState()
    val feed by YouTubeStore.subscriptions.collectAsState()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var showComments by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(url) { url?.let { YtPlayer.play(context, it) } }

    val currentId = now?.stream?.id ?: url?.let { YtResolver.videoId(it) }
    val upNext = feed.data.orEmpty().filter { !it.isShort && it.id != currentId }.take(30)
    // Finito un video parte il successivo della lista.
    LaunchedEffect(upNext.map { it.id }, currentId) {
        YtPlayer.queue = listOfNotNull(currentId?.let { "https://www.youtube.com/watch?v=$it" }) + upNext.map { it.watchUrl }
    }
    val loadedComments = now?.stream?.id?.takeIf { showComments }?.let { commentsFor(it) }

    val inPip = rememberPictureInPicture(enabled = now?.playing == true)
    FullscreenEffect(enabled = fullscreen && !inPip)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    val immersive = fullscreen || inPip
    Surface(color = if (immersive) Color.Black else MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(if (immersive) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding()) {
            VideoArea(
                now = now,
                fullscreen = fullscreen,
                inPip = inPip,
                onBack = onBack,
                onToggleFullscreen = { fullscreen = !fullscreen },
                onRetry = { (now?.link ?: url)?.let { YtPlayer.play(context, it) } },
                modifier = if (immersive) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            if (immersive) return@Column

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val stream = now?.stream
                item(key = "title") { TitleBlock(now, stream) }
                if (stream != null) {
                    item(key = "actions") {
                        Actions(stream, showComments, onToggleComments = { showComments = !showComments })
                    }
                    if (stream.description.isNotBlank()) item(key = "desc") { Description(stream) }
                    if (stream.chapters.size > 1) item(key = "chapters") { Chapters(stream.chapters) }
                    if (showComments) {
                        item(key = "comments-h") { SectionHeader("Commenti") }
                        val comments = loadedComments
                        when {
                            comments == null -> item(key = "comments-l") { CircularProgressIndicator(Modifier.size(24.dp)) }
                            comments.isEmpty() -> item(key = "comments-e") {
                                Text("Nessun commento (o commenti disattivati)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> items(comments.size, key = { "c$it" }) { CommentRow(comments[it]) }
                        }
                    }
                }
                if (upNext.isNotEmpty()) {
                    item(key = "next-h") { SectionHeader("Prossimi dai canali che segui") }
                    items(upNext, key = { it.id }) { video -> VideoRow(video, onClick = { YtPlayer.play(context, video.watchUrl) }) }
                }
            }
        }
    }
}

/**
 * Il video con i nostri comandi: tocco = mostra/nasconde, doppio tocco a sinistra/destra = ∓10 s,
 * trascinare giù = torna indietro lasciando il video nella bolla.
 */
@OptIn(UnstableApi::class)
@Composable
private fun VideoArea(
    now: YtNow?,
    fullscreen: Boolean,
    inPip: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player = remember { YtPlayer.player(context) }
    var controls by remember { mutableStateOf(true) }
    var seekFlash by remember { mutableIntStateOf(0) }
    var flashKey by remember { mutableIntStateOf(0) }
    var drag by remember { mutableFloatStateOf(0f) }
    val playing = now?.playing == true

    // I comandi spariscono da soli dopo 3 secondi mentre il video va.
    LaunchedEffect(controls, playing, flashKey) {
        if (controls && playing) {
            delay(3_000)
            controls = false
        }
    }
    LaunchedEffect(flashKey) {
        if (seekFlash != 0) {
            delay(600)
            seekFlash = 0
        }
    }

    Box(
        modifier
            .background(Color.Black)
            .offset { IntOffset(0, drag.roundToInt()) }
            .pointerInput(inPip) {
                if (inPip) return@pointerInput
                detectTapGestures(
                    onTap = { controls = !controls },
                    onDoubleTap = { offset ->
                        val forward = offset.x > size.width / 2
                        YtPlayer.seekBy(context, if (forward) 10_000 else -10_000)
                        seekFlash = if (forward) 1 else -1
                        flashKey++
                    },
                )
            }
            .pointerInput(fullscreen, inPip) {
                if (fullscreen || inPip) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (drag > 160f) onBack()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                    onVerticalDrag = { _, amount -> drag = (drag + amount).coerceAtLeast(0f) },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )

        val error = now?.error
        when {
            error != null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(error, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("Riprova") }
            }
            now?.loading != false -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(44.dp), color = Color.White)
        }

        // Il segno del doppio tocco: ±10 s sul lato toccato.
        if (seekFlash != 0) {
            Box(
                Modifier
                    .align(if (seekFlash > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (seekFlash > 0) "+10 s" else "−10 s", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        AnimatedVisibility(visible = controls && !inPip && error == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f))) {
                Row(Modifier.align(Alignment.TopStart).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (fullscreen) onToggleFullscreen() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                    }
                    if (fullscreen) {
                        Text(
                            now?.stream?.title.orEmpty(),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { YtPlayer.seekBy(context, -10_000); flashKey++ }) {
                        Icon(Icons.Filled.Replay10, contentDescription = "Indietro di 10 secondi", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { YtPlayer.toggle(context); flashKey++ },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pausa" else "Riproduci",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = { YtPlayer.seekBy(context, 10_000); flashKey++ }) {
                        Icon(Icons.Filled.Forward10, contentDescription = "Avanti di 10 secondi", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                IconButton(onClick = { YtPlayer.next(context) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Prossimo video", tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
                    TimeBar(live = now?.stream?.hlsUrl != null, onFullscreen = onToggleFullscreen, fullscreen = fullscreen, onInteract = { flashKey++ })
                }
            }
        }
    }
}

/** Minutaggio sul video: tempo trascorso, durata, barra da trascinare e schermo intero. */
@OptIn(UnstableApi::class)
@Composable
private fun TimeBar(live: Boolean, fullscreen: Boolean, onFullscreen: () -> Unit, onInteract: () -> Unit) {
    val context = LocalContext.current
    val player = remember { YtPlayer.player(context) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (live) {
            Text("● IN DIRETTA", color = Color(0xFFFF4D4D), style = MaterialTheme.typography.labelLarge)
        } else {
            val shown = dragging?.let { (it * duration).toLong() } ?: position
            Text(
                "${formatDuration(shown)} / ${formatDuration(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onFullscreen) {
            Icon(
                if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (fullscreen) "Esci da schermo intero" else "Schermo intero",
                tint = Color.White,
            )
        }
    }
    if (!live) {
        Slider(
            value = dragging ?: if (duration > 0) position.toFloat() / duration else 0f,
            onValueChange = {
                dragging = it
                onInteract()
            },
            onValueChangeFinished = {
                dragging?.let { YtPlayer.seekTo(context, (it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF2D2D),
                activeTrackColor = Color(0xFFFF2D2D),
                inactiveTrackColor = Color.White.copy(alpha = if (buffered > position) 0.45f else 0.3f),
            ),
            modifier = Modifier.height(28.dp).offset(y = (-4).dp),
        )
    }
}

@Composable
private fun TitleBlock(now: YtNow?, stream: YtStream?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stream?.title ?: if (now?.error != null) "Video non disponibile" else "Caricamento…",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (stream == null) return@Column
        Text(
            listOfNotNull(
                stream.views.takeIf { it >= 0 }?.let { "${compactCount(it)} visualizzazioni" },
                stream.uploaded.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stream.channel.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelAvatar(stream.channelAvatar, stream.channel, 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stream.channel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    stream.subscribers.takeIf { it >= 0 }?.let {
                        Text(
                            "${compactCount(it)} iscritti",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                stream.likes.takeIf { it > 0 }?.let { likes ->
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.card)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(compactCount(likes), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Qualità, velocità e commenti: scelte rapide sotto il video. */
@Composable
private fun Actions(stream: YtStream, showComments: Boolean, onToggleComments: () -> Unit) {
    val context = LocalContext.current
    var qualityMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(YtPlayer.speed()) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stream.qualities.isNotEmpty()) item {
            Box {
                CanvasChip(selected = false, onClick = { qualityMenu = true }, label = "Qualità · ${stream.height}p")
                DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                    stream.qualities.forEach { q ->
                        DropdownMenuItem(
                            text = { Text("${q.height}p" + if (q.height == stream.height) "  ✓" else "") },
                            onClick = {
                                qualityMenu = false
                                YtPlayer.setQuality(context, q.height)
                            },
                        )
                    }
                }
            }
        }
        item {
            Box {
                CanvasChip(selected = speed != 1f, onClick = { speedMenu = true }, label = "Velocità · ${speedLabel(speed)}")
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    speeds.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(speedLabel(s) + if (s == speed) "  ✓" else "") },
                            onClick = {
                                speedMenu = false
                                speed = s
                                YtPlayer.setSpeed(context, s)
                            },
                        )
                    }
                }
            }
        }
        item { CanvasChip(selected = showComments, onClick = onToggleComments, label = "Commenti") }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == 1f) "normale" else (if (speed % 1f == 0f) "${speed.toInt()}" else speed.toString().replace('.', ',')) + "×"

/** Descrizione in una card: tre righe, tocca per leggerla tutta. */
@Composable
private fun Description(stream: YtStream) {
    var expanded by rememberSaveable(stream.id) { mutableStateOf(false) }
    CanvasCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(Modifier.padding(14.dp)) {
            Text(
                stream.description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!expanded) {
                Text("altro", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Chapters(chapters: List<YtChapter>) {
    val context = LocalContext.current
    Column {
        SectionHeader("Capitoli · ${chapters.size}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chapters.size) { i ->
                val chapter = chapters[i]
                CanvasCard(
                    Modifier.width(168.dp).bouncyPress(0.96f),
                    onClick = { YtPlayer.seekTo(context, chapter.startSec * 1000L) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            formatDuration(chapter.startSec * 1000L),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(chapter.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private val commentCache = mutableStateMapOf<String, List<YtComment>>()

/** Commenti del video, caricati la prima volta che si aprono (null mentre arrivano). */
@Composable
private fun commentsFor(id: String): List<YtComment>? {
    LaunchedEffect(id) {
        if (id !in commentCache) commentCache[id] = runCatching { YtResolver.comments(id) }.getOrDefault(emptyList())
    }
    return commentCache[id]
}

@Composable
private fun CommentRow(comment: YtComment) {
    Row {
        ChannelAvatar(comment.avatar, comment.author.removePrefix("@"), 32)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOf(comment.author, comment.date).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            val extra = listOfNotNull(
                comment.likes.takeIf { it > 0 }?.let { "👍 ${compactCount(it.toLong())}" },
                comment.replies.takeIf { it > 0 }?.let { "$it risposte" },
            )
            if (extra.isNotEmpty()) {
                Text(extra.joinToString("   "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 1234 → "1234", 15300 → "15,3K", 2100000 → "2,1 Mln", 1700000000 → "1,7 Mld". */
private fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1f Mld".format(java.util.Locale.ITALY, n / 1_000_000_000.0).replace(",0 ", " ")
    n >= 1_000_000 -> "%.1f Mln".format(java.util.Locale.ITALY, n / 1_000_000.0).replace(",0 ", " ")
    n >= 10_000 -> "%.1fK".format(java.util.Locale.ITALY, n / 1000.0).replace(",0K", "K")
    else -> n.toString()
}
