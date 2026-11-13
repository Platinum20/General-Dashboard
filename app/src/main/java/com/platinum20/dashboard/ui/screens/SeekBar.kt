package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.toBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Regole della barra del tempo: un tocco non sposta mai il video (troppi salti per sbaglio),
 * solo un trascinamento soprattutto orizzontale. Pure, così si testano.
 */
object SeekGesture {
    /** Il trascinamento è un seek solo se va più di lato che in verticale (su/giù resta allo schermo intero). */
    fun isSeek(dx: Float, dy: Float): Boolean = abs(dx) > abs(dy)

    /** Punto della barra (0..1) sotto al dito. */
    fun fraction(x: Float, width: Int): Float = if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)
}

/**
 * La barra del tempo dei player, al posto dello Slider di Material che salta al primo tocco.
 * Si sposta solo trascinando di lato oltre la soglia del tocco; [onSeeking] segue il dito,
 * [onSeekEnd] dice se applicare (false = gesto annullato). Il tocco semplice passa al video.
 */
@Composable
internal fun SeekBar(
    value: Float,
    enabled: Boolean,
    onSeeking: (Float) -> Unit,
    onSeekEnd: (commit: Boolean) -> Unit,
    thumb: @Composable () -> Unit,
    track: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seeking by rememberUpdatedState(onSeeking)
    val end by rememberUpdatedState(onSeekEnd)
    Layout(
        content = {
            track()
            thumb()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(SEEK_HEIGHT)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                if (enabled) setProgress { seeking(it.coerceIn(0f, 1f)); end(true); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Finché il dito non si muove di lato oltre la soglia non succede nulla: niente salti al tocco.
                    val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        if (SeekGesture.isSeek(over.x, over.y)) change.consume()
                    } ?: return@awaitEachGesture
                    seeking(SeekGesture.fraction(start.position.x, size.width))
                    val done = horizontalDrag(start.id) { change ->
                        change.consume()
                        seeking(SeekGesture.fraction(change.position.x, size.width))
                    }
                    end(done)
                }
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trackP = measurables[0].measure(loose.copy(minWidth = width, maxWidth = width))
        val thumbP = measurables[1].measure(loose)
        val height = maxOf(trackP.height, thumbP.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            trackP.place(0, (height - trackP.height) / 2)
            val x = (value.coerceIn(0f, 1f) * width - thumbP.width / 2f).roundToInt()
                .coerceIn(0, (width - thumbP.width).coerceAtLeast(0))
            thumbP.place(x, (height - thumbP.height) / 2)
        }
    }
}

/**
 * Le tacche dei capitoli sulla barra del video (1.518): dove comincia ciascuno, tranne il primo
 * (all'inizio non serve) e quelli oltre la fine. Pure, così si prova.
 */
object ChapterTicks {
    fun fractions(chapters: List<com.platinum20.dashboard.media.youtube.player.YtChapter>, durationMs: Long): List<Float> {
        if (chapters.size < 2 || durationMs <= 0) return emptyList()
        return chapters.map { it.startSec * 1000f / durationMs }.filter { it > 0f && it < 1f }.distinct().sorted()
    }

    /** Il capitolo in corso a [positionMs]: l'ultimo cominciato. */
    fun at(chapters: List<com.platinum20.dashboard.media.youtube.player.YtChapter>, positionMs: Long): com.platinum20.dashboard.media.youtube.player.YtChapter? =
        if (chapters.size < 2) null else chapters.filter { it.startSec * 1000L <= positionMs }.maxByOrNull { it.startSec }

    /** "12:30 · Il titolo del capitolo" sotto l'anteprima; solo il tempo senza capitoli. */
    fun label(time: String, chapters: List<com.platinum20.dashboard.media.youtube.player.YtChapter>, positionMs: Long): String =
        at(chapters, positionMs)?.title?.takeIf { it.isNotBlank() }?.let { "$time · $it" } ?: time
}

/** Il doppio tocco ripetuto si somma: +10, +20, +30… finché si resta dallo stesso lato. */
object SeekStack {
    const val STEP_S = 10

    fun next(shownS: Int, forward: Boolean): Int = when {
        forward && shownS > 0 -> shownS + STEP_S
        !forward && shownS < 0 -> shownS - STEP_S
        forward -> STEP_S
        else -> -STEP_S
    }
}

