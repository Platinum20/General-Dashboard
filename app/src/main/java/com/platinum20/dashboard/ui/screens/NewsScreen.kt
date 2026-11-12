package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.news.NewsFeed
import com.platinum20.dashboard.news.NewsHistory
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsLayout
import com.platinum20.dashboard.news.NewsStories
import com.platinum20.dashboard.news.NewsStory
import com.platinum20.dashboard.news.NewsViewModel
import com.platinum20.dashboard.news.RecapReader
import com.platinum20.dashboard.news.RecapSpeaker
import com.platinum20.dashboard.news.key
import com.platinum20.dashboard.news.newest
import com.platinum20.dashboard.settings.AppSettings
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val READ_ALPHA = 0.55f

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** L'ora di una visita come la legge Antonio ("09:12"), nel fuso del telefono. */
internal fun newsClock(at: Instant): String = clockFormat.format(at.atZone(ZoneId.systemDefault()))

/** L'articolo come cosa da salvare nelle note: si riapre nel lettore. */
internal fun NewsItem.savedLink() = SavedLink.Article(link, source.name, title, imageUrl.orEmpty())

/**
 * Le News rispondono a "cosa è successo da quando ho guardato?": una riga per fatto, le nuove sopra il
 * segno "ULTIMA VISITA", le già viste sotto. In cima l'unico blocco colorato: ascoltare le nuove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    bottomPadding: PaddingValues,
    onOpenArticle: (NewsItem) -> Unit,
    viewModel: NewsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    NewsHistory.init(context)
    val readList by NewsHistory.read.collectAsStateWithLifecycle()
    val read = remember(readList) { readList.toSet() }
    val hidden by AppSettings.hiddenNewsSources.collectAsStateWithLifecycle()
    // Da quando contano le nuove: tornando dal lettore la visita è la stessa, le nuove restano nuove.
    val since = remember { NewsHistory.enter() }
    val settled by NewsHistory.settled.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { NewsHistory.markVisit() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { NewsHistory.markVisit() }
    val open = { item: NewsItem ->
        NewsHistory.markRead(item.link)
        NewsHistory.holdSession()
        onOpenArticle(item)
    }
    val stories = rememberStories(state.items, state.verification.readable, hidden)
    val feed = remember(stories, since, settled) { NewsStories.split(stories, since, settled) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Gli indici fissi della lista: 0 = Ascolta, 1 = Da leggere, poi le nuove, poi (se non ce ne sono) la riga "niente di nuovo", poi il segno.
    val markerIndex = if (since != null && !feed.isEmpty) 2 + feed.fresh.size + (if (feed.fresh.isEmpty()) 1 else 0) else null
    val markerNow by rememberUpdatedState(markerIndex)
    LaunchedEffect(Unit) {
        // Ritoccare News: dal fondo si torna al segno, dal segno (o sopra) in cima.
        TabReselect.events.collect {
            if (it != Routes.NEWS) return@collect
            val m = markerNow
            if (m != null && listState.firstVisibleItemIndex > m) listState.animateScrollToItem(m) else listState.animateScrollToItem(0)
        }
    }
    NewArrivals(feed, listState)

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val barrier = rememberVisitBarrier(listState, markerIndex.takeIf { feed.fresh.isNotEmpty() })
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            TopLevelBar(
                title = "News",
                subtitle = NewsHistory.headline(
                    feed.all.size,
                    if (since == null) null else feed.fresh.size,
                    feed.all.mapNotNull { it.newest }.maxOrNull(),
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        // Le notizie si aggiornano da sole; trascinare dall'alto apre la ricerca.
        AutoRefresh { viewModel.refreshIfStale() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
            val waiting = feed.isEmpty && (state.loading || state.verification.pending > 0 || state.visibleItems.isNotEmpty())
            when {
                // Le righe grigie dove arriveranno le storie, invece della rotella in mezzo.
                waiting -> PlaceholderList(Modifier.padding(top = 8.dp), label = "Carico le notizie")
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = viewModel::refresh)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(barrier),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 16.dp,
                    ),
                ) {
                    item(key = "listen") {
                        ListenBlock(feed, since, Modifier.padding(bottom = 12.dp))
                    }
                    // Da leggere (1.482): gli articoli salvati, prima stavano nel calendario. Sempre un elemento
                    // della lista (vuoto se non c'è niente), così gli indici fissi sotto non cambiano.
                    item(key = "toread") { ToReadBlock(Modifier.padding(bottom = 8.dp)) }
                    itemsIndexed(feed.fresh, key = { _, s -> "n:" + s.key }) { index, story ->
                        Column(Modifier.animateItem()) {
                            val actions = { ActionBubble.show(*storyActions(context, story) { NewsHistory.settle(story.items.map { it.link }) }) }
                            if (index == 0) {
                                StoryLead(story, read = story.items.any { it.link in read }, onOpen = open, onLongClick = actions, modifier = Modifier.padding(bottom = 6.dp))
                            } else {
                                StoryDivider()
                                SwipeToRead(story) {
                                    StoryRow(story, read = { it in read }, onOpen = open, onLongClick = actions, thumb = 0.dp)
                                }
                            }
                        }
                    }
                    if (markerIndex != null) {
                        item(key = "marker") {
                            NewsVisitMarker(
                                NewsStories.markerLabel(since!!, Instant.now(), ::newsClock, nothingNew = feed.fresh.isEmpty()),
                                Modifier.animateItem().padding(vertical = 6.dp),
                            )
                        }
                    }
                    itemsIndexed(feed.seen, key = { _, s -> "v:" + s.key }) { index, story ->
                        Column(Modifier.animateItem()) {
                            val actions = { ActionBubble.show(*storyActions(context, story) {}) }
                            // Come un giornale: anche fra le già viste la prima storia ha la foto grande, poi titoli puliti.
                            if (index == 0) {
                                StoryLead(story, read = story.items.any { it.link in read }, onOpen = open, onLongClick = actions, modifier = Modifier.padding(vertical = 6.dp))
                            } else {
                                StoryDivider()
                                StoryRow(story, read = { it in read }, onOpen = open, onLongClick = actions, dim = 0.85f, thumb = 0.dp)
                            }
                        }
                    }
                    if (feed.isEmpty) item(key = "empty") {
                        Text(
                            if (hidden.isNotEmpty()) "Nessuna notizia leggibile dalle fonti che segui. Le fonti nascoste si riattivano in Impostazioni › News."
                            else "Nessuna notizia leggibile in questo momento.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    // Una fonte ferma non merita un posto in cima: una riga grigia in fondo.
                    val failed = state.failures.map { it.source.name }
                    if (failed.isNotEmpty()) item(key = "failed") {
                        NewsQuietLine(NewsLayout.failureDetail(failed), Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** Portare una storia di lato la segna come letta: esce alla velocità del dito e riappare tra le già viste. */
