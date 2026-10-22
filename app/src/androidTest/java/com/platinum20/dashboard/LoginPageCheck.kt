package com.platinum20.dashboard

import android.graphics.Bitmap
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.browserUserAgent
import com.platinum20.dashboard.media.createMediaWebView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.platinum20.dashboard.settings.AppTheme
import com.platinum20.dashboard.ui.screens.LoginScreen
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Apre in una vera WebView le pagine di accesso (come fa la schermata "Accedi a") e salva una foto
 * e il testo che si vede, per capire perché su un telefono restano nere. Parte solo nel workflow apposito.
 */
@RunWith(AndroidJUnit4::class)
class LoginPageCheck {
    private val outDir = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "login-check").apply { mkdirs() }

    @Test
    fun spotify() = check("spotify", MediaService.SPOTIFY.loginUrl)

    @Test
    fun spotifyAuthorize() = check(
        "spotify-authorize",
        "https://accounts.spotify.com/authorize?client_id=802a1bc467c041538598820b98e20f63&response_type=code" +
            "&redirect_uri=com.platinum20.dashboard%3A%2F%2Fspotify-callback&code_challenge_method=S256" +
            "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&scope=streaming",
    )

    /** La schermata vera dell'app ("Accedi a Spotify"), dentro il vetro come nella navigazione. */
    @Test
    fun spotifyInApp() = inCompose("spotify-in-app", haze = true) { LoginScreen(MediaService.SPOTIFY, onDone = {}) }

    @Test
    fun spotifyInAppNoHaze() = inCompose("spotify-in-app-nohaze", haze = false) { LoginScreen(MediaService.SPOTIFY, onDone = {}) }

    /** WebView semplice dentro Compose: carica subito, come la schermata di accesso. */
    @Test
    fun composeWebView() = inCompose("compose-webview", haze = false) {
        AndroidView(
            factory = { ctx ->
                createMediaWebView(ctx, MediaService.SPOTIFY, blockAds = false) { null }.apply {
                    settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(ctx))
                    loadUrl(MediaService.SPOTIFY.loginUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun inCompose(name: String, haze: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                val state = remember { HazeState() }
                GeneralDashboardTheme(AppTheme.CANVAS) {
                    Box(if (haze) Modifier.fillMaxSize().hazeSource(state) else Modifier.fillMaxSize()) { content() }
                }
            }
        }
        Thread.sleep(25_000)
        scenario.onActivity { activity ->
            val web = findWebView(activity.window.decorView)
            Log.i("LoginCheck", "$name webview ${web?.width}x${web?.height} layer=${web?.layerType} hw=${web?.isHardwareAccelerated}")
            web?.evaluateJavascript(DUMP) { Log.i("LoginCheck", "$name page $it") }
        }
        Thread.sleep(2_000)
        val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(outDir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 90, it) }
        scenario.close()
    }

    private fun findWebView(view: android.view.View): WebView? {
        if (view is WebView) return view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun check(name: String, url: String) {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        lateinit var webView: WebView
        scenario.onActivity { activity ->
            webView = createMediaWebView(activity, MediaService.SPOTIFY, blockAds = false) { null }
            webView.settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(activity))
            val chrome = webView.webChromeClient
            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.i("LoginCheck", "$name console ${message.messageLevel()} ${message.message()} @${message.sourceId()}:${message.lineNumber()}")
                    return true
                }
                override fun onProgressChanged(view: WebView, newProgress: Int) { chrome?.onProgressChanged(view, newProgress) }
            }
            Log.i("LoginCheck", "$name ua ${webView.settings.userAgentString}")
            activity.setContentView(webView)
            webView.loadUrl(url)
        }
        Thread.sleep(25_000)
        scenario.onActivity {
            webView.evaluateJavascript(DUMP) { Log.i("LoginCheck", "$name page $it") }
        }
        Thread.sleep(2_000)
        val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(outDir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 90, it) }
        scenario.close()
    }

    private companion object {
        const val DUMP = "(function(){var r=document.querySelector('main,#root,#__next,form')||document.body;var b=r.getBoundingClientRect();" +
            "return location.href.slice(0,60)+' | vp='+innerWidth+'x'+innerHeight+' dpr='+devicePixelRatio+' | root='+r.tagName+'#'+r.id+' '+Math.round(b.width)+'x'+Math.round(b.height)+'@'+Math.round(b.top)+" +
            "' op='+getComputedStyle(r).opacity+' vis='+getComputedStyle(r).visibility+' | '+document.body.innerText.replace(/\\s+/g,' ').slice(0,200)})()"
    }
}
