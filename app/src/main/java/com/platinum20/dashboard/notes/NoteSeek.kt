package com.platinum20.dashboard.notes

import android.content.Context
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import org.json.JSONObject
import java.util.Base64

/**
 * "Annota" nel player (1.606, idea 5): nella nota il minuto è un'etichetta `[▶ 6:52](seek:…)`; toccandola il video,
 * il podcast o il brano ripartono da lì. Dentro l'indirizzo c'è la cosa intera (in base64, senza spazi né parentesi),
 * così riparte anche se nel frattempo suona altro.
 */
object NoteSeek {
    const val SCHEME = "seek:"

    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** "6:52", "1:02:05": il minuto come lo legge una persona. Pura. */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** L'indirizzo dell'etichetta: la cosa (senza punto salvato) e il millisecondo. Pura. */
    fun url(item: ResumeItem, ms: Long): String {
        val json = item.copy(positionMs = 0, at = 0).toJson().toString()
        val packed = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
        return "$SCHEME${ms.coerceAtLeast(0)}/$packed"
    }

    /** La riga da mettere nella nota: l'etichetta col minuto e uno spazio per scrivere subito dopo. Pura. */
    fun line(item: ResumeItem, ms: Long): String = "[▶ ${clock(ms)}](${url(item, ms)}) "

    /** Che cosa e da dove, letto dall'indirizzo; null se non è un'etichetta del player. Pura. */
    fun parse(url: String): Pair<ResumeItem, Long>? {
        if (!url.startsWith(SCHEME)) return null
        val rest = url.removePrefix(SCHEME)
        val ms = rest.substringBefore('/').toLongOrNull() ?: return null
        return runCatching {
            val json = String(Base64.getUrlDecoder().decode(rest.substringAfter('/')), Charsets.UTF_8)
            ResumeItem.fromJson(JSONObject(json)) to ms
        }.getOrNull()
    }

    /** Il titolo della nota di una cosa: una nota sola per video o episodio, che si allunga a ogni "Annota". Pura. */
    fun noteTitle(item: ResumeItem): String = "Appunti · " + item.title.ifBlank { "Senza titolo" }.replace('\n', ' ').trim()

    /** Si può annotare con il minuto: non la radio (non ha minuti) e solo se si sa cosa suona. Pura. */
    fun canAnnotate(item: ResumeItem?): Boolean = item != null && item.kind != ResumeKind.RADIO

    /** "Annota": la riga col minuto in fondo alla nota di quella cosa (nuova se non c'è). Risponde l'id della nota. */
    fun annotate(item: ResumeItem, ms: Long): Long? = NoteInbox.appendToTitled(noteTitle(item), listOf(line(item, ms)))

    /** Tocco sull'etichetta: se suona già quella cosa salta al minuto, se no la fa partire da lì. */
    fun open(url: String) {
        val (item, ms) = parse(url) ?: return
        val now = CanvasPlayer.now.value
        if (now != null && now.item.kind == item.kind && now.item.key == item.key) {
            CanvasPlayer.seekTo(ms)
            if (!now.playing) CanvasPlayer.toggle()
            return
        }
        val context = appContext ?: return
        CanvasPlayer.play(context, item.copy(positionMs = ms, at = System.currentTimeMillis()))
    }
}
