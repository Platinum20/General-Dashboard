package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.capture.CaptureComposer
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.SearchKind
import com.platinum20.dashboard.search.CaptureMatch
import com.platinum20.dashboard.search.EverywhereSearch
import com.platinum20.dashboard.tickets.TicketQrStore
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Scrivi nel + e trovi già (idea 11, mockup ricerca/Scrivi-nel-piu): mentre scrivi, sopra il riquadro, al più tre cose
 * che hai già sul telefono, con la parola in blu. Tocco = si apre; tenuto premuto = il suo fumetto, e per note ed eventi
 * "Aggiungi qui" mette quello che hai scritto in fondo. Niente rete: risponde subito. Se non c'è niente non compare.
 */
@Composable
internal fun CaptureMatches(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val text = CaptureComposer.text.text
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val finder = rememberFinder()
    val inApp = LocalInAppNav.current
    var events by remember { mutableStateOf(SearchCache.events) }
    val tickets = remember { TicketQrStore.eventIds(context) }
    LaunchedEffect(Unit) { events = SearchCache.events(context, today) }
    var query by remember { mutableStateOf("") }
    // Un attimo dopo l'ultima lettera: mentre si scrive veloce la lista non salta a ogni tasto.
    LaunchedEffect(text) {
        delay(140)
        query = text
    }
    val q = query.trim()
    val found: List<Pair<Found, Any?>> = if (!CaptureMatch.asks(q)) emptyList() else buildList<Pair<Found, Any?>> {
        finder.notes(data, q, emptyMap(), today).forEach { (kind, f) ->
            add(f to if (kind == SearchKind.NOTE) data.notes.firstOrNull { "n${it.id}" == f.key }?.id else null)
        }
        EverywhereSearch.events(events, q, today).forEach { (d, e) ->
            add(finder.event(d, e, q, tickets, com.platinum20.dashboard.search.SearchMatch.score(q, e.title, e.location)) to e)
        }
        finder.myMusic(q).forEach { add(it to null) }
        finder.myVideos(q).forEach { add(it to null) }
    }.let { list -> CaptureMatch.top(list) { p -> p.first.score } }

    AnimatedVisibility(
        found.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        // L'ultima lista resta mentre sparisce, così non si svuota a metà animazione.
        val last = remember { mutableStateOf(found) }
        if (found.isNotEmpty()) last.value = found
        val shape = RoundedCornerShape(24.dp)
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).glass(shape)) {
            Text(
                "GIÀ IN CANVAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 2.dp),
            )
            last.value.forEach { (f, target) ->
                val long = when (target) {
                    is Long -> { { addBubble(f, context) { AddFromPlus.toNote(target, text) } } }
                    is CalendarEvent -> { { addBubble(f, context) { AddFromPlus.toEvent(target, text); true } } }
                    else -> f.onLong
                }
                FoundRow(f.copy(onLong = long), q, onOpened = { CaptureComposer.close() })
            }
            Text(
                "Tutti i risultati per «$q» ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        CaptureComposer.close()
                        inApp?.openRoute?.invoke(Routes.search(null, q))
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** Il fumetto di un risultato nel +: "Aggiungi qui" (quello scritto va in fondo) e Apri. */
private fun addBubble(f: Found, context: android.content.Context, add: () -> Boolean) {
    val written = CaptureComposer.text.text.trim()
    ActionBubble.show(
        BubbleHead(if (f.key.startsWith("e")) "Evento" else "Nota", f.title),
        BubbleAction(
            Icons.Outlined.EditNote, "Aggiungi qui",
            "in fondo: «" + written.take(22) + (if (written.length > 22) "…" else "") + "»",
            group = BubbleGroup.CREA,
        ) {
            if (add()) {
                CaptureComposer.finish()
                CaptureFeedback.tell(context, "Aggiunto a " + f.title)
            }
        },
        Slot.open { CaptureComposer.close(); f.open() },
    )
}
