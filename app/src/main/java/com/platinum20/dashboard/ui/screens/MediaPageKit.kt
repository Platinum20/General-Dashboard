package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import coil3.request.allowHardware
import coil3.toBitmap
import com.platinum20.dashboard.media.CanvasPlayer

/**
 * I pezzi delle pagine di Spotify e YouTube (giro 2, 1.566, Antonio: mockup /mnt/project-files/spotify-redesign/v2):
 * come le altre pagine intere dell'app (Calendario, News) le righe stanno SULLO SFONDO, non in card. In cima un solo
 * blocco: immagine grande, il nome una volta sola, un tasto ▶ grande; il colore lo dà la copertina e sfuma nello sfondo.
 */
internal object MediaPage {
    /** Lo spazio fra un gruppo e l'altro. */
    val GAP = 12.dp
    /** La copertina quadrata del blocco in cima (album, playlist, mix). */
    val HERO_COVER = 200.dp
    /** La foto tonda (canale). */
    val HERO_ROUND = 112.dp
    /** L'altezza della foto a tutta larghezza (artista). */
    val HERO_PHOTO = 300.dp
}

/** Come si mostra l'immagine in cima: copertina quadrata, foto tonda (canale) o foto a tutta larghezza (artista). */
internal enum class HeroStyle { COVER, ROUND, PHOTO }

/**
 * Il blocco in cima di ogni pagina di Spotify e YouTube: indietro, immagine grande, nome, una riga che si tocca
 * ([onLine]: "Madame ›" apre l'artista, con la sua foto [lineImage]), dettagli e descrizione, poi le icone a sinistra
 * ([onMore], [buttons]) e a destra Casuale e il ▶ grande. Sostituisce la barra del titolo: il nome c'è una volta sola.
 */
@Composable
internal fun MediaHero(
    title: String,
    image: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    style: HeroStyle = HeroStyle.COVER,
    mark: MediaMark? = null,
    line: String = "",
    lineImage: String? = null,
    onLine: (() -> Unit)? = null,
    detail: String = "",
    description: String = "",
    onPlay: (() -> Unit)? = null,
    /** Sta suonando proprio questo (una radio, un podcast): il tasto grande diventa ❚❚. */
    playing: Boolean = false,
    onShuffle: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    picture: (@Composable () -> Unit)? = null,
    /** Icone in più a sinistra (il cuore, Fine del riordino) o, nel canale, il tasto Iscriviti al centro. */
    buttons: @Composable RowScope.() -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val haptic = rememberHaptic()
    val tint = rememberCoverTint(image)
    val bg = MaterialTheme.colorScheme.background
    val centered = style == HeroStyle.ROUND
    Box(modifier.fillMaxWidth()) {
        if (style == HeroStyle.PHOTO) {
            Box(Modifier.fillMaxWidth().height(MediaPage.HERO_PHOTO).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (image.isNotBlank()) AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to bg)))
            }
        } else {
            // Il colore della copertina dietro al blocco, che sfuma nello sfondo prima delle righe.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf((tint ?: bg).copy(alpha = 0.55f), bg))))
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Indietro")
            }
            when (style) {
                HeroStyle.PHOTO -> Spacer(Modifier.height(MediaPage.HERO_PHOTO - 150.dp))
                HeroStyle.ROUND -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (picture != null) picture() else Cover(image, CircleShape, MediaPage.HERO_ROUND, mark)
                }
                HeroStyle.COVER -> Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.shadow(18.dp, MaterialTheme.shapes.large)) {
                        if (picture != null) picture() else Cover(image, MaterialTheme.shapes.large, MediaPage.HERO_COVER, mark)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
            ) {
                Text(
                    title,
                    style = if (style == HeroStyle.PHOTO) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                )
                if (line.isNotBlank()) Row(
                    Modifier.padding(top = 6.dp).clip(MaterialTheme.shapes.small)
                        .then(if (onLine != null) Modifier.clickable { haptic(Haptic.TICK); onLine() } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!lineImage.isNullOrBlank()) {
                        AsyncImage(model = lineImage, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(22.dp).clip(CircleShape))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (onLine != null) "$line ›" else line,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (detail.isNotBlank()) Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (description.isNotBlank()) Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 4.dp).heightIn(min = 56.dp),
                horizontalArrangement = if (centered && onPlay == null) Arrangement.Center else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (centered && onPlay == null) {
                    buttons()
                } else {
                    buttons()
                    if (onMore != null) IconButton(onClick = { haptic(Haptic.CONFIRM); onMore() }) {
                        Icon(Icons.Rounded.MoreHoriz, contentDescription = "Altro")
                    }
                    Spacer(Modifier.weight(1f))
                    if (onShuffle != null) IconButton(onClick = { haptic(Haptic.CONFIRM); onShuffle() }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Casuale")
                    }
                    if (onPlay != null) {
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { haptic(Haptic.CONFIRM); onPlay() }, modifier = Modifier.size(56.dp).bouncyPress()) {
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playing) "Pausa" else "Riproduci",
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            }
            extra()
        }
    }
}

