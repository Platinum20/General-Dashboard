package com.platinum20.dashboard.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.MediaLauncher
import com.platinum20.dashboard.news.NewsHistory
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.news.NewsStories
import com.platinum20.dashboard.news.ReadingSpots
import com.platinum20.dashboard.news.RecapReader
import com.platinum20.dashboard.news.RecapSpeaker
import com.platinum20.dashboard.news.reader.ArticleVerifier
import com.platinum20.dashboard.news.reader.ArticleVideos
import com.platinum20.dashboard.news.reader.ReaderArticle
import com.platinum20.dashboard.news.reader.ReaderBlock
import com.platinum20.dashboard.news.reader.ReaderUiState
import com.platinum20.dashboard.news.reader.ReaderViewModel
import com.platinum20.dashboard.news.reader.TextRun
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.settings.AppSettings
import kotlinx.coroutines.flow.first

/** Il testo del lettore: la dimensione più piccola, fissa (Antonio non vuole il tasto "Aa"). */
private const val TEXT_SCALE = 0.9f

/** La prima immagine dell'articolo, per l'anteprima nelle note. */
internal fun ReaderArticle.coverImage(): String? =
    leadImage ?: blocks.firstNotNullOfOrNull { (it as? ReaderBlock.Image)?.url ?: (it as? ReaderBlock.Video)?.poster }

/** Il testo da leggere a voce: paragrafi, citazioni, punti elenco e sottotitoli, uno per parte. */
internal fun ReaderArticle.spokenParagraphs(): List<String> = blocks.mapNotNull { block ->
    when (block) {
        is ReaderBlock.Heading -> block.text
        is ReaderBlock.Paragraph -> block.runs.joinToString("") { it.text }
        is ReaderBlock.Quote -> block.runs.joinToString("") { it.text }
        is ReaderBlock.ListItem -> block.runs.joinToString("") { it.text }
        else -> null
    }?.takeIf { it.isNotBlank() }
}

/**
 * Lettore vero e proprio: dalla pagina del giornale prende solo titolo, testo, immagini e video e li mostra
 * con lo stile dell'app. Dalla 1.356 ha la forma delle altre schede (DetailPage): in alto Indietro e
 * "ANSA · 20 min fa · 4 min di lettura", in basso Ascolta e "Altro". Il titolo arriva volando dalla lista.
 */
