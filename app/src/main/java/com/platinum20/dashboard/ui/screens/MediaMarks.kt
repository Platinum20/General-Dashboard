package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind

/** Che cosa è una copertina (1.485, Antonio): musica, video, podcast o radio. */
enum class MediaType { MUSIC, VIDEO, PODCAST, RADIO }

/** I segni sopra una copertina: il simbolo del tipo in alto a sinistra, la durata in basso a destra (vuota = niente). */
data class MediaMark(val type: MediaType, val duration: String? = null)

/**
 * Simbolo e durata su ogni copertina (1.485, Antonio: "ovunque appaia un media"): così in un feed che mescola Spotify,
 * YouTube, podcast e radio si capisce a colpo d'occhio cosa parte. La radio non ha durata: dice LIVE.
 */
object MediaMarks {
    const val LIVE = "LIVE"

    fun of(item: SpItem): MediaMark? = when (item.kind) {
        SpKind.ARTIST -> null
        SpKind.SHOW -> MediaMark(MediaType.PODCAST)
        SpKind.EPISODE -> MediaMark(MediaType.PODCAST, minutes(item.durationMs))
        SpKind.TRACK -> MediaMark(MediaType.MUSIC, clock(item.durationMs))
        SpKind.ALBUM, SpKind.PLAYLIST -> MediaMark(MediaType.MUSIC)
    }

    fun type(kind: ResumeKind): MediaType = when (kind) {
        ResumeKind.VIDEO, ResumeKind.YOUTUBE -> MediaType.VIDEO
        ResumeKind.MUSIC, ResumeKind.SPOTIFY -> MediaType.MUSIC
        ResumeKind.PODCAST -> MediaType.PODCAST
        ResumeKind.RADIO -> MediaType.RADIO
    }

    fun of(kind: ResumeKind, durationMs: Long): MediaMark = when (val t = type(kind)) {
        MediaType.RADIO -> MediaMark(t, LIVE)
        MediaType.PODCAST -> MediaMark(t, minutes(durationMs))
        else -> MediaMark(t, clock(durationMs))
    }

    /** 252000 → "4:12", 3723000 → "1:02:03"; null se non si sa. */
    fun clock(ms: Long): String? {
        if (ms <= 0) return null
        val s = ms / 1000
        val h = s / 3600
        return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    /** Gli episodi in minuti, come li dice chi fa podcast: "38 min", "1 h 12 min"; null se non si sa. */
    fun minutes(ms: Long): String? {
        if (ms < 60_000) return null
        val m = ms / 60_000
        return if (m >= 60) "${m / 60} h ${m % 60} min".replace(" 0 min", "") else "$m min"
    }

    internal fun icon(type: MediaType): ImageVector = when (type) {
        MediaType.MUSIC -> Icons.Filled.MusicNote
        MediaType.VIDEO -> Icons.Filled.PlayArrow
        MediaType.PODCAST -> Icons.Filled.Mic
        MediaType.RADIO -> Icons.Filled.Radio
    }

    internal fun label(type: MediaType): String = when (type) {
        MediaType.MUSIC -> "Musica"
        MediaType.VIDEO -> "Video"
        MediaType.PODCAST -> "Podcast"
        MediaType.RADIO -> "Radio"
    }
}

/**
 * Disegna [mark] dentro la copertina (il Box che la contiene). [compact] per le copertine piccole delle righe;
 * [inset] più grande sulle copertine tonde, che taglierebbero gli angoli.
 */
@Composable
fun BoxScope.MediaMarkOverlay(mark: MediaMark?, compact: Boolean = false, inset: Dp = if (compact) 3.dp else 6.dp, showType: Boolean = true) {
    mark ?: return
    val badge = Color.Black.copy(alpha = 0.66f)
    if (showType) Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(inset)
            .size(if (compact) 16.dp else 22.dp)
            .background(badge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            MediaMarks.icon(mark.type),
            contentDescription = MediaMarks.label(mark.type),
            tint = Color.White,
            modifier = Modifier.size(if (compact) 11.dp else 14.dp),
        )
    }
    mark.duration?.let { text ->
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = if (compact) 9.sp else 11.sp),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(inset)
                .background(badge, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = if (compact) 3.dp else 5.dp, vertical = 1.dp),
        )
    }
}

/**
 * La tessera unica di Media (1.486, Antonio: "la musica quadrata, i video rettangolari, per il resto uguali"): stessa
 * altezza di copertina [height], simbolo e durata sopra, poi titolo su due righe e autore con la data tutta a destra.
 * [wide] = video (16:9), se no quadrata; [round] per artisti e radio.
 */
@Composable
internal fun MediaTile(
    height: Dp,
    wide: Boolean,
    image: String,
    title: String,
    author: String?,
    date: String?,
    mark: MediaMark?,
    /** Il tocco: `Modifier.clickable { }` o `tapOrSave(...)` (tenendo premuto si salva). */
    click: Modifier,
    round: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val width = if (wide) height * 16f / 9f else height
    val shape = if (round) CircleShape else MaterialTheme.shapes.medium
    androidx.compose.foundation.layout.Column(
        Modifier
            .width(width)
            .bouncyPress(0.96f)
            .then(click),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        CaptionedCover(title, author, centered = round, trailing = date) {
            Box(
                Modifier.width(width).height(height).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                mark?.let {
                    Icon(MediaMarks.icon(it.type), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (image.isNotBlank()) {
                    coil3.compose.AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (round) MediaMarkOverlay(mark, inset = height * 0.1f) else MediaMarkOverlay(mark)
                overlay()
            }
        }
    }
}

/** Altezza di [MediaLine]: la copertina di un video 16:9 larga [MEDIA_LINE_SLOT] e quella quadrata della musica. */
internal val MEDIA_LINE_HEIGHT = 72.dp
internal val MEDIA_LINE_SLOT = 128.dp

/**
 * La riga unica delle liste di Media dove musica e video stanno insieme (1.488, Antonio: "che abbiano tutti lo stesso
 * aspetto", e "nelle cose verticali basta che siano uguali"): stessa altezza, nessun bordo, la stessa copertina 16:9 per
 * musica e video (quadrata/rettangolare solo nelle file orizzontali), così il testo parte sempre dallo stesso punto.
 * [wide] resta per chi la chiama: dice solo se è un video.
 */
@Composable
internal fun MediaLine(
    wide: Boolean,
    image: String,
    title: String,
    author: String?,
    mark: MediaMark?,
    click: Modifier,
    round: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = if (round) CircleShape else MaterialTheme.shapes.medium
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().height(MEDIA_LINE_HEIGHT).bouncyPress(0.97f).clip(MaterialTheme.shapes.medium).then(click),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(MEDIA_LINE_SLOT).height(MEDIA_LINE_HEIGHT), contentAlignment = Alignment.Center) {
            Box(
                Modifier.width(if (round) MEDIA_LINE_HEIGHT else MEDIA_LINE_SLOT).height(MEDIA_LINE_HEIGHT)
                    .clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                mark?.let { Icon(MediaMarks.icon(it.type), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (image.isNotBlank()) {
                    coil3.compose.AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (round) MediaMarkOverlay(mark, compact = true, inset = MEDIA_LINE_HEIGHT * 0.1f) else MediaMarkOverlay(mark, compact = true, inset = 4.dp)
                overlay()
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            author?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
