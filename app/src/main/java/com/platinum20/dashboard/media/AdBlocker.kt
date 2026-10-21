package com.platinum20.dashboard.media

import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayInputStream

/**
 * Blocco pubblicità per le pagine web dei player (YouTube, Spotify).
 *
 * Due livelli: le richieste verso server di pubblicità e tracciamento non partono proprio,
 * e su YouTube uno script nasconde i riquadri sponsorizzati e salta gli spot nel video.
 */
object AdBlocker {

    /** Domini di pubblicità e tracciamento: bloccati insieme a tutti i sottodomini. */
    private val blockedHosts = setOf(
        // Google
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "googletagmanager.com", "google-analytics.com", "adservice.google.com", "adservice.google.it",
        "imasdk.googleapis.com", "ads.youtube.com", "partnerad.l.google.com",
        // Reti pubblicitarie
        "adnxs.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "amazon-adsystem.com",
        "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "moatads.com",
        "adform.net", "smartadserver.com", "teads.tv", "yieldmo.com", "33across.com", "sharethrough.com",
        "indexww.com", "bidswitch.net", "adsafeprotected.com", "doubleverify.com", "serving-sys.com",
        "media.net", "zedo.com", "advertising.com", "adtech.de", "contextweb.com", "lijit.com",
        "sovrn.com", "spotxchange.com", "springserve.com", "tremorhub.com", "undertone.com",
        "yieldlab.net", "adition.com", "seedtag.com", "quantcount.com", "inmobi.com", "applovin.com",
        "unityads.unity3d.com", "mopub.com", "adcolony.com", "vungle.com", "chartboost.com",
        // Tracciamento
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mouseflow.com", "crazyegg.com",
        "chartbeat.com", "chartbeat.net", "newrelic.com", "nr-data.net", "branch.io", "app-measurement.com",
        "adjust.com", "appsflyer.com", "kochava.com", "comscore.com", "krxd.net", "bluekai.com",
        "demdex.net", "omtrdc.net", "everesttech.net", "exelator.com", "rlcdn.com", "tapad.com",
        // Spotify
        "adeventtracker.spotify.com", "ads-fa.spotify.com", "analytics.spotify.com", "log.spotify.com",
    )

    /** Percorsi di pubblicità su server che servono anche contenuti normali. */
    private val blockedPaths = mapOf(
        "youtube.com" to listOf(
            "/pagead/", "/api/stats/ads", "/ptracking", "/get_midroll_info", "/pcs/activeview",
            "/youtubei/v1/log_event", "/api/stats/qoe?adformat",
        ),
        "google.com" to listOf("/pagead/", "/ads/", "/adsense/"),
        "spotify.com" to listOf("/ads/", "/ad-logic/"),
    )

    private val _blocked = MutableStateFlow(0)
    /** Richieste bloccate da quando l'app è aperta. */
    val blocked: StateFlow<Int> = _blocked.asStateFlow()

    fun shouldBlock(url: Uri): Boolean {
        val host = url.host?.lowercase() ?: return false
        val hit = matchesHost(host) || matchesPath(host, url)
        if (hit) _blocked.update { it + 1 }
        return hit
    }

    private fun matchesHost(host: String): Boolean {
        var candidate = host
        while (true) {
            if (candidate in blockedHosts) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun matchesPath(host: String, url: Uri): Boolean {
        val full = (url.encodedPath.orEmpty()) + (url.encodedQuery?.let { "?$it" } ?: "")
        return blockedPaths.any { (domain, paths) ->
            (host == domain || host.endsWith(".$domain")) && paths.any { full.startsWith(it) }
        }
    }

    /** Risposta vuota da restituire al posto della richiesta bloccata. */
    fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /** Origini su cui gira lo script che nasconde e salta gli annunci. */
    private val scriptOrigins = setOf("https://m.youtube.com", "https://www.youtube.com", "https://music.youtube.com")

    /**
     * Installa lo script all'avvio di ogni pagina YouTube.
     * Restituisce false se la WebView non lo supporta: in quel caso va iniettato a pagina caricata.
     */
    fun installScript(webView: WebView): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        WebViewCompat.addDocumentStartJavaScript(webView, YOUTUBE_SCRIPT, scriptOrigins)
        return true
    }

    fun injectScript(webView: WebView, url: String?) {
        val host = url?.let { Uri.parse(it).host } ?: return
        if (scriptOrigins.any { Uri.parse(it).host == host }) webView.evaluateJavascript(YOUTUBE_SCRIPT, null)
    }

    // Nasconde i riquadri sponsorizzati; se nel player parte uno spot lo salta (tasto "Salta")
    // o lo manda alla fine, con l'audio disattivato finché dura.
    private val YOUTUBE_SCRIPT = """
        (function () {
          if (window.__gdAdBlock) return;
          window.__gdAdBlock = true;
          var css = [
            'ytm-promoted-sparkles-web-renderer', 'ytm-promoted-video-renderer', 'ytm-companion-ad-renderer',
            'ad-slot-renderer', 'ytm-ad-slot-renderer', 'ytd-ad-slot-renderer', 'ytd-promoted-sparkles-web-renderer',
            'ytd-banner-promo-renderer', 'ytd-in-feed-ad-layout-renderer', 'ytm-promoted-sparkles-text-search-renderer',
            'ytd-player-legacy-desktop-watch-ads-renderer', 'ytm-statement-banner-renderer', 'ytd-statement-banner-renderer',
            '.ytp-ad-overlay-container', '.ytp-ad-module .ytp-ad-image-overlay', '#player-ads', '#masthead-ad',
            'ytm-rich-item-renderer:has(ad-slot-renderer)', 'ytd-rich-item-renderer:has(ytd-ad-slot-renderer)'
          ].join(',') + '{display:none !important}';
          function addStyle() {
            var root = document.head || document.documentElement;
            if (!root) return setTimeout(addStyle, 50);
            var s = document.createElement('style');
            s.textContent = css;
            root.appendChild(s);
          }
          addStyle();
          var mutedByUs = false;
          setInterval(function () {
            var skip = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytm-skip-ad-button, button[class*="skip-ad"]');
            if (skip) skip.click();
            var ad = document.querySelector('.ad-showing, .ad-interrupting');
            var video = document.querySelector('video');
            if (!video) return;
            if (ad) {
              if (!video.muted) { video.muted = true; mutedByUs = true; }
              if (isFinite(video.duration) && video.duration > 0) video.currentTime = video.duration;
            } else if (mutedByUs) {
              video.muted = false;
              mutedByUs = false;
            }
          }, 300);
        })();
    """.trimIndent()
}