/**
 * Scorrere di lato al centro del video, come il volume in verticale: avanti e indietro piano,
 * con una vibrazione ogni [TICK_MS] e l'anteprima del fotogramma. Pure, così si prova.
 */
object SeekSwipe {
    /** Tutta la larghezza del video = tre minuti (1.526, Antonio: un minuto era troppo lento). */
    const val SPAN_MS = 180_000L

    /** L'ombra dei testi sopra il video al posto della bolla scura. */
    val textShadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 10f)
    const val TICK_MS = 5_000L

    /** Parte solo lontano dai bordi a destra e sinistra: lì c'è il gesto Indietro del telefono. */
    fun canStart(downX: Float, widthPx: Float, leftEdgePx: Float, rightEdgePx: Float): Boolean =
        downX > leftEdgePx && downX < widthPx - rightEdgePx

    /** Come il volume ma di lato: dopo [deadZonePx] di strada, e solo se più orizzontale che verticale. */
    fun armed(dx: Float, dy: Float, deadZonePx: Float): Boolean =
        abs(dx) > deadZonePx && abs(dx) > abs(dy) * 1.5f

    /** Il punto dove si arriva: dal punto di partenza, [SPAN_MS] per tutta la larghezza. */
    fun target(startMs: Long, draggedPx: Float, widthPx: Float, durationMs: Long): Long {
        if (widthPx <= 0f || durationMs <= 0) return startMs.coerceAtLeast(0)
        return (startMs + (draggedPx / widthPx * SPAN_MS).toLong()).coerceIn(0, durationMs)
    }

    /** Vibra quando si passa una tacca di [TICK_MS], o quando si tocca l'inizio o la fine. */
    fun ticks(previousMs: Long, nowMs: Long, durationMs: Long): Boolean =
        previousMs != nowMs && (previousMs / TICK_MS != nowMs / TICK_MS || nowMs == 0L || nowMs == durationMs)

    /** "+0:15" / "−1:05": quanto ci si sposta rispetto a dove si era. */
    fun deltaLabel(deltaMs: Long): String {
        val s = abs(deltaMs) / 1000
        return (if (deltaMs < 0) "−" else "+") + "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
}

/**
 * Il fotogramma dello storyboard di YouTube al punto [positionMs], col tempo sotto: lo stesso
 * riquadro per la barra e per lo scorrimento sul video. Senza storyboard resta solo il tempo.
 */
@Composable
internal fun SeekPreview(
    storyboard: com.platinum20.dashboard.media.youtube.player.YtStoryboard?,
    positionMs: Long,
    label: String,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 160.dp,
) {
    val spot = storyboard?.frameAt(positionMs)
    val context = androidx.compose.ui.platform.LocalContext.current
    // Il foglio si scarica una volta (cache di Coil) e resta finché si passa a un altro foglio.
    var sheet by remember { mutableStateOf<Pair<String, androidx.compose.ui.graphics.ImageBitmap>?>(null) }
    androidx.compose.runtime.LaunchedEffect(spot?.url) {
        val url = spot?.url ?: return@LaunchedEffect
        if (sheet?.first == url) return@LaunchedEffect
        val request = coil3.request.ImageRequest.Builder(context).data(url).build()
        val image = (coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult)?.image
        if (image != null) sheet = url to image.toBitmap().asImageBitmap()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = sheet?.takeIf { it.first == spot?.url }?.second
        if (storyboard != null && spot != null) {
            val ratio = storyboard.frameWidth.toFloat() / storyboard.frameHeight.coerceAtLeast(1)
            androidx.compose.foundation.Canvas(
                Modifier
                    .width(width)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(1.5.dp, Color.White, RoundedCornerShape(10.dp)),
            ) {
                if (bitmap != null) drawImage(
                    bitmap,
                    srcOffset = IntOffset(spot.column * storyboard.frameWidth, spot.row * storyboard.frameHeight),
                    srcSize = IntSize(storyboard.frameWidth, storyboard.frameHeight),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(
            label,
            color = Color.White,
            // Solo il testo, senza bolla (Antonio, 1.526): l'ombra basta a leggerlo sul video.
            style = MaterialTheme.typography.labelLarge.copy(shadow = SeekSwipe.textShadow),
            // Col nome del capitolo può essere lunga: una riga sola, tagliata.
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
