package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.CanvasPlayer

/**
 * I pezzi delle pagine di Spotify e YouTube fatte come il resto di Canvas (1.563, Antonio: "deve essere come il resto
 * dell'app", mockup /mnt/project-files/spotify-redesign/ strada A): titolo grande a sinistra nella barra, poi una prima
 * card con immagine piccola e i tasti, poi card col titolo in maiuscolo e righe Canvas. Il colore solo a ciò che suona.
 */
internal object MediaPage {
    /** Lo spazio fra una card e l'altra (dentro una card le righe stanno attaccate). */
    val GAP = 12.dp
    /** L'immagine della prima card. */
    val HEAD_IMAGE = 84.dp
}

/**
 * La prima card: immagine a sinistra, una riga che si tocca ([onLine]: "Madame ›" apre l'artista) e una sotto,
 * la descrizione (al massimo due righe), poi Riproduci · Casuale · Altro. [extra] = altro sotto i tasti.
 */
@Composable
internal fun MediaHeadCard(
    image: String,
    line: String,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    mark: MediaMark? = null,
    onLine: (() -> Unit)? = null,
    detail: String = "",
    description: String = "",
    onPlay: (() -> Unit)? = null,
    playLabel: String = "Riproduci",
    onShuffle: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    picture: (@Composable () -> Unit)? = null,
    buttons: @Composable RowScope.() -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val haptic = rememberHaptic()
    CanvasCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (picture != null) picture() else Cover(image, if (round) CircleShape else MaterialTheme.shapes.medium, MediaPage.HEAD_IMAGE, mark)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (line.isNotBlank()) Text(
                        if (onLine != null) "$line ›" else line,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onLine != null) Modifier.clip(MaterialTheme.shapes.small).clickable { haptic(Haptic.TICK); onLine() } else Modifier,
                    )
                    if (detail.isNotBlank()) Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (description.isNotBlank()) Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPlay != null) CanvasButton(playLabel, onClick = { haptic(Haptic.CONFIRM); onPlay() }, icon = Icons.Rounded.PlayArrow, primary = true)
                if (onShuffle != null) RoundIconButton(Icons.Rounded.Shuffle, "Casuale") { haptic(Haptic.CONFIRM); onShuffle() }
                buttons()
                if (onMore != null) RoundIconButton(Icons.Rounded.MoreHoriz, "Altro") { haptic(Haptic.CONFIRM); onMore() }
            }
            extra()
        }
    }
}

/**
 * Una card fatta di righe dentro un elenco lungo: il titolo ([CardTitle], con › se [onOpen] apre la pagina intera)
 * e poi una voce per riga, tutte nella stessa card senza separatori. Dopo la card lascia lo spazio [MediaPage.GAP].
 */
internal fun <T> LazyListScope.mediaGroup(
    key: String,
    title: String,
    items: List<T>,
    itemKey: (T) -> Any,
    onOpen: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    row: @Composable (index: Int, item: T) -> Unit,
) {
    val lastIsFooter = footer != null
    item(key = "$key-h") {
        val haptic = rememberHaptic()
        Box(
            Modifier.fillMaxWidth()
                .cardGroupItem(first = true, last = items.isEmpty() && !lastIsFooter, divider = false)
                .then(if (onOpen != null) Modifier.clickable { haptic(Haptic.TICK); onOpen() } else Modifier)
                .padding(bottom = if (items.isEmpty() && !lastIsFooter) 8.dp else 0.dp),
        ) { CardTitle(title, opens = onOpen != null, trailing = trailing) }
    }
    items.forEachIndexed { i, entry ->
        val last = i == items.lastIndex && !lastIsFooter
        item(key = "$key-" + itemKey(entry)) {
            Box(Modifier.fillMaxWidth().cardGroupItem(first = false, last = last, divider = false).padding(bottom = if (last) 8.dp else 0.dp)) {
                row(i, entry)
            }
        }
    }
    if (footer != null) item(key = "$key-f") {
        Box(Modifier.fillMaxWidth().cardGroupItem(first = false, last = true, divider = false).padding(bottom = 8.dp)) { footer() }
    }
    item(key = "$key-gap") { Spacer(Modifier.height(MediaPage.GAP)) }
}

/** Lo spazio sotto una card messa da sola in un elenco a card attaccate. */
internal fun LazyListScope.mediaGap(key: String) = item(key = key) { Spacer(Modifier.height(MediaPage.GAP)) }

/**
 * La riga Canvas dei brani e dei video (regola 1 del 1.402): a sinistra il numero o la copertina, in mezzo nome e una
 * riga, a destra la durata. Quella che suona adesso ([now]) ha il colore "adesso" e l'onda al posto del numero.
 */
@Composable
internal fun MediaRow(
    title: String,
    line: String,
    click: Modifier,
    number: Int? = null,
    image: String? = null,
    round: Boolean = false,
    wide: Boolean = false,
    mark: MediaMark? = null,
    trailing: String? = null,
    now: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).bouncyPress(0.98f).clip(MaterialTheme.shapes.medium).then(click)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            number != null -> Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (now) Icon(Icons.Outlined.GraphicEq, contentDescription = "Suona adesso", tint = accent, modifier = Modifier.size(18.dp))
                else Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            wide -> Box(
                Modifier.width(72.dp).height(41.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!image.isNullOrBlank()) AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                MediaMarkOverlay(mark, compact = true, inset = 3.dp)
            }
            image != null -> Cover(image, if (round) CircleShape else MaterialTheme.shapes.small, 44.dp, mark)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (now) accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (now) FontWeight.SemiBold else null,
                maxLines = if (wide) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.isNotBlank()) Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!trailing.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** La chiave di quello che suona adesso (per Spotify l'uri del brano): la riga uguale si colora. */
@Composable
internal fun rememberPlayingKey(): String? {
    val now by CanvasPlayer.now.collectAsState()
    return now?.item?.key
}

/** Il testo a destra del titolo di una card che si allarga ("Altri" / "Meno"), come "Mostra altri" della ricerca. */
@Composable
internal fun MoreToggle(open: Boolean, onToggle: () -> Unit) {
    val haptic = rememberHaptic()
    Text(
        if (open) "Meno" else "Altri",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { haptic(Haptic.TICK); onToggle() }.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/**
 * La cronologia per giorno (1.563, come il Calendario): una card per giorno, dal più recente; il nome del giorno è
 * "Oggi", "Ieri", poi "Mar 18 nov" (con l'anno se non è quest'anno). Funzione pura, provata nei test.
 */
internal object HistoryDays {
    private val days = listOf("Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom")
    private val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")

    fun label(day: java.time.LocalDate, today: java.time.LocalDate): String = when (day) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> days[day.dayOfWeek.value - 1] + " ${day.dayOfMonth} " + months[day.monthValue - 1] +
            if (day.year != today.year) " ${day.year}" else ""
    }

    fun <T> group(items: List<T>, at: (T) -> Long, zone: java.time.ZoneId, today: java.time.LocalDate): List<Pair<String, List<T>>> =
        items.sortedByDescending(at)
            .groupBy { java.time.Instant.ofEpochMilli(at(it)).atZone(zone).toLocalDate() }
            .map { (day, list) -> label(day, today) to list }
}
