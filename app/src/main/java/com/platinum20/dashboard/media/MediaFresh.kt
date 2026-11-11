package com.platinum20.dashboard.media

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.youtube.YtVideo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Cosa c'è di nuovo in Media (1.475, Antonio): i video usciti dopo l'ultima visita a Video, gli episodi non ancora
 * ascoltati, le uscite non aperte. La riga sopra il titolo e le file dicono questo, non "cosa suona" (lo dice già il blocco).
 */
object MediaFresh {
    /** Un episodio resta "nuovo" una settimana: dopo è solo un episodio vecchio mai ascoltato. */
    const val EPISODE_DAYS = 7L

    /** Uscito dopo [since]. Con [since] = 0 (Video mai aperto) nulla è nuovo: la prima volta non si accende tutto. */
    fun isNew(published: Instant?, since: Long): Boolean =
        since > 0 && published != null && published.toEpochMilli() > since

    fun newVideos(videos: List<YtVideo>, since: Long): Int = videos.count { isNew(it.published, since) }

    /** Dove va ULTIMA VISITA in una lista dal più nuovo: sotto l'ultimo video nuovo. Null se nessuno o tutti nuovi. */
    fun markerIndex(videos: List<YtVideo>, since: Long): Int? {
        val first = videos.indexOfFirst { !isNew(it.published, since) }
        return first.takeIf { it > 0 }
    }

    /** Un episodio uscito da poco che non hai ancora ascoltato ([listened] = chiavi della cronologia). */
    fun isNewEpisode(episode: Episode, listened: Set<String>, now: Long): Boolean =
        episode.id !in listened && episode.publishedAt > now - EPISODE_DAYS * 86_400_000L

    /** La riga sopra "Media": "6 video nuovi · 2 episodi"; null se non c'è niente di nuovo. */
    fun mediaLine(videos: Int, episodes: Int): String? = when {
        videos > 0 && episodes > 0 -> "${videos(videos)} · ${count(episodes, "episodio", "episodi")}"
        videos > 0 -> videos(videos)
        episodes > 0 -> count(episodes, "episodio nuovo", "episodi nuovi")
        else -> null
    }

    /** La riga sopra "Musica": "3 uscite · 2 episodi nuovi"; null se non c'è niente di nuovo. */
    fun musicLine(releases: Int, episodes: Int): String? = when {
        releases > 0 && episodes > 0 -> "${count(releases, "uscita", "uscite")} · ${count(episodes, "episodio nuovo", "episodi nuovi")}"
        releases > 0 -> count(releases, "uscita nuova", "uscite nuove")
        episodes > 0 -> count(episodes, "episodio nuovo", "episodi nuovi")
        else -> null
    }

    /** Il numero accanto al titolo di una fila: "6 nuovi", "1 nuovo"; vuoto se zero. */
    fun shelfCount(n: Int): String = if (n <= 0) "" else count(n, "nuovo", "nuovi")

    private fun videos(n: Int) = count(n, "video nuovo", "video nuovi")
    private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
}

/**
 * L'ultima visita alla pagina Video (1.475): quello uscito dopo è "nuovo" (pallino su Media, ULTIMA VISITA in Iscrizioni).
 * Aprire un video e tornare non è una visita nuova: entro [HOLD_MS] si tiene lo stesso segno, come in News.
 */
class VisitClock(private var stored: Long = 0L) {
    private var shown: Long? = null
    private var leftAt = 0L

    /** Entrando: il segno da mostrare in questa visita. */
    fun enter(now: Long): Long {
        shown?.let { if (now - leftAt < HOLD_MS) return it }
        return stored.also { shown = it }
    }

    /** Uscendo: da qui in poi "nuovo" vuol dire dopo adesso. */
    fun leave(now: Long) {
        leftAt = now
        stored = now
    }

    val last: Long get() = stored

    companion object {
        const val HOLD_MS = 30 * 60_000L
    }
}

object VideoVisit {
    private const val PREFS = "video_visit"
    private const val KEY_AT = "at"
    private lateinit var prefs: SharedPreferences
    private var clock = VisitClock()
    private val _since = MutableStateFlow(0L)

    /** L'ultima volta che sei uscito da Video (0 = mai). */
    val since: StateFlow<Long> = _since.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        clock = VisitClock(prefs.getLong(KEY_AT, 0L))
        _since.value = clock.last
    }

    fun enter(now: Long = System.currentTimeMillis()): Long = clock.enter(now)

    fun leave(now: Long = System.currentTimeMillis()) {
        clock.leave(now)
        _since.value = now
        if (::prefs.isInitialized) prefs.edit().putLong(KEY_AT, now).apply()
    }
}
