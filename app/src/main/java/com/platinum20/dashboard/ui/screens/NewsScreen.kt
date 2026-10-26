package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import com.platinum20.dashboard.Routes
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.platinum20.dashboard.news.NewsHistory
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsLayout
import com.platinum20.dashboard.news.NewsSources
import com.platinum20.dashboard.news.NewsViewModel

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
    val read by NewsHistory.read.collectAsStateWithLifecycle()
    // L'ultima visita si legge all'ingresso e si aggiorna all'uscita: le "nuove" restano tali finché sei qui.
    val since = remember { NewsHistory.lastVisit() }
    DisposableEffect(Unit) { onDispose { NewsHistory.markVisit() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { NewsHistory.markVisit() }
    val open = { item: NewsItem ->
        NewsHistory.markRead(item.link)
        onOpenArticle(item)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        TabReselect.events.collect { if (it == Routes.NEWS) listState.animateScrollToItem(0) }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    // Le notizie uscite dopo l'ultima visita stanno in cima; dalla prima già vista in poi, un separatore.
    val items = state.visibleItems
    val firstSeen = if (since == null) -1 else items.indexOfFirst { it.publishedAt != null && !it.publishedAt.isAfter(since) }
    val newCount = if (since == null) null else if (firstSeen < 0) items.size else firstSeen

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            // Come le altre sezioni: in alto solo il titolo; le fonti scorrono con le notizie.
            TopLevelBar(
                title = "News",
                subtitle = NewsHistory.headline(items.size, newCount, items.mapNotNull { it.publishedAt }.maxOrNull()),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        // Le notizie si aggiornano da sole; trascinare dall'alto apre la ricerca.
        AutoRefresh { viewModel.refreshIfStale() }
        val sources = @Composable { side: androidx.compose.ui.unit.Dp ->
            SourceChips(
                side = side,
                selected = state.selectedSource,
                onSelect = viewModel::selectSource,
                failed = state.failures.map { it.source.name },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
            // Finché non c'è la lista le fonti stanno in cima; con la lista ne sono la prima riga.
            val listShown = !(state.loading && state.items.isEmpty()) &&
                !(state.visibleItems.isEmpty() && state.verification.pending > 0) &&
                !(state.error != null && state.items.isEmpty())
            if (!listShown) Box(Modifier.align(Alignment.TopStart)) { sources(16.dp) }
            when {
                state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.visibleItems.isEmpty() && state.verification.pending > 0 -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Preparo gli articoli…",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.error != null && state.items.isEmpty() -> ErrorState(state.error!!, onRetry = viewModel::refresh)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "sources") { sources(0.dp) }
                    if (state.verification.pending > 0) {
                        item {
                            Text(
                                "Preparo altri ${state.verification.pending} articoli…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    // La prima notizia in grande, le altre come righe compatte con la miniatura.
                    itemsIndexed(items, key = { _, it -> it.link }) { index, item ->
                        Column(Modifier.animateItem()) {
                            if (index == firstSeen && firstSeen > 0) SectionHeader("Già viste", Modifier.padding(top = 8.dp, bottom = 4.dp))
                            val seen = item.link in read
                            if (index == 0) NewsCard(item, read = seen, onClick = { open(item) })
                            else NewsRow(item, read = seen, onClick = { open(item) })
                        }
                    }
                    if (state.visibleItems.isEmpty()) {
                        item {
                            Text(
                                if (state.verification.pending > 0) "Preparo gli articoli di questa fonte…"
                                else "Nessun articolo leggibile da questa fonte in questo momento.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

const val READ_ALPHA = 0.55f

@Composable
private fun SourceChips(selected: String?, onSelect: (String?) -> Unit, failed: List<String>, side: androidx.compose.ui.unit.Dp = 16.dp) {
    var showFailed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = side, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CanvasChip(selected = selected == null, onClick = { onSelect(null) }, label = "Tutte")
        NewsSources.all.forEach { source ->
            CanvasChip(selected = selected == source.id, onClick = { onSelect(source.id) }, label = source.name)
        }
        // Una fonte ferma non merita una riga in cima a tutto: un chip in coda ai filtri, i nomi al tocco.
        NewsLayout.failureChip(failed.size)?.let { label ->
            AssistChip(
                onClick = { showFailed = !showFailed },
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                leadingIcon = { Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp)) },
                shape = CircleShape,
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
    if (showFailed && failed.isNotEmpty()) {
        Text(
            NewsLayout.failureDetail(failed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

/** L'articolo come cosa da salvare nelle note: si riapre nel lettore. */
internal fun NewsItem.savedLink() = SavedLink.Article(link, source.name, title, imageUrl.orEmpty())

@Composable
private fun NewsCard(item: NewsItem, read: Boolean, onClick: () -> Unit) {
    CanvasCard(
        onClick = onClick,
        onLongClick = { SavedLinks.offer(item.savedLink()) },
        // Gli articoli già aperti restano, ma attenuati: l'occhio va subito a quelli da leggere.
        modifier = Modifier.fillMaxWidth().bouncyPress(0.98f).alpha(if (read) READ_ALPHA else 1f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        // Senza foto, o se non si carica, la card resta solo testo: niente riquadro grigio vuoto.
        val image = NewsLayout.usableImage(item.imageUrl)
        var imageFailed by remember(image) { mutableStateOf(false) }
        if (image != null && !imageFailed) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.source.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                item.publishedAt?.let {
                    Text(
                        "  ·  " + relativeTime(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.summary.isNotBlank() && item.summary != item.title) {
                Spacer(Modifier.height(6.dp))
                Text(
                    item.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NewsRow(item: NewsItem, read: Boolean, onClick: () -> Unit) {
    CanvasCard(
        onClick = onClick,
        onLongClick = { SavedLinks.offer(item.savedLink()) },
        modifier = Modifier.fillMaxWidth().bouncyPress().alpha(if (read) READ_ALPHA else 1f),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.source.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    item.publishedAt?.let {
                        Text(
                            "  ·  " + relativeTime(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // La miniatura che non si carica sparisce invece di lasciare un quadrato grigio.
            val image = NewsLayout.usableImage(item.imageUrl)
            var imageFailed by remember(image) { mutableStateOf(false) }
            if (image != null && !imageFailed) {
                Spacer(Modifier.size(12.dp))
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .size(84.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
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
        Button(onClick = onRetry) {
            Text("Riprova")
        }
    }
}
