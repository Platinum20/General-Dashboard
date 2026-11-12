package com.platinum20.dashboard.media.spotify

import com.platinum20.dashboard.media.lastfm.FmTrack
import com.platinum20.dashboard.media.lastfm.LastFmParse
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random

/**
 * Le playlist "fatte per te" che Spotify non dà più alle app personali (1.491, Antonio: "implementa tutto"), rifatte
 * da noi: Release Radar (il venerdì), Discover Weekly (il lunedì), Daily Mix per genere, Top 50 Italia e mondo e le
 * radio da un brano. Qui solo le regole, pure; i dati li raccoglie [MadeStore].
 */
object MadeForYou {
    const val SIZE = 30
    const val DAILY_MAX = 5
    const val DAILY_OWN = 22
    const val DAILY_EXTRA = 8
    /** Sotto questi brani tuoi un genere non fa un Daily Mix: sarebbe quasi tutto "simili". */
    const val DAILY_MIN_OWN = 6
    const val RADIO_SIZE = 30
    const val RADIOS_MAX = 5
    /** Brani dello stesso artista al massimo in Discover Weekly: deve far scoprire, non ripetere. */
    const val PER_ARTIST = 2

    const val RADAR_ID = "made-radar"
    const val DISCOVER_ID = "made-discover"
    const val TOP_IT_ID = "made-top-it"
    const val TOP_WORLD_ID = "made-top-world"
    const val DAILY_PREFIX = "made-daily-"
    const val RADIO_PREFIX = "radio-"

