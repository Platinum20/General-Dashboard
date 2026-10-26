package com.platinum20.dashboard.ui.screens

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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
    PlaySource.SPEECH -> Icons.Outlined.Headphones
}

internal fun sourceAccent(source: PlaySource): TileAccent = when (source) {
    PlaySource.VIDEO -> TileAccent.ORANGE
    PlaySource.MUSIC, PlaySource.SPOTIFY -> TileAccent.GREEN
    PlaySource.RADIO, PlaySource.PODCAST -> TileAccent.PURPLE
    PlaySource.SPEECH -> TileAccent.BLUE
}

/**
 * Il player grande, uguale per musica (Spotify e YouTube Music), radio, podcast e il punto del giorno:
 * copertina, barra del tempo e comandi. Niente tasti per uscire: trascinato giù si riduce al mini player,
 * lanciato di lato (come il mini player) si chiude. I video di YouTube hanno il loro, con il video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, onWatchVideo: (String) -> Unit) {
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
            Column(
                Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Non sta suonando niente", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Scegli musica, un video, una radio o un podcast da Media.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
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
                        onDragCancel = { scope.launch { drag.animateTo(Offset.Zero, Springs.bouncy()) } },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            when {
                                kotlin.math.abs(total.x) > w * 0.5f -> {
                                    view.haptic(Haptic.CONFIRM)
                                    scope.launch {
                                        drag.animateTo(Offset(kotlin.math.sign(total.x) * w * 1.2f, total.y), androidx.compose.animation.core.tween(200))
                                        Playback.stop(context)
                                        onBack()
                                    }
                                }
                                total.y > h * 0.25f || (v.y > 1_500f && v.y > 1.5f * kotlin.math.abs(v.x) && total.y > 0f) -> {
                                    view.haptic(Haptic.SNAP)
                                    onBack()
                                }
                                else -> scope.launch { drag.animateTo(Offset.Zero, Springs.bouncy(), initialVelocity = v.let { Offset(it.x, it.y) }) }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        travelled += amount
                        tracker.addPosition(change.uptimeMillis, travelled)
                        // In su resiste: non porta da nessuna parte.
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
            Artwork(current, Modifier.weight(1f, fill = false))
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
                SourcePill(current.source)
                Spacer(Modifier.height(8.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
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
                Spacer(Modifier.height(16.dp))
                // La musica di YouTube suona in solo audio: da qui si passa al video, dallo stesso punto.
                if (current.source == PlaySource.MUSIC) {
                    YtPlayer.now.value?.link?.let { link ->
                        OutlinedButton(onClick = { onWatchVideo(link) }, modifier = Modifier.bouncyPress()) { Text("Guarda il video") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
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
        Icon(sourceIcon(source), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(source.label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Artwork(now: Playing, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = 420.dp)
            // Quadrata, e più piccola se lo schermo è basso: tutto il player sta in una pagina.
            .aspectRatio(1f, matchHeightConstraintsFirst = true)
            .shadow(24.dp, MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Tenendo premuta la copertina: "Salva nelle note", per riaprirla poi ferma.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { Playback.currentAsItem()?.let { SavedLinks.offer(SavedLink.Media(it)) } })
            },
        contentAlignment = Alignment.Center,
    ) {
        IconTile(sourceIcon(now.source), sourceAccent(now.source), size = 96.dp, iconSize = 48.dp)
        if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.fillMaxSize())
        else if (now.artwork.isNotBlank()) {
            AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (now.loading) CircularProgressIndicator(Modifier.size(40.dp))
    }
}

@Composable
private fun TimeBar(now: Playing) {
    val context = LocalContext.current
    if (now.live) {
        Text(
            "● In diretta",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
        return
    }
    if (now.durationMs <= 0) return
    // Mentre si trascina la barra segue il dito; lasciata, il player salta lì.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = livePosition(now)
    Slider(
        value = dragging ?: (position.toFloat() / now.durationMs).coerceIn(0f, 1f),
        enabled = now.canSeek,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { Playback.seekTo(context, (it * now.durationMs).toLong()) }
            dragging = null
        },
    )
    Row {
        Text(clockLabel(dragging?.let { (it * now.durationMs).toLong() } ?: position), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(clockLabel(now.durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Controls(now: Playing) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val podcast = now.source == PlaySource.PODCAST
        if (now.canSkip || podcast) {
            IconButton(onClick = { Playback.previous(context) }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (podcast) Icons.Filled.Replay10 else Icons.Filled.SkipPrevious,
                    contentDescription = if (podcast) "Indietro di 10 secondi" else "Indietro",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        FilledIconButton(
            onClick = { Playback.toggle(context) },
            modifier = Modifier.size(76.dp).bouncyPress(),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(
                if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (now.playing) "Pausa" else "Riproduci",
                modifier = Modifier.size(40.dp),
            )
        }
        if (now.canSkip || podcast) {
            IconButton(onClick = { Playback.next(context) }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (podcast) Icons.Filled.Forward30 else Icons.Filled.SkipNext,
                    contentDescription = if (podcast) "Avanti di 30 secondi" else "Avanti",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
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
