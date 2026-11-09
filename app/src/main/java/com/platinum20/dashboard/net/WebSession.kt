package com.platinum20.dashboard.net

import android.content.Context
import android.webkit.CookieManager
import com.platinum20.dashboard.security.Vault
import org.json.JSONObject

/**
 * L'accesso a un sito (Revolut, WindTre) che resta acceso come in un browser con "resta collegato".
 * Due cose si perdevano: i cookie "di sessione" (la WebView li tiene solo in memoria, persi a ogni
 * riavvio del processo) e il sessionStorage della pagina, che vive solo nella singola WebView: ogni
 * aggiornamento nascosto apre una WebView nuova, quindi se il sito tiene lì il suo token la sessione
 * sembrava finita anche se per il server era ancora buona. Dopo ogni lettura riuscita se ne tiene una
 * copia cifrata col Keystore ([Vault]) e la si rimette prima che la pagina parta.
 */
open class WebSession(private val prefsName: String, private val hosts: List<String>) {
    /** L'ultima copia del sessionStorage mandata dalla pagina: si salva solo dopo una lettura buona. */
    @Volatile
    private var pending: JSONObject = JSONObject()

    /** La pagina manda il suo sessionStorage (origine → JSON chiave/valore), da qualsiasi thread. */
    fun offer(origin: String, json: String) {
        if (json.length > MAX_STORAGE) return
        val parsed = runCatching { JSONObject(json) }.getOrNull() ?: return
        synchronized(this) { pending = JSONObject(pending.toString()).put(origin, parsed) }
    }

    /** Copia cookie e sessionStorage attuali (dopo una lettura andata bene). */
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        var changed = false
        runCatching {
            val cookies = CookieManager.getInstance()
            val all = JSONObject()
            hosts.forEach { host -> cookies.getCookie(host)?.takeIf { it.isNotBlank() }?.let { all.put(host, it) } }
            if (all.length() > 0 && Vault.read(prefs, KEY_COOKIES) != all.toString()) {
                Vault.put(edit, KEY_COOKIES, all.toString()); changed = true
            }
            cookies.flush()
        }
        val storage = synchronized(this) { pending.toString() }
        if (storage != "{}" && Vault.read(prefs, KEY_STORAGE) != storage) {
            Vault.put(edit, KEY_STORAGE, storage); changed = true
        }
        if (changed) edit.apply()
    }

    /** Rimette i cookie che la WebView ha perso ripartendo. */
    fun restore(context: Context) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val saved = runCatching { JSONObject(Vault.read(prefs, KEY_COOKIES) ?: return) }.getOrNull() ?: return
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

    /** Lo script d'inizio pagina che rimette il sessionStorage salvato, o null se non c'è niente. */
    fun storageScript(context: Context): String? {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val saved = Vault.read(prefs, KEY_STORAGE)?.takeIf { it.length > 2 } ?: return null
        synchronized(this) { if (pending.length() == 0) pending = runCatching { JSONObject(saved) }.getOrDefault(JSONObject()) }
        return restoreScript(saved)
    }

    /** Dimentica tutto (scollegando). */
    fun clear(context: Context) {
        synchronized(this) { pending = JSONObject() }
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().remove(KEY_COOKIES).remove(KEY_STORAGE).apply()
    }

    /** I cookie "nome=valore" salvati che non ci sono più fra quelli presenti (stesso nome). */
    fun missing(saved: String, present: String?): List<String> {
        val have = names(present.orEmpty())
        return saved.split(';').map { it.trim() }.filter { it.contains('=') && it.substringBefore('=').trim() !in have }
    }

    private fun names(cookies: String) =
        cookies.split(';').map { it.substringBefore('=').trim() }.filter { it.isNotEmpty() }.toSet()

    companion object {
        private const val KEY_COOKIES = "session"
        private const val KEY_STORAGE = "storage"
        /** Quanto vale un cookie rimesso: la scadenza vera la decide il sito. */
        private const val KEEP_S = 30L * 24 * 3600
        private const val MAX_STORAGE = 400_000

        /**
         * Rimette solo le chiavi che mancano: se la pagina ha già un token più nuovo vince il suo.
         * Il JSON entra come stringa JS quotata ([JSONObject.quote]), mai come codice.
         */
        fun restoreScript(savedJson: String): String = """
            (function () {
              try {
                var all = JSON.parse(${JSONObject.quote(savedJson)}), s = all[location.origin];
                if (!s) return;
                for (var k in s) if (sessionStorage.getItem(k) === null) sessionStorage.setItem(k, s[k]);
              } catch (e) {}
            })();
        """.trimIndent()

        /** Il pezzo di script che manda a Canvas il sessionStorage della pagina ogni pochi secondi. */
        fun snapshotScript(bridge: String): String = """
            (function () {
              function snap() {
                try {
                  var o = {}, n = sessionStorage.length;
                  for (var i = 0; i < n; i++) { var k = sessionStorage.key(i); o[k] = sessionStorage.getItem(k); }
                  if (n) $bridge.storage(location.origin, JSON.stringify(o));
                } catch (e) {}
              }
              setInterval(snap, 3000);
              window.addEventListener('pagehide', snap);
              window.__cvSnap = snap;
            })();
        """.trimIndent()
    }
}
