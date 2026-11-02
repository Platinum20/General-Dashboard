package com.platinum20.dashboard.update

import android.content.Context
import com.platinum20.dashboard.BuildConfig

/**
 * Le novità mostrate una volta sola al primo avvio dopo un aggiornamento. Il testo è la sezione
 * di CHANGELOG.md della versione installata, copiata in [BuildConfig.WHATS_NEW] dalla build.
 */
object WhatsNew {
    data class Notes(val version: String, val title: String, val points: List<String>)

    private const val PREFS = "whats_new"
    private const val KEY_SEEN = "seen_version"

    /** Titolo in grassetto e punti "- …" della sezione del changelog; null se non c'è niente da dire. */
    fun parse(version: String, section: String): Notes? {
        val lines = section.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val title = lines.firstOrNull { !it.startsWith("- ") }?.trim('*', ' ').orEmpty()
        val points = lines.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() }
        if (title.isEmpty() && points.isEmpty()) return null
        return Notes(version, title.ifEmpty { "Novità" }, points)
    }

    /** Solo dopo un aggiornamento: alla prima installazione non c'è niente di "nuovo" da raccontare. */
    fun shouldShow(seen: String?, current: String, freshInstall: Boolean): Boolean =
        !freshInstall && seen != current

    /**
     * Le novità da mostrare adesso, oppure null. Segna subito la versione come vista, così il
     * fumetto non torna anche se l'app viene chiusa mentre è aperto.
     */
    fun pending(context: Context): Notes? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = BuildConfig.VERSION_NAME
        val seen = prefs.getString(KEY_SEEN, null)
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val fresh = info != null && info.firstInstallTime == info.lastUpdateTime
        if (seen == current) return null
        prefs.edit().putString(KEY_SEEN, current).apply()
        if (!shouldShow(seen, current, fresh)) return null
        return parse(current, BuildConfig.WHATS_NEW)
    }
}