@Composable
fun ReaderScreen(
    sourceName: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onOpenNext: (NewsItem) -> Unit,
    viewModel: ReaderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val url = viewModel.url
    val loaded = (state as? ReaderUiState.Loaded)?.article
    val title = loaded?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle

    // Integrazione News → Note: l'articolo resta in Note con il link e l'immagine; se c'è già, si toglie.
    NotesStore.init(context)
    val notes by NotesStore.data.collectAsStateWithLifecycle()
    val link = SavedLink.Article(url, sourceName, title, loaded?.coverImage().orEmpty())
    val saved = remember(notes.notes, url) { SavedLinks.notesWith(notes.notes, link).isNotEmpty() }

    val news by NewsStore.result.collectAsStateWithLifecycle()
    val item = remember(news, url) { news?.items?.firstOrNull { it.link == url } }
    val words = remember(loaded) { loaded?.spokenParagraphs()?.sumOf { p -> p.split(' ').count { it.isNotBlank() } } }
    val meta = listOfNotNull(
        sourceName.ifBlank { null },
        item?.publishedAt?.let(::relativeTime),
        words?.let { "${NewsStories.readingMinutes(it)} min di lettura" },
    ).joinToString(" · ").ifBlank { "Articolo" }

    // Dove eri arrivato: uscendo a metà, la volta dopo si riparte da lì.
    val scroll = rememberScrollState()
    ReadingSpots.init(context)
    val isLoaded by rememberUpdatedState(loaded != null)
    LaunchedEffect(loaded != null) {
        if (loaded == null) return@LaunchedEffect
        val spot = ReadingSpots.get(url) ?: return@LaunchedEffect
        snapshotFlow { scroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
        scroll.animateScrollTo(spot)
    }
    DisposableEffect(url) {
        onDispose { if (isLoaded) ReadingSpots.put(url, scroll.value, scroll.maxValue) }
    }

    DetailPage(
        onBack = onBack,
        onEdit = null,
        onDelete = null,
        meta = meta,
        primary = loaded?.let { article ->
            DetailAction(Icons.Outlined.Headphones, "Ascolta") {
                val script = RecapReader.articleScript(title, article.spokenParagraphs())
                RecapSpeaker.play(context, script, title = title, labels = script.map(RecapReader::chapterLabel))
            }
        },
        more = listOf(
            if (saved) BubbleAction(Icons.Filled.Bookmark, "Togli dalle note") { SavedLinks.remove(context, link) }
            else BubbleAction(Icons.Outlined.BookmarkAdd, "Salva nelle note") { SavedLinks.save(context, link) },
            BubbleAction(Icons.Outlined.Share, "Condividi il link") {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title)
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            },
        ),
        scroll = scroll,
        topExtra = { ReadingProgress { if (scroll.maxValue in 1 until Int.MAX_VALUE) scroll.value / scroll.maxValue.toFloat() else 0f } },
    ) {
        // Tenendo premuto il titolo: "Salva nelle note", come per le cose del resto dell'app.
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = (26 * TEXT_SCALE).sp, lineHeight = 1.25.em),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .sharedItem(newsSharedKey(url))
                .pointerInput(url) { detectTapGestures(onLongPress = { SavedLinks.offer(link) }) },
        )
        when (val s = state) {
            // Il titolo è già al suo posto; sotto, le righe grigie dove arriverà il testo.
            is ReaderUiState.Loading -> Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.step, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PlaceholderLine(1f, 180.dp)
                listOf(0.95f, 0.9f, 0.97f, 0.6f, 0.92f, 0.88f, 0.4f).forEach { PlaceholderLine(it, 14.dp) }
            }
            is ReaderUiState.Loaded -> {
                ArticleBody(s.article, onOpenLink)
                ArticleEnd(url, onOpenArticle, onOpenNext)
            }
            ReaderUiState.NoText -> ReaderMessage(
                "Non riesco a ricavare il testo di questo articolo: forse è riservato agli abbonati o è solo un video.",
                onRetry = viewModel::load,
            )
            is ReaderUiState.Failed -> ReaderMessage(s.message, onRetry = viewModel::load)
        }
    }
}

/** La barra sottile sotto la riga in alto: quanto hai letto dell'articolo. */
@Composable
private fun ReadingProgress(fraction: () -> Float) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Box(
        Modifier
            .padding(start = 20.dp, top = 8.dp)
            .fillMaxWidth()
            .height(2.dp)
            .clip(CircleShape)
            .background(track)
            .drawBehindProgress(primary, fraction),
    )
}

private fun Modifier.drawBehindProgress(color: Color, fraction: () -> Float): Modifier =
    drawBehind { drawRect(color, size = size.copy(width = size.width * fraction().coerceIn(0f, 1f))) }

@Composable
private fun ArticleBody(article: ReaderArticle, onOpenLink: (String) -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = (18 * TEXT_SCALE).sp, lineHeight = 1.6.em)
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        article.byline?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        article.leadImage?.let { ArticleImage(it, caption = null) }
        article.blocks.forEach { block ->
            when (block) {
                is ReaderBlock.Heading -> Text(
                    block.text,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = (21 * TEXT_SCALE).sp),
                    modifier = Modifier.padding(top = 8.dp),
                )
                is ReaderBlock.Paragraph -> Text(block.runs.toAnnotated(linkColor, onOpenLink), style = bodyStyle)
                is ReaderBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        block.runs.toAnnotated(linkColor, onOpenLink),
                        style = bodyStyle.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ReaderBlock.ListItem -> Row {
                    Text(block.marker, style = bodyStyle, modifier = Modifier.width(28.dp))
                    Text(block.runs.toAnnotated(linkColor, onOpenLink), style = bodyStyle)
                }
                is ReaderBlock.Image -> ArticleImage(block.url, block.caption)
                is ReaderBlock.Video -> ArticleVideo(block)
                ReaderBlock.Divider -> HorizontalDivider()
            }
        }
    }
}

