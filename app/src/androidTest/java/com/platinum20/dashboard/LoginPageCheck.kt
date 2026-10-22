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
            webView.evaluateJavascript(
                "(function(){return location.href+' | bg='+getComputedStyle(document.body).backgroundColor+' | '+document.body.innerText.replace(/\\s+/g,' ').slice(0,400)})()"
            ) { Log.i("LoginCheck", "$name page $it") }
        }
        Thread.sleep(2_000)
        val shot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(outDir, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 90, it) }
        scenario.close()
    }
}
