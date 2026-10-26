package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.media.browserUserAgent
import android.webkit.WebSettings
import android.net.Uri
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.MediaPageListener
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.createMediaWebView
import com.platinum20.dashboard.media.inFrame
import com.platinum20.dashboard.media.loadWhenAttached
import com.platinum20.dashboard.media.spotify.SpotifyAuth

/** Pagina di accesso ufficiale di Google o Spotify: la password resta tra te e il servizio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    service: MediaService,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(true) }
    var historyBack by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val finish = {
        if (!finished) {
            finished = true
            CookieManager.getInstance().flush()
            Accounts.refresh()
            onDone()
        }
    }

    val webView = remember {
        lateinit var listener: MediaPageListener
        val view = createMediaWebView(context, service, blockAds = false) { listener }
        listener = object : MediaPageListener {
            override fun onProgress(progress: Int) {
                loadProgress = progress / 100f
                loading = progress < 100
            }

            override fun onHistoryChanged(canGoBack: Boolean) {
                historyBack = canGoBack
            }

            override fun onAppLink(uri: Uri): Boolean {
                if (!SpotifyAuth.isRedirect(uri)) return false
                SpotifyAuth.handleRedirect(uri)
                finish()
                return true
            }

            override fun onPageFinished(url: String) {
                // Accesso riuscito quando si torna sul sito del servizio con i cookie dell'account.
                if (Uri.parse(url).host == service.homeHost && Accounts.isLoggedIn(service)) finish()
            }
        }
        // L'accesso si fa sempre con la versione per telefono del sito: con quella "desktop" che usa
        // il web player di Spotify, la pagina di login di Spotify restava nera.
        view.settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(context))
        view.settings.setSupportZoom(false)
        view.settings.builtInZoomControls = false
        // Spotify: accesso ufficiale (OAuth) che dà all'app il permesso di leggere la libreria e
        // suonare. La vecchia pagina di login che portava al web player sui telefoni restava nera.
        val start = if (service == MediaService.SPOTIFY) {
            SpotifyAuth.init(context)
            SpotifyAuth.loginUrl().toString()
        } else {
            service.loginUrl
        }
        view.also { it.loadWhenAttached(start) }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = historyBack) { webView.goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Accedi a ${service.label}") },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                        }
                    },
                    actions = {
                        TextButton(onClick = finish) { Text("Fatto") }
                    },
                )
                if (loading) {
                    LinearProgressIndicator(progress = { loadProgress }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(
                        if (service == MediaService.SPOTIFY) {
                            "Se \"Continua con Google\" non va, scegli \"Continua con email\": ti arriva un codice per entrare."
                        } else {
                            "Accedi come in un browser: le credenziali vanno solo a ${service.label}."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        },
    ) { inner ->
        AndroidView(
            factory = { ctx -> webView.inFrame(ctx) },
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        )
    }
}
