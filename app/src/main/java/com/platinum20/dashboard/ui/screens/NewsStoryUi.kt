package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.news.NewsHistory
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsLayout
import com.platinum20.dashboard.news.NewsStories
import com.platinum20.dashboard.news.NewsStory
import com.platinum20.dashboard.news.RecapReader
import com.platinum20.dashboard.news.RecapSpeaker
import com.platinum20.dashboard.news.key
import com.platinum20.dashboard.news.newest
import com.platinum20.dashboard.news.reader.ArticleCache
import com.platinum20.dashboard.news.sourcesLine
import com.platinum20.dashboard.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** La chiave che lega il titolo di una notizia nella lista al titolo nel lettore (elemento condiviso). */
fun newsSharedKey(link: String) = "news:$link"

/** Le storie (una per fatto) dalle notizie scaricate: il raggruppamento gira fuori dal thread della UI. */
@Composable
fun rememberStories(items: List<NewsItem>, readable: Set<String>?, hidden: Set<String>): List<NewsStory> {
    val stories by produceState(emptyList<NewsStory>(), items, readable, hidden) {
        value = withContext(Dispatchers.Default) { NewsStories.group(items, readable, hidden) }
    }
    return stories
}

/** "ANSA, AGI e altre 2 · 20 min fa": chi ne parla e quando, in una riga sola. */
private fun NewsStory.metaLine(): String = sourcesLine + (newest?.let { "  ·  " + relativeTime(it) } ?: "")

/**
 * Le azioni di una storia tenuta premuta, nel fumetto di vetro: salvare, ascoltare, segnare come letta,
 * nascondere la fonte (si riattiva in Impostazioni › News).
 */
internal fun storyActions(context: Context, story: NewsStory, onMarkRead: () -> Unit): Array<BubbleAction> {
    val lead = story.headline
    return arrayOf(
        BubbleAction(Icons.Outlined.BookmarkAdd, "Leggi dopo", lead.source.name) { SavedLinks.save(context, lead.savedLink()) },
        BubbleAction(Icons.Outlined.Headphones, "Ascolta", "Con la voce di lettura") { listenArticle(context, lead) },
        BubbleAction(Icons.Outlined.DoneAll, "Segna come letta") {
            NewsHistory.init(context)
            story.items.forEach { NewsHistory.markRead(it.link) }
            onMarkRead()
        },
        BubbleAction(Icons.Outlined.VisibilityOff, "Nascondi ${lead.source.name}", "Si riattiva in Impostazioni › News") {
            AppSettings.setNewsSourceHidden(lead.source.id, true)
        },
    )
}

/** Un articolo a voce: il testo già pulito dal lettore se c'è, altrimenti titolo e sommario. */
internal fun listenArticle(context: Context, item: NewsItem) {
    val paragraphs = ArticleCache.get(item.link)?.spokenParagraphs() ?: listOf(item.summary)
    val script = RecapReader.articleScript(item.title, paragraphs)
    RecapSpeaker.play(context, script, title = item.title, labels = script.map(RecapReader::chapterLabel))
}

/** Le storie a voce, come il punto del giorno: un'apertura e una parte per storia. */
internal fun storiesScript(stories: List<NewsStory>, intro: String?): List<String> {
    val script = RecapReader.script(stories, LocalTime.now())
    return if (intro == null || script.isEmpty()) script else listOf(intro) + script.drop(1)
}

internal fun listenStories(context: Context, stories: List<NewsStory>, intro: String?) {
    val script = storiesScript(stories, intro)
    RecapSpeaker.play(context, script, labels = RecapReader.labels(stories))
}

/** Gli articoli già aperti si attenuano davanti a te tornando alla lista, invece di essere già grigi. */
@Composable
private fun Modifier.readFade(read: Boolean, dim: Float = 1f): Modifier {
    val alpha by animateFloatAsState(if (read) READ_ALPHA else dim, tween(Motion.ENTER_MS), label = "letta")
    return graphicsLayer { this.alpha = alpha }
}

/** La storia nuova più importante: foto grande, titolo grande, chi ne parla. Un tocco apre l'articolo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryLead(story: NewsStory, read: Boolean, onOpen: (NewsItem) -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val lead = story.headline
    Column(
        modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(onClick = { onOpen(lead) }, onLongClick = onLongClick)
            .readFade(read)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val image = story.items.firstNotNullOfOrNull { NewsLayout.usableImage(it.imageUrl) }
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
        Text(
            lead.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).sharedItem(newsSharedKey(lead.link)),
        )
        Text(
            story.metaLine(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * Una storia in una riga: titolo, chi ne parla, miniatura sempre della stessa misura. Con più testate il
 * tocco apre la riga sul posto e mostra gli articoli di ognuna; con una sola apre subito l'articolo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryRow(
    story: NewsStory,
    read: (String) -> Boolean,
    onOpen: (NewsItem) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Le già viste: un po' più chiare delle nuove. */
    dim: Float = 1f,
    thumb: Dp = 64.dp,
    horizontal: Dp = 4.dp,
    /** In Home un tocco apre sempre l'articolo: le altre testate stanno nella scheda. */
    expandable: Boolean = true,
) {
    val lead = story.headline
    val many = expandable && story.items.size > 1
    var expanded by remember(story.key) { mutableStateOf(false) }
    val storyRead = story.items.any { read(it.link) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyPress()
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = { if (many) expanded = !expanded else onOpen(lead) },
                    onLongClick = onLongClick,
                )
                .readFade(storyRead, dim)
                .padding(horizontal = horizontal, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    lead.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedItem(newsSharedKey(lead.link)),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    NewsStories.metaLine(story, story.newest?.let { relativeTime(it) }),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val image = story.items.firstNotNullOfOrNull { NewsLayout.usableImage(it.imageUrl) }
            var imageFailed by remember(image) { mutableStateOf(false) }
            if (image != null && !imageFailed && thumb > 0.dp) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .size(thumb)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
        // Le testate scendono una dopo l'altra sotto la riga, con la molla senza rimbalzo.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.settle()) + fadeIn(),
            exit = shrinkVertically(Motion.settle()) + fadeOut(),
        ) {
            Column(Modifier.padding(start = horizontal + 8.dp, bottom = 6.dp)) {
                story.items.forEach { item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .bouncyPress()
                            .clip(MaterialTheme.shapes.medium)
                            .combinedClickable(onClick = { onOpen(item) }, onLongClick = onLongClick)
                            .readFade(read(item.link))
                            .drawBehind {
                                drawLine(
                                    color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.35f),
                                    start = Offset(0f, 8.dp.toPx()),
                                    end = Offset(0f, size.height - 8.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Text(
                            item.source.name + (item.publishedAt?.let { "  ·  " + relativeTime(it) } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Il segno fra nuove e già viste, come "OGGI" nel Calendario: l'ora dell'ultima visita e una linea.
 * Lì la lista fa un po' di resistenza (vedi [rememberVisitBarrier]).
 */
@Composable
fun NewsVisitMarker(label: String, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth().height(36.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = primary, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.weight(1f).height(2.dp).drawBehind {
                drawLine(primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            },
        )
        Box(Modifier.padding(start = 4.dp).size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(primary))
    }
}

/** Una riga grigia, per "Niente di nuovo" e per le fonti che non rispondono. */
@Composable
fun NewsQuietLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp).alpha(0.9f),
    )
}
