package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.search.EverywhereSearch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val eventHour = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Cerca ovunque, dalla lente di Oggi: tutto quello che sta in Note (promemoria, note, spese,
 * pacchi, date, abitudini), gli impegni del calendario di un mese indietro e tre avanti e le
 * notizie già scaricate. Un posto solo per ritrovare le cose, qualunque funzione le abbia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val news by NewsStore.result.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    val today = remember { LocalDate.now() }
    var events by remember { mutableStateOf<Map<LocalDate, List<CalendarEvent>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        if (news == null) NewsStore.restore()
        events = CalendarRepository.eventsFrom(context, today.minusDays(30), 120)
    }

    Scaffold(topBar = { TopLevelBar(title = "Cerca", onBack = onBack) }) { inner ->
        NotesSearchPanel(
            data = data,
            padding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            onClose = onBack,
            onOpenNote = onOpenNote,
            onOpenTab = { onOpenNotes() },
            placeholder = "Cerca in note, agenda, notizie…",
            extra = { query ->
                val foundEvents = EverywhereSearch.events(events, query, today)
                val foundNews = EverywhereSearch.news(news?.items.orEmpty(), query)
                if (foundEvents.isNotEmpty()) item(key = "events") {
                    Section(title = "Agenda · ${foundEvents.size}") {
                        foundEvents.forEachIndexed { i, (day, e) ->
                            if (i > 0) RowDivider()
                            val time = if (e.allDay) "" else " · " + Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).format(eventHour)
                            ResultRow(e.title, longDate(day) + time) { inApp?.openEvent?.invoke(e) }
                        }
                    }
                }
                if (foundNews.isNotEmpty()) item(key = "news") {
                    Section(title = "Notizie · ${foundNews.size}") {
                        foundNews.forEachIndexed { i, n ->
                            if (i > 0) RowDivider()
                            val whenText = n.publishedAt?.let { " · " + relativeTime(it) }.orEmpty()
                            ResultRow(n.title, n.source.name + whenText) { onOpenArticle(n) }
                        }
                    }
                }
                foundEvents.size + foundNews.size
            },
        )
    }
}

@Composable
private fun ResultRow(title: String, detail: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .bouncyPress()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