    /** Il venerdì in cui il Release Radar si è rifatto l'ultima volta (oggi, se è venerdì). */
    fun radarDay(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY))

    /** Il lunedì di questa settimana: la chiave di Discover Weekly. */
    fun discoverDay(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * Release Radar: le uscite della settimana fino a quel venerdì, dalla più nuova, due brani per album; se sono poche
     * si va indietro fino a quattro settimane, così non è mai quasi vuota.
     */
    fun radar(releases: List<Pair<SpRelease, List<SpItem>>>, friday: LocalDate, size: Int = SIZE): List<SpItem> {
        val sorted = releases.filter { !it.first.released.isAfter(friday) }.sortedByDescending { it.first.released }
        fun within(days: Long) = sorted.filter { !it.first.released.isBefore(friday.minusDays(days)) }
            .flatMap { (release, tracks) -> tracks.take(2).map { t -> if (t.image.isBlank()) t.copy(image = release.item.image) else t } }
            .distinctBy { it.id }
        val week = within(6)
        return (if (week.size >= 10) week else within(27)).take(size)
    }

    /** La chiave di un brano di Spotify, per riconoscerlo tra quelli di Last.fm. */
    fun keyOf(item: SpItem): String = FmTrack(item.title, item.subtitle.substringBefore(", ").substringBefore(" · ")).key

    /**
     * Discover Weekly: i simili dei tuoi brani presi a giro (uno per brano di partenza, poi il secondo...), senza
     * quelli che conosci già e al massimo [PER_ARTIST] per artista. Ogni lunedì un ordine diverso.
     * Torna i candidati con l'artista da cui vengono (serve ai Daily Mix): ne servono più di [SIZE], qualcuno su
     * Spotify non si trova.
     */
    fun discover(similar: List<Pair<String, List<FmTrack>>>, known: Set<String>, seed: Long, max: Int = SIZE * 2): List<Pair<FmTrack, String>> {
        val random = Random(seed)
        val lists = similar.map { (from, list) -> list.map { it to from } }.shuffled(random)
        val seen = HashSet<String>(known)
        val perArtist = HashMap<String, Int>()
        val out = ArrayList<Pair<FmTrack, String>>()
        var round = 0
        while (out.size < max && lists.any { it.size > round }) {
            for (l in lists) {
                val (track, from) = l.getOrNull(round) ?: continue
                val artist = LastFmParse.norm(track.artist)
                if (track.key in seen || (perArtist[artist] ?: 0) >= PER_ARTIST) continue
                seen += track.key
                perArtist[artist] = (perArtist[artist] ?: 0) + 1
                out += track to from
                if (out.size >= max) break
            }
            round++
        }
        return out
    }

    /**
     * I generi per i Daily Mix: ogni artista va nel suo genere più diffuso tra i tuoi artisti, così due mix non si
     * pestano i piedi. [artists] è nell'ordine di ascolto, ognuno coi suoi generi. Genere → artisti.
     */
    fun genres(artists: List<Pair<String, List<String>>>, max: Int = DAILY_MAX): List<Pair<String, List<String>>> {
        val count = HashMap<String, Int>()
        artists.forEach { (_, g) -> g.map { it.lowercase() }.distinct().forEach { count[it] = (count[it] ?: 0) + 1 } }
        val groups = LinkedHashMap<String, MutableList<String>>()
        artists.forEach { (name, g) ->
            val best = g.map { it.lowercase() }.distinct().maxWithOrNull(compareBy<String> { count[it] ?: 0 }.thenBy { -g.indexOf(it) })
                ?: return@forEach
            groups.getOrPut(best) { mutableListOf() } += name
        }
        return groups.entries.sortedByDescending { it.value.size }.take(max).map { it.key to it.value.toList() }
    }

    /**
     * I Daily Mix: per ogni genere i tuoi brani dei suoi artisti più qualche simile dagli stessi artisti; ogni giorno
     * un ordine nuovo, sempre aperto da un brano tuo. Si numerano 1, 2, 3 come su Spotify.
     */
    fun daily(
        groups: List<Pair<String, List<String>>>,
        pool: List<SpItem>,
        extras: List<Pair<SpItem, String>>,
        images: Map<String, String>,
        epochDay: Long,
    ): List<SpMix> {
        val tracks = pool.filter { it.kind == SpKind.TRACK }.distinctBy { it.id }
        var n = 0
        return groups.mapNotNull { (genre, artists) ->
            val names = artists.map { LastFmParse.norm(it) }.toSet()
            val own = tracks.filter { t -> artistsOf(t).any { LastFmParse.norm(it) in names } }
            if (own.size < DAILY_MIN_OWN) return@mapNotNull null
            val random = Random(epochDay * 17 + genre.hashCode())
            val ownIds = own.map { it.id }.toSet()
            val more = extras.filter { (t, from) -> LastFmParse.norm(from) in names && t.id !in ownIds }.map { it.first }
                .distinctBy { it.id }.shuffled(random).take(DAILY_EXTRA)
            val first = own.shuffled(random).take(DAILY_OWN)
            val mixed = listOf(first.first()) + (first.drop(1) + more).shuffled(random)
            n++
            val who = (artists + mixed.flatMap(::artistsOf)).distinct().take(3)
            SpMix(
                id = DAILY_PREFIX + n,
                title = "Daily Mix $n",
                subtitle = who.joinToString(", ") + " e altri",
                image = artists.firstNotNullOfOrNull { images[LastFmParse.norm(it)]?.takeIf(String::isNotBlank) } ?: first.first().image,
                tracks = mixed,
                badge = SpotifyDiscover.label(genre),
            )
        }
    }

    /** Il brano di Spotify trovato per uno di Last.fm: il primo con lo stesso artista, se no niente (meglio che sbagliare). */
    fun match(track: FmTrack, found: List<SpItem>): SpItem? {
        val artist = LastFmParse.norm(track.artist)
        val title = LastFmParse.norm(track.title)
        val sameArtist = found.filter { f -> artistsOf(f).any { LastFmParse.norm(it) == artist } }
        return sameArtist.firstOrNull { LastFmParse.norm(it.title) == title } ?: sameArtist.firstOrNull { LastFmParse.norm(it.title).startsWith(title) }
    }

    /** La radio di un brano: lui per primo, poi i simili trovati, senza doppioni. */
    fun radio(seed: SpItem, similar: List<SpItem>): SpMix {
        val tracks = (listOf(seed) + similar).distinctBy { keyOf(it) }.take(RADIO_SIZE)
        val who = tracks.drop(1).flatMap(::artistsOf).distinct().take(3)
        return SpMix(
            id = RADIO_PREFIX + seed.id,
            title = "Radio di ${seed.title}",
            subtitle = if (who.isEmpty()) seed.subtitle else who.joinToString(", ") + " e altri",
            image = seed.image,
            tracks = tracks,
            badge = "Radio",
        )
    }

    /** L'ultima radio va davanti; le stesse non si ripetono; al massimo [RADIOS_MAX]. */
    fun addRadio(radios: List<SpMix>, radio: SpMix): List<SpMix> =
        (listOf(radio) + radios.filter { it.id != radio.id }).take(RADIOS_MAX)

    fun playlist(id: String, title: String, why: String, badge: String, tracks: List<SpItem>): SpMix? =
        tracks.distinctBy { it.uri }.takeIf { it.isNotEmpty() }?.let { SpMix(id, title, why, it.first().image, it, badge) }

    fun artistsOf(track: SpItem): List<String> =
        track.subtitle.substringBefore(" · ").split(", ").map { it.trim() }.filter { it.isNotBlank() }

    // Salvataggio: le playlist fatte restano tra un'apertura e l'altra (si rifanno solo quando tocca).
    fun mixJson(m: SpMix): JSONObject = JSONObject().put("id", m.id).put("t", m.title).put("s", m.subtitle).put("i", m.image)
        .put("b", m.badge).put("tr", JSONArray().apply { m.tracks.forEach { put(itemJson(it)) } })

    fun mixFrom(o: JSONObject): SpMix = SpMix(
        o.getString("id"), o.optString("t"), o.optString("s"), o.optString("i"),
        o.optJSONArray("tr").objects().map(::itemFrom), o.optString("b", "Mix"),
    )

    fun itemJson(i: SpItem): JSONObject = JSONObject()
        .put("id", i.id).put("u", i.uri).put("k", i.kind.name).put("t", i.title).put("s", i.subtitle).put("i", i.image).put("d", i.durationMs)

    fun itemFrom(o: JSONObject): SpItem = SpItem(
        o.getString("id"), o.optString("u"), runCatching { SpKind.valueOf(o.optString("k")) }.getOrDefault(SpKind.TRACK),
        o.optString("t"), o.optString("s"), o.optString("i"), o.optLong("d"),
    )

    internal fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
