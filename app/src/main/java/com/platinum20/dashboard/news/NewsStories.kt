package com.platinum20.dashboard.news

import java.time.Duration
import java.time.Instant

/** Una storia: lo stesso fatto raccontato da una o più testate ([RecapTheme], il titolo più centrale in testa). */
typealias NewsStory = RecapTheme

/** L'articolo più recente della storia: decide se è nuova e dove sta nella lista. */
val NewsStory.newest: Instant? get() = items.mapNotNull { it.publishedAt }.maxOrNull()

/** La chiave stabile della riga: il link del titolo della storia. */
val NewsStory.key: String get() = headline.link

/** "ANSA", "ANSA e AGI", "ANSA, AGI e altre 2": chi ne parla, in una riga. */
val NewsStory.sourcesLine: String
    get() {
        val names = items.map { it.source.name }.distinct()
        return when (names.size) {
            0 -> ""
            1 -> names[0]
            2 -> "${names[0]} e ${names[1]}"
            3 -> "${names[0]}, ${names[1]} e ${names[2]}"
            else -> "${names[0]}, ${names[1]} e altre ${names.size - 2}"
        }
    }

/** Le News divise dal segno "ultima visita": sopra le storie nuove (la prima è quella con la foto grande), sotto le già viste. */
data class NewsFeed(
    val fresh: List<NewsStory>,
    val seen: List<NewsStory>,
    /** null = prima apertura: niente segno, tutto è "nuovo". */
    val since: Instant?,
) {
    val all: List<NewsStory> get() = fresh + seen
    val isEmpty: Boolean get() = fresh.isEmpty() && seen.isEmpty()
}

/**
 * La sezione News risponde a "cosa è successo da quando ho guardato?": una riga per fatto (non per articolo),
 * le storie nuove sopra il segno "ULTIMA VISITA", le già viste sotto. Logica pura, per i test.
 */
object NewsStories {

    /**
     * [readable] = gli articoli che il lettore sa aprire (null = tutti): gli altri escono dalle storie.
     * [hidden] = id delle fonti nascoste in Impostazioni.
     */
    fun group(items: List<NewsItem>, readable: Set<String>? = null, hidden: Set<String> = emptySet()): List<NewsStory> {
        val usable = items.filter { it.source.id !in hidden && (readable == null || it.link in readable) && !isBulletin(it.title) }
        return NewsRecap.cluster(usable)
    }

    /**
     * [settled] = articoli che contano già come visti (aperti prima di entrare, o segnati come letti):
     * la loro storia sta sotto il segno anche se è uscita dopo l'ultima visita. Gli articoli aperti durante
     * questa visita non spostano la riga: restano al loro posto, attenuati.
     */
    fun split(stories: List<NewsStory>, since: Instant?, settled: Set<String> = emptySet()): NewsFeed {
        val byTime = stories.sortedByDescending { it.newest ?: Instant.EPOCH }
        val (fresh, seen) = byTime.partition { story ->
            val isNew = since == null || story.newest?.isAfter(since) == true
            isNew && story.items.none { it.link in settled }
        }
        return NewsFeed(lead(fresh), seen, since)
    }

    /**
     * La storia con la foto grande va in cima: quella di cui parlano più testate fra le nuove;
     * a pari testate la più recente. Le altre restano dalla più recente.
     */
    internal fun lead(fresh: List<NewsStory>): List<NewsStory> {
        val top = fresh.maxWithOrNull(
            compareBy<NewsStory> { it.sourceCount }.thenBy { it.newest ?: Instant.EPOCH },
        ) ?: return fresh
        return listOf(top) + (fresh - top)
    }

    /** Le storie del widget e della notifica del mattino: le nuove, o se non ce ne sono le più riprese. */
    fun forWidget(feed: NewsFeed): List<NewsStory> =
        feed.fresh.ifEmpty { feed.all.sortedByDescending { it.sourceCount } }.take(3)

    /** La storia che contiene questo articolo, per "Le altre fonti" in fondo al lettore. */
    fun storyOf(stories: List<NewsStory>, link: String): NewsStory? = stories.firstOrNull { s -> s.items.any { it.link == link } }

    /** La prossima storia nuova da leggere dopo [link]: la prima non aperta che viene dopo nella lista, o prima. */
    fun next(feed: NewsFeed, link: String, read: Set<String>): NewsStory? {
        val list = feed.fresh
        val at = list.indexOfFirst { s -> s.items.any { it.link == link } }
        val order = if (at < 0) list else list.drop(at + 1) + list.take(at)
        return order.firstOrNull { s -> s.items.none { it.link in read } }
    }

