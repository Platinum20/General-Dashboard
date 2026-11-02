package com.platinum20.dashboard.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.browserUserAgent
import com.platinum20.dashboard.media.inFrame
import com.platinum20.dashboard.media.loadWhenAttached
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import kotlinx.coroutines.launch

/**
 * Il collegamento ufficiale (API di Spotify o di Google) per un servizio: prima era la chiave in
 * alto nelle pagine Musica e Video, ora si apre da Impostazioni › Account. Restituisce l'azione
 * da collegare a un tocco.
 */
@Composable
internal fun rememberOfficialConnect(service: MediaService): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var spotifyUrl by remember { mutableStateOf<String?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (!YouTubeAuth.handleConsent(context, result.data)) CaptureFeedback.tell(context, "Accesso annullato")
    }
    spotifyUrl?.let { url -> SpotifyAuthDialog(url, onDone = { spotifyUrl = null }) }
    return {
        when (service) {
            MediaService.SPOTIFY -> spotifyUrl = SpotifyAuth.loginUrl().toString()
            MediaService.YOUTUBE -> scope.launch {
                when (val outcome = YouTubeAuth.authorize(context)) {
                    is YouTubeAuth.Outcome.Authorized -> Unit
                    is YouTubeAuth.Outcome.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(outcome.sender).build())
                    is YouTubeAuth.Outcome.Failed -> CaptureFeedback.tell(
                        context,
                        if (outcome.notConfigured) "Google non riconosce ancora l'app" else "Accesso non riuscito: ${outcome.message}",
                    )
                }
            }
        }
    }
}

/** Login di Spotify dentro l'app: la pagina di autorizzazione in una finestra, il ritorno lo intercettiamo noi. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SpotifyAuthDialog(url: String, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                    Text("Collega Spotify", style = MaterialTheme.typography.titleMedium)
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = browserUserAgent(settings.userAgentString)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val target = request.url
                                    if (SpotifyAuth.isRedirect(target)) {
                                        SpotifyAuth.handleRedirect(target)
                                        onDone()
                                        return true
                                    }
                                    return target.scheme != "http" && target.scheme != "https"
                                }
                            }
                            loadWhenAttached(url)
                        }.inFrame(ctx)
                    },
                    onRelease = { frame -> (frame.getChildAt(0) as? WebView)?.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
