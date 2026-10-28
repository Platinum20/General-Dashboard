package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.news.NewsHistory
import com.platinum20.dashboard.news.RecapListening
import com.platinum20.dashboard.news.RecapReader
import com.platinum20.dashboard.news.RecapSpeaker
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.CardDefaults
import java.time.LocalTime
import com.platinum20.dashboard.news.NewsItem

/** Tutti i temi del giorno, ciascuno con gli articoli delle varie testate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    onBack: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
) {
    val state = rememberRecapState()
    val context = LocalContext.current
    NewsHistory.init(context)
    // Come in News: gli articoli già aperti restano attenuati.
    val read by NewsHistory.read.collectAsStateWithLifecycle()
    val listening by RecapSpeaker.state.collectAsStateWithLifecycle()
    // Il copione con i capitoli: ogni tema ha l'orario da cui la voce ne parla.
    val script = remember(state.themes) { RecapReader.script(state.themes, LocalTime.now()) }
    val starts = remember(script) { RecapSpeaker.timeline(script) }
    val listen = { fromMs: Long ->
        RecapSpeaker.play(context, script, labels = RecapReader.labels(state.themes), fromMs = fromMs)
    }
    val open = { item: NewsItem ->
        NewsHistory.markRead(item.link)
        onOpenArticle(item)
    }

    Scaffold(
        topBar = {
            TopLevelBar(
                title = "Il punto del giorno",
                onBack = onBack,
            )
        },
    ) { inner ->
        when {
            state.themes.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = inner.calculateBottomPadding() + 16.dp + miniPlayerRoom(),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // L'ascolto sta nella pagina, non in alto: nessuna pagina ha più tasti nella barra del titolo.
                item {
                    CanvasCard(
                        onClick = { if (listening != null) RecapSpeaker.stop() else listen(0) },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (listening != null) Icons.Filled.Stop else Icons.Outlined.Headphones,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (listening != null) "Smetti di ascoltare" else "Ascolta il punto del giorno",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "${state.themes.size} temi di cui parlano più testate nelle ultime 24 ore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                itemsIndexed(state.themes) { index, theme ->
                    CanvasCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            // Il primo articolo è il titolo del tema; sopra solo quanto se ne parla.
                            Row(
                                Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${index + 1} · " + if (theme.items.size > 1) {
                                        "${theme.items.size} articoli da ${theme.sourceCount} ${if (theme.sourceCount == 1) "fonte" else "fonti"}"
                                    } else "1 articolo",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                // Il timestamp del tema nella lettura: toccandolo si ascolta da lì.
                                starts.getOrNull(index + 1)?.let { start ->
                                    // Solo se la voce sta leggendo il punto (e non un articolo).
                                    val recap = listening?.title == RecapReader.TITLE
                                    val here = recap && listening?.part == index + 1
                                    CanvasChip(
                                        label = "▶ " + clockLabel(start),
                                        selected = here,
                                        onClick = { if (recap) RecapSpeaker.seekTo(start) else listen(start) },
                                    )
                                }
                            }
                            theme.items.take(6).forEachIndexed { i, item ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .bouncyPress(0.98f)
                                        .clickable { open(item) }
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                        .alpha(if (item.link in read) READ_ALPHA else 1f)
                                ) {
                                    Text(
                                        item.source.name + (item.publishedAt?.let { "  ·  " + relativeTime(it) } ?: ""),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        item.title,
                                        style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error)
                Button(onClick = state.retry, modifier = Modifier.padding(top = 16.dp)) { Text("Riprova") }
            }
            else -> Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
