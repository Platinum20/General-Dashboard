package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.notes.NotesStore
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.platinum20.dashboard.adblock.AdBlocker
import com.platinum20.dashboard.news.RecapReader
import com.platinum20.dashboard.news.RecapSpeaker
import com.platinum20.dashboard.news.reader.ReaderArticle
import com.platinum20.dashboard.news.reader.ReaderBlock
import com.platinum20.dashboard.news.reader.ReaderUiState
import com.platinum20.dashboard.news.reader.ReaderViewModel
import com.platinum20.dashboard.news.reader.TextRun

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
 * Lettore vero e proprio: dalla pagina del giornale prende solo titolo, testo, immagini e video
 * e li mostra con lo stile dell'app, senza menu, pubblicità o banner del sito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    sourceName: String,
    fallbackTitle: String,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenOriginal: (String) -> Unit,
    viewModel: ReaderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(sourceName.ifBlank { "Articolo" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    // Solo due tasti: ascoltarla con la voce del player e salvarla nelle note.
                    val loaded = (state as? ReaderUiState.Loaded)?.article
                    if (loaded != null) {
                        IconButton(onClick = {
                            val script = RecapReader.articleScript(loaded.title.ifBlank { fallbackTitle }, loaded.spokenParagraphs())
                            RecapSpeaker.play(context, script, title = loaded.title.ifBlank { fallbackTitle }, labels = script.map(RecapReader::chapterLabel))
                        }) {
                            Icon(Icons.Outlined.Headphones, contentDescription = "Ascolta")
                        }
                    }
                    // Integrazione News → Note: l'articolo resta in Note con il link e l'immagine, da leggere dopo.
                    // Se è già nelle note il tasto la toglie: niente copie dello stesso articolo.
                    NotesStore.init(context)
                    val notes by NotesStore.data.collectAsStateWithLifecycle()
                    val link = SavedLink.Article(
                        viewModel.url, sourceName,
                        loaded?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle, loaded?.coverImage().orEmpty(),
                    )
                    val saved = remember(notes.notes, viewModel.url) { SavedLinks.notesWith(notes.notes, link).isNotEmpty() }
                    IconButton(onClick = {
                        if (saved) SavedLinks.remove(context, link) else SavedLinks.save(context, link)
                    }) {
                        if (saved) {
                            Icon(Icons.Filled.Bookmark, contentDescription = "Rimuovi dalle note", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Salva nelle note")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding())
        ) {
            when (val s = state) {
                is ReaderUiState.Loading -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    if (fallbackTitle.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            fallbackTitle,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        s.step,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ReaderUiState.Loaded -> ArticleContent(
                    article = s.article,
                    sourceName = sourceName,
                    scale = TEXT_SCALE,
                    bottomInset = inner.calculateBottomPadding(),
                    onOpenLink = onOpenLink,
                )
                ReaderUiState.NoText -> ReaderMessage(
                    message = "Non riesco a ricavare il testo di questo articolo: forse è riservato agli abbonati o è solo un video. Se il sito chiede il consenso ai cookie, accettalo nella pagina originale e riprova.",
                    onRetry = viewModel::load,
                    onOpenOriginal = { onOpenOriginal(viewModel.url) },
                )
                is ReaderUiState.Failed -> ReaderMessage(
                    message = s.message,
                    onRetry = viewModel::load,
                    onOpenOriginal = { onOpenOriginal(viewModel.url) },
                )
            }
        }
    }
}

@Composable
private fun ArticleContent(
    article: ReaderArticle,
    sourceName: String,
    scale: Float,
    bottomInset: Dp,
    onOpenLink: (String) -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = (18 * scale).sp,
        lineHeight = 1.6.em,
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = bottomInset + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                // Tenendo premuto il titolo: "Salva nelle note", come per le cose del resto dell'app.
                Column(
                    Modifier.pointerInput(article.url) {
                        detectTapGestures(onLongPress = { SavedLinks.offer(SavedLink.Article(article.url, sourceName, article.title, article.coverImage().orEmpty())) })
                    },
                ) {
                    Text(
                        sourceName.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        article.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (26 * scale).sp,
                            lineHeight = 1.25.em,
                        ),
                    )
                    article.byline?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            article.leadImage?.let { lead ->
                item(key = "lead") { ArticleImage(lead, caption = null) }
            }
            items(article.blocks) { block ->
                when (block) {
                    is ReaderBlock.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (21 * scale).sp,
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is ReaderBlock.Paragraph -> Text(
                        block.runs.toAnnotated(linkColor, onOpenLink),
                        style = bodyStyle,
                    )
                    is ReaderBlock.Quote -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
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
                    is ReaderBlock.Video -> ArticleVideo(block, baseUrl = article.url)
                    ReaderBlock.Divider -> HorizontalDivider()
                }
            }
        }
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

/**
 * Video dentro l'articolo: un riquadro che contiene solo il player (YouTube, Vimeo, file video...),
 * non il resto della pagina.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ArticleVideo(video: ReaderBlock.Video, baseUrl: String) {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val webView = remember(video.url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = WebChromeClient()
            // Anche i player incorporati (YouTube, ...) hanno annunci: stesse regole delle altre pagine.
            val adBlock = AdBlocker.attach(this, pageUrl = baseUrl)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    adBlock.intercept(request)

                // Un link che cambia pagina (il titolo del video, "Guarda su YouTube") si apre
                // dentro l'app: nel player di YouTube o nella pagina web interna, mai fuori.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val link = request.url.toString()
                    (InAppLinks.intentFallback(link) ?: link).let { inApp?.openWeb(it, "Video") }
                    return true
                }
            }
            setBackgroundColor(android.graphics.Color.BLACK)
            loadDataWithBaseURL(baseUrl, playerHtml(video), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }
    AndroidView(
        factory = { webView },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large),
    )
}

private fun playerHtml(video: ReaderBlock.Video): String {
    val src = video.url.htmlAttr()
    val player = if (video.embed) {
        """<iframe src="$src" allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowfullscreen></iframe>"""
    } else {
        val poster = video.poster?.let { """ poster="${it.htmlAttr()}"""" }.orEmpty()
        """<video src="$src"$poster controls playsinline preload="metadata"></video>"""
    }
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">""" +
        """<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}""" +
        """iframe,video{position:absolute;top:0;left:0;width:100%;height:100%;border:0}</style></head>""" +
        """<body>$player</body></html>"""
}

private fun String.htmlAttr(): String =
    replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

@Composable
private fun ReaderMessage(message: String, onRetry: () -> Unit, onOpenOriginal: () -> Unit) {
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
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenOriginal) { Text("Apri la pagina originale") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRetry) { Text("Riprova") }
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
