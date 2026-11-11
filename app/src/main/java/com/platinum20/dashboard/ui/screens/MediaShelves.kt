package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyMixes
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.settings.AppSettings
import java.time.LocalDate

/**
 * Le file di Media (1.357, Antonio): al posto delle tre tessere Musica/Video/Radio, quello che c'è dentro.
 * Ogni fila ha il titolo che apre la pagina intera; una fila vuota non si disegna.
 */
internal object MediaShelves {
    /** Quante copertine al massimo in una fila: il resto sta nella pagina intera. */
    const val MAX = 12

    /** Righe del titolo nelle file di Media e in Continua (1.461, Antonio: massimo 2, poi i puntini). */
    const val TITLE_LINES = 2

    /** Fila Video (1.442, Antonio): i video dei canali a cui sei iscritto, dal più nuovo; senza data in fondo. */
    fun newestFirst(videos: List<YtVideo>): List<YtVideo> =
        videos.sortedWith(compareByDescending<YtVideo, java.time.Instant?>(nullsFirst()) { it.published })

    /** Che cosa mostra la fila Musica: prima i mix, poi gli ascoltati di recente, senza doppioni. */
    fun <T> mix(first: List<T>, then: List<T>, key: (T) -> String, max: Int = MAX): List<T> =
        (first + then).distinctBy(key).take(max)

    /** Prima quelli nuovi, poi gli altri, ognuno nel suo ordine (uscite non aperte, episodi non ascoltati). */
    fun <T> freshFirst(list: List<T>, isNew: (T) -> Boolean): List<T> = list.filter(isNew) + list.filterNot(isNew)

    /** Il titolo di una fila con quanti nuovi ci sono: "Video · 6 nuovi"; solo il nome se zero. */
    fun title(name: String, fresh: Int): String =
        com.platinum20.dashboard.media.MediaFresh.shelfCount(fresh).let { if (it.isEmpty()) name else "$name · $it" }
}

/**
 * Copertina con le sue scritte (1.461, Antonio: di nuovo sotto, come prima del 1.437): l'immagine, poi il titolo
 * al massimo su [titleLines] righe e chi lo pubblica su una riga, con i puntini se non ci stanno. Altezze fisse:
 * il titolo occupa sempre [titleLines] righe e l'autore una, così le tessere di una fila sono alte uguali.
 * Va chiamata dentro la Column della tessera.
 */
@Composable
internal fun CaptionedCover(
    title: String,
    subtitle: String?,
    titleLines: Int = 2,
    centered: Boolean = false,
    textModifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    cover()
    @Composable
    fun texts(title: String, subtitle: String?, fixed: Boolean) = Column {
        // Un filo di margine: gli angoli arrotondati della tessera non tagliano le lettere.
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            minLines = if (fixed) titleLines else 1,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = textModifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 6.dp),
        )
        if (subtitle != null) Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = textModifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp),
        )
    }
    // Altezza fissa (titolo su [titleLines] righe + autore): la tessera tiene sempre quello spazio, invisibile,
    // ma l'autore sta subito sotto il titolo. Titolo su una riga → autore sulla seconda, terza vuota (Antonio, 2026-11-11).
    Box {
        Box(Modifier.alpha(0f).clearAndSetSemantics { }) { texts(" ", subtitle?.let { " " }, fixed = true) }
        texts(title, subtitle, fixed = false)
    }
}
