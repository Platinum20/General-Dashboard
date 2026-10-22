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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.news.NewsHistory
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
    val open = { item: NewsItem ->
        NewsHistory.markRead(item.link)
        onOpenArticle(item)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Il punto del giorno") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { inner ->
        when {
            state.themes.isNotEmpty() -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = inner.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "I temi di cui parlano più testate nelle ultime 24 ore, raggruppati in automatico.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                itemsIndexed(state.themes) { index, theme ->
                    CanvasCard(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            // Il primo articolo è il titolo del tema; sopra solo quanto se ne parla.
                            Text(
                                "${index + 1} · " + if (theme.items.size > 1) {
                                    "${theme.items.size} articoli da ${theme.sourceCount} ${if (theme.sourceCount == 1) "fonte" else "fonti"}"
                                } else "1 articolo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                            )
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
