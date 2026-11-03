package com.platinum20.dashboard.bank

import android.content.Context
import android.webkit.CookieManager
import com.platinum20.dashboard.security.Vault
import org.json.JSONObject

/**
 * L'accesso a Revolut che sopravvive agli aggiornamenti dell'app. L'app web tiene la sessione in
 * cookie "di sessione" (senza scadenza): la WebView li tiene solo in memoria, quindi ogni volta
 * che il processo riparte (un aggiornamento lo chiude sempre) l'accesso andava rifatto. Dopo ogni
 * lettura riuscita se ne tiene una copia cifrata col Keystore ([Vault]) e, se mancano, si rimettono
 * prima di aprire la pagina, come fa un browser con "resta collegato". Se Revolut li ha scaduti
 * risponde 401 come prima e serve il nuovo accesso.
 */
object RevolutSession {
    private const val PREFS = "revolut"
    private const val KEY = "session"
    /** I posti dove l'app web legge i suoi cookie. */
    private val HOSTS = listOf("https://app.revolut.com", "https://sso.revolut.com", "https://www.revolut.com")
    /** Quanto vale un cookie rimesso: la scadenza vera la decide Revolut col 401. */
    private const val KEEP_S = 30L * 24 * 3600

    /** Copia i cookie attuali (dopo una lettura andata bene). */
    fun save(context: Context) {
        val cookies = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val all = JSONObject()
        HOSTS.forEach { host -> cookies.getCookie(host)?.takeIf { it.isNotBlank() }?.let { all.put(host, it) } }
        if (all.length() == 0) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (Vault.read(prefs, KEY) == all.toString()) return
        Vault.put(prefs.edit(), KEY, all.toString()).apply()
        cookies.flush()
    }

    /** Rimette i cookie che la WebView ha perso ripartendo. */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = runCatching { JSONObject(Vault.read(prefs, KEY) ?: return) }.getOrNull() ?: return
        val cookies = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        var changed = false
        saved.keys().forEach { host ->
            missing(saved.optString(host), cookies.getCookie(host)).forEach { pair ->
                cookies.setCookie(host, "$pair; Path=/; Secure; Max-Age=$KEEP_S")
                changed = true
            }
        }
        if (changed) cookies.flush()
    }

    /** I cookie "nome=valore" salvati che non ci sono più fra quelli presenti (stesso nome). */
    fun missing(saved: String, present: String?): List<String> {
        val have = names(present.orEmpty())
        return saved.split(';').map { it.trim() }.filter { it.contains('=') && it.substringBefore('=').trim() !in have }
    }

    private fun names(cookies: String) =
        cookies.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.toSet()
}
