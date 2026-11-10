package com.platinum20.dashboard.update

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Le parole della pagina Aggiornamenti (punto 52): lo stato vero ("controlla da sola ogni 15 minuti") invece
 * di "premi il tasto", e una riga per versione da aprire a richiesta.
 */
object UpdateText {
    private const val PREFS = "update_check"
    private const val KEY_CHECKED = "checked_at"
    private val hour = DateTimeFormatter.ofPattern("HH:mm")
    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)

    /** Un controllo riuscito (in background o aprendo la pagina): la pagina dice quando. */
    fun markChecked(context: Context, at: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_CHECKED, at).apply()
    }

    fun lastChecked(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_CHECKED, 0L)

    /** "Ultimo controllo alle 01:20 · controlla da sola ogni 15 min"; spento: "Controllo automatico spento". */
    fun checked(at: Long, auto: Boolean, minutes: Long, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val last = if (at <= 0) null else Instant.ofEpochMilli(at).atZone(zone).let { t ->
            val today = now.atZone(zone).toLocalDate()
            when (t.toLocalDate()) {
                today -> "Ultimo controllo alle " + t.format(hour)
                today.minusDays(1) -> "Ultimo controllo ieri"
                else -> "Ultimo controllo il " + t.format(day)
            }
        }
        val how = if (auto) "controlla da sola ogni $minutes min" else "controllo automatico spento"
        return listOfNotNull(last, how).joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    /** Il titolo della versione: la riga in grassetto in cima alle note (quella del CHANGELOG), o il titolo. */
    fun headline(release: GitHubRelease): String {
        val bold = release.notes.lines().map { it.trim() }.firstOrNull { it.startsWith("**") && it.endsWith("**") && it.length > 4 }
        val fromNotes = bold?.removePrefix("**")?.removeSuffix("**")?.trim()
        return fromNotes ?: release.title.takeIf { it.isNotBlank() && it.removePrefix("v") != release.version } ?: "Versione ${release.version}"
    }

    /** Le note senza la riga del titolo, che è già nella riga chiusa. */
    fun body(release: GitHubRelease): String {
        val lines = release.notes.lines()
        val at = lines.indexOfFirst { val t = it.trim(); t.startsWith("**") && t.endsWith("**") && t.length > 4 }
        return (if (at >= 0) lines.filterIndexed { i, _ -> i != at } else lines).joinToString("\n")
    }

    /** "oggi", "ieri", "3 nov". */
    fun whenLabel(publishedAt: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String? =
        runCatching { java.time.OffsetDateTime.parse(publishedAt).atZoneSameInstant(zone).toLocalDate() }.getOrNull()?.let { d ->
            when (d) {
                today -> "oggi"
                today.minusDays(1) -> "ieri"
                else -> d.format(day)
            }
        }
}