    /** Le parole che la voce legge in un minuto, per "Ascolta le 7 nuove · 3 min" e "4 min di lettura". */
    fun minutes(chars: Int, charsPerMinute: Int = 900): Int = ((chars + charsPerMinute - 1) / charsPerMinute).coerceAtLeast(1)

    /** Minuti di lettura dell'articolo, a 200 parole al minuto. */
    fun readingMinutes(words: Int): Int = ((words + 199) / 200).coerceAtLeast(1)

    /** "3 nuove dalle 09:12" sopra la card in Home; senza nuove, la riga sola che dice che sei in pari. */
    fun homeLine(fresh: Int, since: Instant?, now: Instant, clock: (Instant) -> String): String {
        if (since == null) return "Le notizie di adesso"
        val from = sinceLabel(since, now, clock)
        return when (fresh) {
            0 -> "Niente di nuovo $from"
            1 -> "1 nuova $from"
            else -> "$fresh nuove $from"
        }
    }

    /** "dalle 09:12" se l'ultima visita è di meno di un giorno fa, altrimenti "da ieri" o "da 3 giorni". */
    fun sinceLabel(since: Instant, now: Instant, clock: (Instant) -> String): String {
        val hours = Duration.between(since, now).toHours()
        return when {
            hours < 20 -> "dalle ${clock(since)}"
            hours < 48 -> "da ieri"
            else -> "da ${hours / 24} giorni"
        }
    }

    /**
     * Il segno nella lista: "Ultima visita · 09:12", "· ieri", "· 3 giorni fa". Senza novità lo dice il
     * segno stesso ("· niente di nuovo"): una riga sola invece di due che dicono la stessa cosa.
     */
    fun markerLabel(since: Instant, now: Instant, clock: (Instant) -> String, nothingNew: Boolean = false): String {
        val hours = Duration.between(since, now).toHours()
        return "Ultima visita · " + when {
            hours < 20 -> clock(since)
            hours < 48 -> "ieri"
            else -> "${hours / 24} giorni fa"
        } + if (nothingNew) " · niente di nuovo" else ""
    }

    // Un telegiornale intero non è una notizia: "World24 del 09/11/2026", "Tg1 delle 20", "GR1 ore 8".
    private val bulletinName = Regex(
        "^(?:tg\\d*|tg\\s?la7|tgr|tg24|gr\\s?\\d|giornale radio|telegiornale|rainews24|world24)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val bulletinWhen = Regex(
        "\\d{1,2}[/.]\\d{1,2}[/.]\\d{2,4}|\\bedizione\\b|\\b(?:ore|delle)\\s+\\d{1,2}(?:[:.]\\d{2})?\\s*$",
        RegexOption.IGNORE_CASE,
    )

    /** Pura: il titolo è un tg o un giornale radio (nome del notiziario e data o ora), non una storia. */
    fun isBulletin(title: String): Boolean {
        val t = title.trim()
        return t.length <= 60 && bulletinName.containsMatchIn(t) && bulletinWhen.containsMatchIn(t)
    }

    /**
     * La riga sotto il titolo: "Open · 23 min fa"; con più versioni della storia "RaiNews · 2 fonti · 23 min fa"
     * (al posto della freccina, che sembrava un menu). Il tocco sulla storia le apre come prima.
     */
    fun metaLine(story: NewsStory, time: String?): String {
        val names = story.items.map { it.source.name }.distinct()
        val versions = when {
            story.items.size <= 1 -> null
            names.size > 1 -> "${names.size} fonti"
            else -> "${story.items.size} articoli"
        }
        return listOfNotNull(story.headline.source.name, versions, time).joinToString(" · ")
    }

    /** Pura: quanti articoli ha dato oggi ogni fonte (id → numero), per Impostazioni › News › Fonti. */
    fun todayBySource(items: List<NewsItem>, today: java.time.LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Map<String, Int> =
        items.filter { it.publishedAt?.atZone(zone)?.toLocalDate() == today }.groupingBy { it.source.id }.eachCount()

    /** "31 storie oggi", "1 storia oggi", "Niente oggi". */
    fun todayLabel(count: Int): String = when (count) {
        0 -> "Niente oggi"
        1 -> "1 storia oggi"
        else -> "$count storie oggi"
    }
}
