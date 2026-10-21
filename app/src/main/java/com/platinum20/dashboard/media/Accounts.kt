package com.platinum20.dashboard.media

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** I servizi web che l'app apre come player, con l'accesso salvato nei cookie della WebView. */
enum class MediaService(
    val label: String,
    val homeUrl: String,
    val loginUrl: String,
    /** Pagine da cui leggere e cancellare i cookie dell'account. */
    val cookieUrls: List<String>,
    val cookieDomains: List<String>,
    /** Cookie presenti solo quando si è dentro con un account. */
    val sessionCookies: Set<String>,
    /** Spotify fa ascoltare la musica solo alla versione desktop del sito. */
    val desktopSite: Boolean,
) {
    YOUTUBE(
        label = "YouTube",
        homeUrl = "https://m.youtube.com/",
        loginUrl = "https://accounts.google.com/ServiceLogin?service=youtube&hl=it&continue=" +
            Uri.encode("https://m.youtube.com/"),
        cookieUrls = listOf(
            "https://www.youtube.com", "https://m.youtube.com", "https://accounts.google.com",
            "https://www.google.com", "https://accounts.youtube.com",
        ),
        cookieDomains = listOf(".youtube.com", ".google.com", "accounts.google.com"),
        sessionCookies = setOf("SAPISID", "__Secure-3PAPISID", "LOGIN_INFO"),
        desktopSite = false,
    ),
    SPOTIFY(
        label = "Spotify",
        homeUrl = "https://open.spotify.com/",
        loginUrl = "https://accounts.spotify.com/it/login?continue=" + Uri.encode("https://open.spotify.com/"),
        cookieUrls = listOf("https://open.spotify.com", "https://accounts.spotify.com", "https://www.spotify.com"),
        cookieDomains = listOf(".spotify.com", "accounts.spotify.com", "open.spotify.com"),
        sessionCookies = setOf("sp_dc"),
        desktopSite = true,
    );

    val homeHost: String get() = Uri.parse(homeUrl).host.orEmpty()
}

/** Stato degli accessi a YouTube e Spotify, gestiti da Impostazioni. */
object Accounts {
    private val _loggedIn = MutableStateFlow<Set<MediaService>>(emptySet())
    val loggedIn: StateFlow<Set<MediaService>> = _loggedIn.asStateFlow()

    fun refresh() {
        _loggedIn.value = MediaService.entries.filter { isLoggedIn(it) }.toSet()
    }

    fun isLoggedIn(service: MediaService): Boolean {
        val cookies = CookieManager.getInstance()
        return service.cookieUrls.any { url ->
            cookieNames(cookies.getCookie(url)).any { it in service.sessionCookies }
        }
    }

    /** Esce dall'account cancellando cookie e dati salvati dei siti del servizio. */
    fun logout(service: MediaService) {
        MediaWebViews.release(service)
        val cookies = CookieManager.getInstance()
        service.cookieUrls.forEach { url ->
            cookieNames(cookies.getCookie(url)).forEach { name ->
                // Un cookie si cancella solo riscrivendolo con lo stesso dominio: proviamo tutte le varianti.
                cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure")
                service.cookieDomains.forEach { domain ->
                    cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$domain; Secure")
                }
            }
            WebStorage.getInstance().deleteOrigin(url)
        }
        cookies.flush()
        refresh()
    }

    private fun cookieNames(raw: String?): List<String> =
        raw.orEmpty().split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }
}
