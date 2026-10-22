package com.platinum20.dashboard.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.platinum20.dashboard.adblock.AdBlocker

/** Pagina originale del giornale dentro l'app, per quando il lettore non riesce a ricavare il testo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    url: String,
    sourceName: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var pageTitle by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(url) }
    var progress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val webView = remember {
        createWebView(
            context = context,
            onTitle = { pageTitle = it },
            onProgress = { progress = it / 100f },
            onStarted = { loading = true; failed = false; currentUrl = it },
            onFinished = { loading = false },
            onHistoryChanged = { canGoBack = it },
            onMainFrameError = { failed = true; loading = false },
        ).also { it.loadUrl(url) }
    }

    // Tema scuro anche per le pagine: i siti con una loro versione scura la usano, gli altri vengono scuriti.
    DisposableEffect(dark) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
        }
        onDispose {}
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // Il tasto indietro torna prima alle pagine precedenti dell'articolo, poi alle notizie.
    BackHandler(enabled = canGoBack) { webView.goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                sourceName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            Text(
                                pageTitle?.takeIf { it.isNotBlank() } ?: Uri.parse(currentUrl).host.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                    actions = {
                        IconButton(onClick = { shareLink(context, currentUrl, pageTitle) }) {
                            Icon(Icons.Outlined.Share, contentDescription = "Condividi")
                        }
                    },
                )
                if (loading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (failed) {
                ArticleError(onRetry = { failed = false; webView.reload() })
            }
        }
    }
}

@Composable
private fun ArticleError(onRetry: () -> Unit) {
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
        Text(
            "Impossibile caricare l'articolo.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Riprova") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    onTitle: (String?) -> Unit,
    onProgress: (Int) -> Unit,
    onStarted: (String) -> Unit,
    onFinished: () -> Unit,
    onHistoryChanged: (canGoBack: Boolean) -> Unit,
    onMainFrameError: () -> Unit,
): WebView = WebView(context).apply {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        mediaPlaybackRequiresUserGesture = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
    }
    // Le pagine dei giornali sono piene di pubblicità: stesse liste di uBlock Origin dei player.
    val adBlock = AdBlocker.attach(this)
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
        override fun onReceivedTitle(view: WebView, title: String?) = onTitle(title)
    }
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            adBlock.intercept(request)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url
            // Tutto resta nell'app: le pagine web qui, gli inviti ad aprire un'altra app (intent://)
            // con il loro indirizzo web di riserva; mail e telefono vanno al compositore e al tastierino.
            if (target.scheme == "http" || target.scheme == "https") return false
            InAppLinks.intentFallback(target.toString())?.let { view.loadUrl(it) }
            if (target.scheme == "mailto" || target.scheme == "tel") openSystemAction(view.context, target)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            adBlock.onPage(view, url)
            onStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) = onFinished()

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            adBlock.onPage(view, url)
            onHistoryChanged(view.canGoBack())
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) onMainFrameError()
        }
    }
}

/** Scrivere una mail o chiamare un numero: le uniche cose che passano al telefono. */
private fun openSystemAction(context: Context, uri: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun shareLink(context: Context, url: String, title: String?) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    context.startActivity(Intent.createChooser(send, "Condividi articolo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
