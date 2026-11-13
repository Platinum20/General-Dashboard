package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.outlined.SwipeDown
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.VideoLibrary
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.Resume
import com.platinum20.dashboard.media.MediaHistory
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.capture.SavedLink
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.Playing
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun sourceIcon(source: PlaySource): ImageVector = when (source) {
    PlaySource.VIDEO -> Icons.Outlined.OndemandVideo
    PlaySource.MUSIC -> Icons.Outlined.MusicNote
    PlaySource.SPOTIFY -> Icons.Outlined.GraphicEq
    PlaySource.RADIO -> Icons.Outlined.Radio
    PlaySource.PODCAST -> Icons.Outlined.Mic
    PlaySource.LOCAL -> Icons.Outlined.OndemandVideo
    PlaySource.SPEECH -> Icons.Outlined.Headphones
}

internal fun sourceAccent(source: PlaySource): TileAccent = when (source) {
    PlaySource.VIDEO, PlaySource.LOCAL -> TileAccent.ORANGE
    PlaySource.MUSIC, PlaySource.SPOTIFY -> TileAccent.GREEN
    PlaySource.RADIO, PlaySource.PODCAST -> TileAccent.PURPLE
    PlaySource.SPEECH -> TileAccent.BLUE
}

/**
 * Il player unico, per qualunque cosa suoni. Se la voce della coda ha un video di YouTube la pagina
 * è quella del video (con titolo, descrizione, commenti); se no copertina, barra del tempo e comandi.
 * Passando da un video a una canzone nella coda la pagina cambia da sola, senza cambiare schermata.
 * [startUrl]: un video di YouTube da far partire aprendo la pagina. La coda è un pop-up sopra ([QueuePopup]).
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    startUrl: String? = null,
    onOpenChannel: (com.platinum20.dashboard.media.youtube.YtChannel) -> Unit = {},
) {
    val context = LocalContext.current
    val now by Playback.now.collectAsState()
    var starting by remember { mutableStateOf(startUrl != null) }
    LaunchedEffect(startUrl) {
        startUrl?.let { YtPlayer.play(context, it) }
        starting = false
    }
    // Niente pulsante per chiudere (regola di Antonio): la prima volta che suona qualcosa una riga spiega i gesti.
    val hasNow = now != null
    LaunchedEffect(hasNow) {
        if (hasNow && !GestureHints.seen(context, PLAYER_GESTURES_HINT)) {
            GestureHints.mark(context, PLAYER_GESTURES_HINT)
            kotlinx.coroutines.delay(700)
            CaptureFeedback.tell(context, PLAYER_GESTURES_TEXT, Icons.Outlined.SwipeDown)
        }
    }
    // La coda si apre sopra il player, non in un'altra pagina: il video resta a schermo intero e in orizzontale.
    val onOpenQueue = { QueueOpen.open = true }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        if (starting || now?.source == PlaySource.VIDEO) {
            YouTubeWatchScreen(url = null, onBack = onBack, onOpenQueue = onOpenQueue, onOpenChannel = onOpenChannel)
        } else {
            AudioPlayer(onBack, onOpenQueue)
        }
        // Il fumetto delle impostazioni e la coda stanno fuori dal NavHost (vetro): uscendo dal player si chiudono.
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { VideoSettings.open = false; QueueOpen.open = false } }
    }
}

/**
 * Musica (Spotify e YouTube Music), radio, podcast, video del telefono e il punto del giorno:
 * copertina (o il video), barra del tempo e comandi. Niente tasti per uscire: trascinato giù si
 * riduce al mini player, lanciato di lato (come il mini player) si chiude.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioPlayer(onBack: () -> Unit, onOpenQueue: () -> Unit) {
    val context = LocalContext.current
    val now by Playback.now.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    // Il punto del giorno non ha copertina da sfocare: dietro, i colori della sua.
    if (now?.source == PlaySource.SPEECH) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(
                Brush.verticalGradient(listOf(Color(0xFF3550E8).copy(alpha = 0.35f), MaterialTheme.colorScheme.background))
            )
        )
    }
    // Dietro a tutto la copertina sfocata, che sfuma nello sfondo: il player prende i colori di quello che suona.
    now?.artwork?.takeIf { it.isNotBlank() }?.let { art ->
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).blur(72.dp).alpha(0.55f),
        )
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
            )
        )
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopLevelBar(
                title = "In ascolto",
                subtitle = now?.source?.label?.uppercase(),
                onBack = onBack,
                transparent = true,
            )
        },
    ) { inner ->
        val current = now
        if (current == null) {
            // Il player vuoto non è un vicolo cieco: "Riprendi" con le ultime cose, come in Media (punto 43).
            remember { MediaHistory.init(context); VideoLibrary.init(context) }
            val history by MediaHistory.items.collectAsState()
            val videos by VideoLibrary.recent.collectAsState()
            val resumable = remember(history, videos) { Resume.pick(history + videos.map(ResumeItem::fromVideo)).take(3) }
            Column(
                Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                Text("Non sta suonando niente", style = MaterialTheme.typography.titleMedium)
                if (resumable.isEmpty()) {
                    Text(
                        "Scegli musica, un video, una radio o un podcast da Media.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                // Parte qui, nel player stesso: la pagina diventa quella di quello che suona.
                resumable.forEachIndexed { i, item ->
                    MediaResumeCard(
                        item,
                        onClick = { com.platinum20.dashboard.media.MediaLauncher.start(context, item) },
                        modifier = Modifier.fillMaxWidth(),
                        hero = i == 0,
                    )
                }
            }
            return@Scaffold
        }
        // Tutta la pagina segue il dito: giù oltre un quarto (o con uno scatto) torna al mini player,
        // di lato oltre metà si chiude. Su e di lato insieme, si decide solo lasciandola.
        val down = (drag.value.y / h).coerceIn(0f, 1f)
        val side = (kotlin.math.abs(drag.value.x) / w).coerceIn(0f, 1f)
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .graphicsLayer {
                    translationX = drag.value.x
                    translationY = drag.value.y
                    val sc = 1f - 0.08f * down
                    scaleX = sc
                    scaleY = sc
                    alpha = 1f - 0.6f * side
                }
                .pointerInput(w, h) {
                    val tracker = VelocityTracker()
                    var total = Offset.Zero
                    var travelled = Offset.Zero
                    var armed = false
                    detectDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            total = drag.value
                            travelled = Offset.Zero
                            armed = false
                        },
                        onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, Motion.gesture()) } },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            when (playerRelease(total, Offset(v.x, v.y), w, h)) {
                                PlayerRelease.CLOSE -> {
                                    view.haptic(Haptic.CONFIRM)
                                    scope.launch {
                                        drag.animateTo(Offset(kotlin.math.sign(total.x) * w * 1.2f, total.y), Motion.fling(w * 1.2f - kotlin.math.abs(total.x), v.x))
                                        Playback.stop()
                                        onBack()
                                    }
                                }
                                PlayerRelease.MINI -> {
                                    view.haptic(Haptic.SNAP)
                                    onBack()
                                }
                                PlayerRelease.QUEUE -> {
                                    view.haptic(Haptic.SNAP)
                                    scope.launch { drag.animateTo(Offset.Zero, Motion.gesture()) }
                                    onOpenQueue()
                                }
                                PlayerRelease.STAY -> scope.launch { drag.animateTo(Offset.Zero, Motion.gesture(), initialVelocity = v.let { Offset(it.x, it.y) }) }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        travelled += amount
                        tracker.addPosition(change.uptimeMillis, travelled)
                        // In su resiste, e lasciandola apre la coda.
                        total = Offset(total.x + amount.x, (total.y + amount.y).coerceAtLeast(-h * 0.05f))
                        val over = kotlin.math.abs(total.x) > w * 0.5f
                        if (over != armed) {
                            armed = over
                            view.haptic(if (over) Haptic.THRESHOLD else Haptic.TICK)
                        }
                        val target = total
                        scope.launch { drag.snapTo(target) }
                    }
                }
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // La maniglia in alto dice che la pagina si trascina giù.
            Box(
                Modifier
                    .padding(bottom = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            // Il testo (1.491): si apre e si chiude toccando la copertina o "Testo"; da solo col volume a zero (Antonio).
            val lyrics = rememberLyrics(current)
            var showLyrics by remember(current.title) { mutableStateOf(false) }
            remember { com.platinum20.dashboard.media.MediaVolume.init(context) }
            val muted by com.platinum20.dashboard.media.MediaVolume.muted.collectAsState()
            LaunchedEffect(muted, lyrics != null) { if (lyrics != null) showLyrics = muted }
            Artwork(
                current, Modifier.weight(1f, fill = false),
                lyrics = lyrics?.takeIf { showLyrics },
                onTap = if (lyrics != null) ({ showLyrics = !showLyrics }) else null,
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
                SourcePill(current.source)
                Spacer(Modifier.height(8.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (showPlayerSubtitle(current.live, current.subtitle)) Text(
                    current.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                TimeBar(current)
                Spacer(Modifier.height(8.dp))
                Controls(current)
                Spacer(Modifier.height(8.dp))
                // La coda unica (cosa viene dopo, di qualunque fonte): anche trascinando la pagina in su.
                val queue by com.platinum20.dashboard.media.CanvasPlayer.queue.collectAsState()
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasButton(
                        if (queue.upcoming.isEmpty()) "Coda" else "Coda · ${queue.upcoming.size} dopo",
                        onClick = onOpenQueue,
                        icon = Icons.Outlined.QueueMusic,
                    )
                    if (lyrics != null) CanvasButton(
                        if (showLyrics) "Copertina" else "Testo",
                        onClick = { showLyrics = !showLyrics },
                        icon = Icons.AutoMirrored.Outlined.Notes,
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (current.source == PlaySource.SPEECH) SpeechChapters()
                // La musica di YouTube suona in solo audio: da qui si passa al video, dallo stesso punto.
                if (current.source == PlaySource.MUSIC) {
                    YtPlayer.now.value?.link?.let { link ->
                        CanvasButton("Guarda il video", onClick = { YtPlayer.play(context, link) }, icon = Icons.Outlined.OndemandVideo)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    }
}

/** "In diretta" lo dice già la riga del tempo, sotto il titolo: non due volte. */
internal fun showPlayerSubtitle(live: Boolean, subtitle: String): Boolean =
    !(live && subtitle.trim().equals("In diretta", ignoreCase = true))

