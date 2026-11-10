package com.platinum20.dashboard.settings

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import java.io.File

/**
 * Quello che resta sul telefono di cose tolte dall'app (1.443, Antonio: "non ci deve essere traccia"):
 * l'accesso e i dati letti del conto (cookie, storage della pagina, preferenze e copie delle risposte).
 * Si pulisce una volta sola, all'avvio.
 */
object Leftovers {
    private const val PREFS = "leftovers"
    private const val KEY = "bank"
    private val HOSTS = listOf("https://app.revolut.com", "https://sso.revolut.com", "https://www.revolut.com", "https://revolut.com")

    fun clean(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return
        runCatching { app.deleteSharedPreferences("revolut") }
        runCatching { File(app.filesDir, "revolut-captures.json").delete() }
        runCatching {
            val cookies = CookieManager.getInstance()
            HOSTS.forEach { url ->
                cookies.getCookie(url)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                    cookies.setCookie(url, "$name=; Max-Age=0")
                    cookies.setCookie(url, "$name=; Max-Age=0; Domain=.revolut.com; Path=/")
                }
                WebStorage.getInstance().deleteOrigin(url)
            }
            cookies.flush()
        }
        prefs.edit().putBoolean(KEY, true).apply()
    }
}