@Composable
private fun SwipeToRead(story: NewsStory, content: @Composable () -> Unit) {
    val swipe = rememberSwipeAwayState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Box(
        Modifier.swipeAway(swipe) {
            NewsHistory.init(context)
            val links = story.items.map { it.link }
            links.forEach(NewsHistory::markRead)
            NewsHistory.settle(links)
            scope.launch { swipe.reset() }
        },
    ) { content() }
}

/**
 * L'unico blocco colorato della pagina: ascoltare le storie nuove (le più importanti per prime),
 * o il punto del giorno se non c'è niente di nuovo.
 */
@Composable
private fun ListenBlock(feed: NewsFeed, since: Instant?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val listening by RecapSpeaker.state.collectAsStateWithLifecycle()
    val fresh = feed.fresh.isNotEmpty() && since != null
    val pool = if (fresh) feed.fresh else feed.all.filter { s -> s.newest?.let { java.time.Duration.between(it, Instant.now()).toHours() < 24 } ?: true }
    val chosen = remember(pool) {
        pool.sortedWith(compareByDescending<NewsStory> { it.sourceCount }.thenByDescending { it.newest ?: Instant.EPOCH })
            .take(RecapReader.MAX_THEMES)
    }
    if (chosen.isEmpty()) return
    val intro = if (fresh) {
        val n = feed.fresh.size
        (if (n == 1) "Una notizia nuova " else "$n notizie nuove ") + NewsStories.sinceLabel(since!!, Instant.now(), ::newsClock) + "."
    } else null
    val script = remember(chosen, intro) { storiesScript(chosen, intro) }
    val minutes = NewsStories.minutes(script.sumOf { it.length })
    val playing = listening != null
    CanvasCard(
        onClick = { if (playing) RecapSpeaker.stop() else listenStories(context, chosen, intro) },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = modifier.fillMaxWidth().bouncyPress(0.98f),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (playing) Icons.Filled.Stop else Icons.Outlined.Headphones, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        playing -> "Smetti di ascoltare"
                        !fresh -> "Ascolta il punto del giorno"
                        feed.fresh.size == 1 -> "Ascolta la notizia nuova"
                        feed.fresh.size <= RecapReader.MAX_THEMES -> "Ascolta le ${feed.fresh.size} nuove"
                        else -> "Ascolta le ${chosen.size} nuove principali"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$minutes min · le più riprese prima",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Le notizie nuove che arrivano mentre leggi non fanno saltare la lista: se sei in cima entrano scivolando,
 * altrimenti compare il fumetto in basso "2 nuove notizie · Mostra".
 */
@Composable
private fun NewArrivals(feed: NewsFeed, listState: LazyListState) {
    val context = LocalContext.current
    var baseline by remember { mutableStateOf<Instant?>(null) }
    val newest = feed.all.mapNotNull { it.newest }.maxOrNull()
    LaunchedEffect(newest) {
        val base = baseline
        baseline = newest
        if (base == null || newest == null || !newest.isAfter(base)) return@LaunchedEffect
        val added = feed.fresh.count { s -> s.newest?.isAfter(base) == true }
        if (added == 0 || listState.firstVisibleItemIndex <= 1) return@LaunchedEffect
        CaptureFeedback.tell(
            context,
            if (added == 1) "1 nuova notizia" else "$added nuove notizie",
            Icons.Outlined.Newspaper,
            actionLabel = "Mostra",
        ) { listState.animateScrollToItem(0) }
    }
}

/**
 * La piccola resistenza sul segno "ULTIMA VISITA", come su "OGGI" nel Calendario ma al contrario: qui le
 * nuove stanno sopra, e scorrendo verso le già viste la lista si ferma sul segno con una vibrazione leggera.
 * Riusa la regola pura di [PastBarrier] guardando la lista capovolta.
 */
@Composable
private fun rememberVisitBarrier(listState: LazyListState, markerIndex: Int?): NestedScrollConnection {
    val haptic = rememberHaptic()
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    val index by rememberUpdatedState(markerIndex)
    return remember(listState, haptic, threshold) {
        object : NestedScrollConnection {
            var pull = 0f
            var unlocked = false
            /** Dove sta il segno, visto dalla lista capovolta: positivo = già tra le viste. */
            fun boundary(): Float? {
                val m = index ?: return null
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == m }
                return when {
                    item != null -> -item.offset.toFloat()
                    listState.firstVisibleItemIndex > m -> 1e9f
                    else -> -1e9f
                }
            }
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val s = PastBarrier.step(
                    boundary(), -available.y, pull, unlocked, threshold,
                    fling = source != NestedScrollSource.UserInput,
                )
                pull = s.pull
                unlocked = s.unlocked
                if (s.bump) haptic(Haptic.TICK)
                if (s.through) haptic(Haptic.THRESHOLD)
                return Offset(0f, -s.consumed)
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) { Text("Riprova") }
    }
}