/**
 * In fondo all'articolo: le altre testate che raccontano lo stesso fatto e la prossima storia nuova,
 * così si legge di seguito senza tornare alla lista.
 */
@Composable
private fun ArticleEnd(url: String, onOpenArticle: (NewsItem) -> Unit, onOpenNext: (NewsItem) -> Unit) {
    val context = LocalContext.current
    NewsHistory.init(context)
    val news by NewsStore.result.collectAsStateWithLifecycle()
    val verification by ArticleVerifier.state.collectAsStateWithLifecycle()
    val hidden by AppSettings.hiddenNewsSources.collectAsStateWithLifecycle()
    val readList by NewsHistory.read.collectAsStateWithLifecycle()
    val stories = rememberStories(news?.items.orEmpty(), verification.readable.takeIf { it.isNotEmpty() }, hidden)
    val story = remember(stories, url) { NewsStories.storyOf(stories, url) }
    val others = story?.items?.filter { it.link != url }.orEmpty()
    val next = remember(stories, url, readList) {
        NewsStories.next(NewsStories.split(stories, NewsHistory.since(), NewsHistory.settled.value), url, readList.toSet() + url)
    }
    val open = { item: NewsItem, replace: Boolean ->
        NewsHistory.markRead(item.link)
        NewsHistory.holdSession()
        if (replace) onOpenNext(item) else onOpenArticle(item)
    }
    if (others.isEmpty() && next == null) return
    Spacer(Modifier.height(12.dp))
    if (others.isNotEmpty()) {
        SectionHeader("Le altre fonti su questa storia")
        others.forEach { other ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .bouncyPress()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { open(other, false) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text(
                    other.source.name + (other.publishedAt?.let { "  ·  " + relativeTime(it) } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(other.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    next?.let { story ->
        Spacer(Modifier.height(4.dp))
        CanvasCard(
            onClick = { open(story.headline, true) },
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().bouncyPress(0.98f),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("PROSSIMA NUOVA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(2.dp))
                    Text(story.headline.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Video dentro l'articolo: un riquadro nostro con l'anteprima. Toccandolo il video parte nel player di
 * Canvas (YouTube nel suo player, un file video nel player unico), mai nel riquadro del sito. I video
 * che non sappiamo riprodurre da noi non si mostrano.
 */
@Composable
private fun ArticleVideo(video: ReaderBlock.Video) {
    val target = remember(video) { ArticleVideos.playable(video) } ?: return
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val youtube = ArticleVideos.youtubeId(video.url)
    val poster = video.poster ?: youtube?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .bouncyPress(0.98f)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black)
            .clickable {
                if (youtube != null) inApp?.openWeb(target, "Video")
                else inApp?.openRoute(MediaLauncher.playVideo(context, Uri.parse(target)))
            },
        contentAlignment = Alignment.Center,
    ) {
        poster?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Guarda il video", tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun ReaderMessage(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onRetry) { Text("Riprova") }
    }
}

@Composable
private fun ArticleImage(url: String, caption: String?) {
    Column(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = url,
            contentDescription = caption,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        caption?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Trasforma i pezzi di testo in un testo con grassetti, corsivi e link toccabili. */
private fun List<TextRun>.toAnnotated(linkColor: Color, onOpenLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        for (run in this@toAnnotated) {
            val style = SpanStyle(
                fontWeight = if (run.bold) FontWeight.Bold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
            )
            val link = run.link
            if (link != null) {
                withLink(
                    LinkAnnotation.Url(
                        url = link,
                        styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { onOpenLink(link) },
                    )
                ) {
                    withStyle(style) { append(run.text) }
                }
            } else {
                withStyle(style) { append(run.text) }
            }
        }
    }