/** Cosa fa la pagina del player quando la lasci dopo averla trascinata. */
internal enum class PlayerRelease { CLOSE, MINI, QUEUE, STAY }

/** Di lato oltre metà chiude, giù oltre un quarto (o uno scatto) torna al mini player, su (o uno scatto in su) apre la coda. */
internal fun playerRelease(total: Offset, velocity: Offset, w: Float, h: Float): PlayerRelease = when {
    kotlin.math.abs(total.x) > w * 0.5f -> PlayerRelease.CLOSE
    total.y > h * 0.25f || (velocity.y > 1_500f && velocity.y > 1.5f * kotlin.math.abs(velocity.x) && total.y > 0f) -> PlayerRelease.MINI
    total.y < -h * 0.04f || (velocity.y < -1_500f && -velocity.y > 1.5f * kotlin.math.abs(velocity.x) && total.y < 0f) -> PlayerRelease.QUEUE
    else -> PlayerRelease.STAY
}

/** I timestamp della lettura: di cosa parla e da quando; toccandone uno la voce salta lì. */
@Composable
private fun SpeechChapters() {
    val speech by com.platinum20.dashboard.news.RecapSpeaker.state.collectAsState()
    val chapters = speech?.chapters.orEmpty()
    if (chapters.size < 2) return
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        chapters.forEachIndexed { i, chapter ->
            val here = speech?.part == i
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { Playback.seekTo(chapter.startMs) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    clockLabel(chapter.startMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    chapter.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (here) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Da dove arriva quello che suona, con l'icona e il colore della sezione. */
@Composable
private fun SourcePill(source: PlaySource) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sourceIcon(source), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(source.label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Artwork(now: Playing, modifier: Modifier = Modifier, lyrics: com.platinum20.dashboard.media.lyrics.SongLyrics? = null, onTap: (() -> Unit)? = null) {
    val tap by rememberUpdatedState(onTap)
    Box(
        modifier
            .widthIn(max = 420.dp)
            // Quadrata, e più piccola se lo schermo è basso: tutto il player sta in una pagina.
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .shadow(24.dp, MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Tenendo premuta la copertina: "Salva nelle note", per riaprirla poi ferma.
            .pointerInput(onTap != null) {
                detectTapGestures(
                    onTap = { tap?.invoke() },
                    onLongPress = { Playback.currentAsItem()?.let { SavedLinks.offer(SavedLink.Media(it)) } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        IconTile(sourceIcon(now.source), sourceAccent(now.source), size = 96.dp, iconSize = 48.dp)
        if (now.isVideo) VideoSurface(Modifier.fillMaxSize())
        else if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.fillMaxSize())
        else if (now.artwork.isNotBlank()) {
            AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (lyrics != null && !now.isVideo) LyricsPanel(now, lyrics)
        if (now.loading) LoadingPulse(size = 14.dp)
    }
}

/**
 * La barra del tempo, sottile come quella dei video di YouTube: linea di 4dp e pallino piccolo.
 * C'è anche mentre il brano dopo si carica (a zero), così dopo uno skip non sparisce e ricompare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeBar(now: Playing) {
    if (now.live) {
        Text(
            "● In diretta",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
        return
    }
    // Mentre si trascina la barra segue il dito; lasciata, il player salta lì.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = now.durationMs
    val position = livePosition(now)
    val accent = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    // Un tocco non sposta il brano: si va avanti o indietro solo trascinando di lato.
    val value = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    SeekBar(
        value = value,
        enabled = now.canSeek,
        onSeeking = { dragging = it },
        onSeekEnd = { commit ->
            if (commit) dragging?.let { Playback.seekTo((it * duration).toLong()) }
            dragging = null
        },
        thumb = {
            SeekDot(if (now.canSeek) accent else rest, if (dragging != null) 16.dp else 12.dp)
        },
        track = {
            SeekTrack {
                drawRoundRect(rest, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                drawRoundRect(
                    accent,
                    size = androidx.compose.ui.geometry.Size(value.coerceIn(0f, 1f) * size.width, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            }
        },
        modifier = Modifier.height(SEEK_HEIGHT),
    )
    Row {
        Text(clockLabel(dragging?.let { (it * duration).toLong() } ?: position), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(if (duration > 0) clockLabel(duration) else "–:––", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Controls(now: Playing) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Il podcast ha anche i classici -10 e +30 secondi; avanti e indietro vanno nella coda.
        val podcast = now.source == PlaySource.PODCAST && now.canSeek
        if (podcast) {
            IconButton(onClick = { Playback.seekBy(-10_000) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Replay10, contentDescription = "Indietro di 10 secondi", modifier = Modifier.size(26.dp))
            }
        }
        IconButton(onClick = { Playback.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Indietro", modifier = Modifier.size(32.dp))
        }
        FilledIconButton(
            onClick = { Playback.toggle() },
            modifier = Modifier.size(76.dp).bouncyPress(),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(
                if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (now.playing) "Pausa" else "Riproduci",
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = { Playback.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Avanti", modifier = Modifier.size(32.dp))
        }
        if (podcast) {
            IconButton(onClick = { Playback.seekBy(30_000) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Forward30, contentDescription = "Avanti di 30 secondi", modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Il video del motore (i video del telefono): stesso player, si vede solo qui. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoSurface(modifier: Modifier) {
    val context = LocalContext.current
    androidx.compose.ui.viewinterop.AndroidView(
        factory = {
            androidx.media3.ui.PlayerView(it).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = com.platinum20.dashboard.media.CanvasPlayer.player(context) },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

/**
 * Dove si è arrivati, che avanza da sola tra un aggiornamento della fonte e l'altro: la barra
 * scorre fluida anche quando la fonte dà la posizione di rado (Spotify su un altro dispositivo).
 */
@Composable
internal fun livePosition(now: Playing): Long {
    val latest by rememberUpdatedState(now)
    var anchor by remember { mutableStateOf(now.positionMs to System.currentTimeMillis()) }
    LaunchedEffect(now.positionMs, now.title, now.playing) { anchor = now.positionMs to System.currentTimeMillis() }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(now.playing) {
        while (latest.playing) {
            delay(250)
            clock = System.currentTimeMillis()
        }
    }
    val elapsed = if (now.playing && !now.loading) (clock - anchor.second).coerceAtLeast(0) else 0
    val end = now.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
    return (anchor.first + elapsed).coerceIn(0, end)
}

/** Il suggerimento dei gesti del player, una volta sola (punto 44). */
private const val PLAYER_GESTURES_HINT = "player_gestures"
internal const val PLAYER_GESTURES_TEXT = "Trascina giù per il mini player, di lato per chiudere"