/**
 * Il colore di una copertina, per tingere il blocco in cima (Spotify fa lo stesso). Si legge da un'immagine piccola
 * in un secondo momento: finché non c'è, il blocco ha il colore dello sfondo.
 */
@Composable
internal fun rememberCoverTint(url: String): Color? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tint by androidx.compose.runtime.produceState<Color?>(null, url) {
        if (url.isBlank()) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val request = coil3.request.ImageRequest.Builder(context).data(url).allowHardware(false).build()
                val result = coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult ?: return@runCatching null
                val small = android.graphics.Bitmap.createScaledBitmap(result.image.toBitmap(), 16, 16, true)
                val pixels = IntArray(16 * 16).also { small.getPixels(it, 0, 16, 0, 0, 16, 16) }
                Color(CoverTint.of(pixels))
            }.getOrNull()
        }
    }
    return tint
}

/** Il colore che rappresenta una copertina: la media dei pixel pesata sui più vivi, scurita per stare sotto al testo chiaro. */
internal object CoverTint {
    fun of(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0xFF000000.toInt()
        var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
        for (p in pixels) {
            val pr = (p shr 16 and 0xFF) / 255.0; val pg = (p shr 8 and 0xFF) / 255.0; val pb = (p and 0xFF) / 255.0
            val max = maxOf(pr, pg, pb); val min = minOf(pr, pg, pb)
            val weight = 0.15 + (max - min) // i pixel grigi contano poco: il colore lo fanno quelli vivi
            r += pr * weight; g += pg * weight; b += pb * weight; w += weight
        }
        r /= w; g /= w; b /= w
        // Mai più chiaro del 55%: sotto c'è il testo chiaro del tema scuro.
        val max = maxOf(r, g, b)
        if (max > 0.55) { val k = 0.55 / max; r *= k; g *= k; b *= k }
        fun c(v: Double) = (v * 255).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}

/**
 * Un gruppo di righe in un elenco lungo, sullo sfondo come le pagine di News e del Calendario: il titolo in maiuscolo
 * ([CardTitle], con › se [onOpen] apre la pagina intera) e una voce per riga. Dopo lascia lo spazio [MediaPage.GAP].
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
    // Senza titolo (i brani di un album, subito sotto il blocco in cima) si parte dalle righe.
    if (title.isNotBlank()) item(key = "$key-h") {
        val haptic = rememberHaptic()
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp)
                .then(if (onOpen != null) Modifier.clickable { haptic(Haptic.TICK); onOpen() } else Modifier),
        ) { CardTitle(title, opens = onOpen != null, trailing = trailing) }
    }
    items.forEachIndexed { i, entry ->
        item(key = "$key-" + itemKey(entry)) { row(i, entry) }
    }
    if (footer != null) item(key = "$key-f") { footer() }
    item(key = "$key-gap") { Spacer(Modifier.height(MediaPage.GAP)) }
}

/** Un gruppo che non è fatto di righe (file che scorrono di lato): solo il titolo, poi il contenuto. */
internal fun LazyListScope.mediaShelf(key: String, title: String, onOpen: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    item(key = key) {
        val haptic = rememberHaptic()
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = MediaPage.GAP)) {
            Box(Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable { haptic(Haptic.TICK); onOpen() } else Modifier)) {
                CardTitle(title, opens = onOpen != null, trailing = trailing)
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

/**
 * Il nome di un giorno sulla linea del tempo, come nel Calendario: "Oggi ———— ●" (il puntino di oggi ha il colore
 * di "adesso"). Tenendolo premuto, [onLongClick] (la Cronologia lo usa per svuotarsi).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DayLineHeader(label: String, now: Boolean, onLongClick: (() -> Unit)? = null) {
    val haptic = rememberHaptic()
    Row(
        Modifier.fillMaxWidth()
            .then(if (onLongClick != null) Modifier.combinedClickable(onClick = {}, onLongClick = { haptic(Haptic.CONFIRM); onLongClick() }) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface))
    }
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
