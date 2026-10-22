package com.platinum20.dashboard.ui.screens

import android.view.View
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.platinum20.dashboard.media.AdBlocker
import com.platinum20.dashboard.media.MediaPageListener
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.settings.AppSettings

/** YouTube o Spotify dentro l'app, con l'account di Impostazioni e il blocco pubblicità. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPlayerScreen(
    service: MediaService,
    startUrl: String?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val adBlock by AppSettings.adBlock.collectAsState()
    val blockedCount by AdBlocker.blocked.collectAsState()

    var pageTitle by remember { mutableStateOf<String?>(null) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var historyBack by remember { mutableStateOf(false) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    val webView = remember(service) { MediaWebViews.obtain(context, service, startUrl) }
    // Il contenitore in cui questa schermata ha messo la pagina (non è stato: non deve ricomporre).
    val hostParent = remember { arrayOfNulls<android.view.ViewParent>(1) }

    DisposableEffect(service) {
        MediaWebViews.setMiniMode(service, false)
        historyBack = webView.canGoBack()
        pageTitle = webView.title
        MediaWebViews.setListener(service, object : MediaPageListener {
            override fun onProgress(progress: Int) {
                loadProgress = progress / 100f
                loading = progress < 100
            }

            override fun onTitle(title: String?) {
                pageTitle = title
            }

            override fun onHistoryChanged(canGoBack: Boolean) {
                historyBack = canGoBack
            }

            override fun onShowFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
            }

            override fun onHideFullscreen() {
                fullscreenView = null
                fullscreenCallback = null
            }
        })
        onDispose {
            fullscreenCallback?.onCustomViewHidden()
            MediaWebViews.setListener(service, null)
            MediaWebViews.detach(service, hostParent[0])
        }
    }

    val exitFullscreen = {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
    }
    // YouTube resta visibile in un riquadro uscendo dall'app (picture-in-picture).
    val inPip = rememberPictureInPicture(enabled = service == MediaService.YOUTUBE)
    FullscreenEffect(enabled = fullscreenView != null && !inPip)

    // Indietro: prima esce dallo schermo intero, poi torna alle pagine precedenti, infine chiude.
    BackHandler(enabled = fullscreenView != null) { exitFullscreen() }
    BackHandler(enabled = fullscreenView == null && historyBack) { webView.goBack() }

    Scaffold(
        topBar = {
            if (fullscreenView == null && !inPip) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(service.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(
                                    if (adBlock) "Pubblicità bloccate: $blockedCount"
                                    else pageTitle?.takeIf { it.isNotBlank() } ?: service.homeHost,
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
                            IconButton(onClick = { webView.loadUrl(service.homeUrl) }) {
                                Icon(Icons.Outlined.Home, contentDescription = "Home")
                            }
                            IconButton(onClick = { webView.reload() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Ricarica")
                            }
                        },
                    )
                    if (loading) {
                        LinearProgressIndicator(progress = { loadProgress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (fullscreenView == null && !inPip) inner else PaddingValues())
        ) {
            AndroidView(
                factory = {
                    (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                    webView
                },
                update = { hostParent[0] = it.parent },
                modifier = Modifier.fillMaxSize(),
            )
            fullscreenView?.let { view ->
                key(view) {
                    AndroidView(
                        factory = { view },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    )
                }
            }
        }
    }
}
