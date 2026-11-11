package com.platinum20.dashboard.trips

import android.content.Context
import android.net.Uri
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.youtube.player.YtResolver
import com.platinum20.dashboard.notes.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Una cosa scaricata per un viaggio: il file sul telefono e la voce da mettere in coda. */
data class PackFile(
    val item: ResumeItem,
    val kind: PackKind,
    val file: String,
    val sec: Long,
    val eventId: Long,
    /** Fine del viaggio: dopo [TrainPack.KEEP_AFTER_MS] il file si cancella. */
    val until: Long,
)

/**
 * Lo zaino del treno sul telefono (1.468): i file in filesDir/zaino e il loro indice. Il player li usa al posto
 * della rete ([localPodcast], [localVideo]); la riga del treno nel Calendario dice quanto c'è ([readySec]).
 */
object TrainPackStore {
    private const val DIR = "zaino"
    private const val INDEX = "index.json"
    private val lock = Mutex()
    private var dir: File? = null

    private val _files = MutableStateFlow<List<PackFile>>(emptyList())
    val files: StateFlow<List<PackFile>> = _files.asStateFlow()

    fun init(context: Context) {
        if (dir != null) return
        val d = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
        dir = d
        _files.value = runCatching { read(File(d, INDEX).readText()) }.getOrDefault(emptyList())
            .filter { File(d, it.file).exists() }
    }

    /** Quanti secondi sono pronti per il viaggio [eventId]; 0 se niente (o lo zaino è spento: la lista è vuota). */
    fun readySec(eventId: Long): Long = _files.value.filter { it.eventId == eventId }.sumOf { it.sec }

    /** Le voci dello zaino del viaggio, nell'ordine in cui vanno in coda. */
    fun queueFor(eventId: Long): List<ResumeItem> = _files.value.filter { it.eventId == eventId }.map { it.item }

    /** Il file dell'episodio, se è nello zaino: si ascolta senza rete. */
    fun localPodcast(key: String): Uri? = local(PackKind.PODCAST, key)

    /** Il file del video di YouTube, se è nello zaino. */
    fun localVideo(id: String): PackFile? = _files.value.firstOrNull { it.kind == PackKind.VIDEO && it.item.key == id }
        ?.takeIf { f -> dir?.let { File(it, f.file).exists() } == true }

    fun fileOf(f: PackFile): File? = dir?.let { File(it, f.file) }

    private fun local(kind: PackKind, key: String): Uri? {
        val d = dir ?: return null
        val f = _files.value.firstOrNull { it.kind == kind && it.item.key == key } ?: return null
        return File(d, f.file).takeIf { it.exists() }?.let(Uri::fromFile)
    }

    /** Toglie quello dei viaggi finiti da un po'. */
    suspend fun clean(now: Long = System.currentTimeMillis()) = lock.withLock {
        val d = dir ?: return@withLock
        val (old, keep) = _files.value.partition { TrainPack.expired(it.until, now) }
        withContext(Dispatchers.IO) {
            old.filter { o -> keep.none { it.file == o.file } }.forEach { File(d, it.file).delete() }
            // File rimasti a metà o non più nell'indice.
            d.listFiles()?.filter { f -> f.name != INDEX && keep.none { it.file == f.name } }?.forEach { it.delete() }
        }
        save(keep)
    }

    /**
     * Prepara lo zaino del treno [event]: prima i podcast e i video che hai salvato nelle Note, poi gli episodi
     * nuovi dei podcast che segui, finché basta per il viaggio. Ogni file si scarica una volta sola.
     */
    suspend fun pack(context: Context, event: CalendarEvent) {
        init(context)
        val d = dir ?: return
        val tripSec = (event.end - event.begin) / 1000
        if (tripSec <= 0) return
        NotesStore.init(context)
        PodcastStore.init(context)
        val notes = NotesStore.data.value.notes.sortedByDescending { it.createdAt }
        val savedEpisodes = notes.mapNotNull { (SavedLinks.find(it.text) as? SavedLink.Media)?.item }
            .filter { it.kind == ResumeKind.PODCAST && it.link.startsWith("http") }
        val videos = SavedLinks.youtubeVideos(notes)
        val heard = PodcastStore.positions.value
        val fresh = runCatching { PodcastStore.latestNow() }.getOrDefault(emptyList())
            .filter { e -> e.durationSec <= 0 || (heard[e.id] ?: 0L) < e.durationSec * 900 }
            .map { with(PodcastStore) { it.asItem() } }

        var packedSec = _files.value.filter { it.eventId == event.eventId }.sumOf { it.sec }
        var packedBytes = _files.value.sumOf { f -> File(d, f.file).length() }

        suspend fun take(item: ResumeItem, kind: PackKind, knownSec: Long, url: suspend () -> String?) {
            val existing = _files.value.firstOrNull { it.kind == kind && it.item.key == item.key }
            if (existing != null) {
                if (existing.eventId != event.eventId) {
                    save(_files.value.map { if (it === existing) it.copy(eventId = event.eventId, until = maxOf(it.until, event.end)) else it })
                    packedSec += existing.sec
                }
                return
            }
            val sec = knownSec.takeIf { it > 0 } ?: TrainPack.UNKNOWN_SEC
            if (!TrainPack.keep(kind, tripSec, packedSec, packedBytes, sec, TrainPack.estimateBytes(kind, sec))) return
            val link = runCatching { url() }.getOrNull() ?: return
            val name = (if (kind == PackKind.VIDEO) "yt-" else "pod-") + item.key.hashCode().toUInt().toString(16) +
                if (kind == PackKind.VIDEO) ".mp4" else ".audio"
            val file = File(d, name)
            val ok = runCatching { download(link, file) }.isSuccess
            if (!ok) {
                file.delete()
                return
            }
            packedSec += sec
            packedBytes += file.length()
            lock.withLock { save(_files.value + PackFile(item, kind, name, sec, event.eventId, event.end)) }
        }

        for (e in savedEpisodes) take(e, PackKind.PODCAST, e.durationMs / 1000) { e.link }
        for (v in videos) {
            var stream: com.platinum20.dashboard.media.youtube.player.YtStream? = null
            val item = ResumeItem(ResumeKind.YOUTUBE, v.id, v.title, v.channel, "https://i.ytimg.com/vi/${v.id}/hqdefault.jpg", at = System.currentTimeMillis())
            // La durata vera la sa solo YouTube: si legge prima di decidere.
            if (_files.value.none { it.item.key == v.id }) {
                stream = runCatching { YtResolver.resolve("https://www.youtube.com/watch?v=${v.id}", 360) }.getOrNull() ?: continue
                if (stream.hlsUrl != null || stream.muxedUrl == null) continue
            }
            val s = stream
            take(
                if (s != null) item.copy(title = s.title.ifBlank { item.title }, subtitle = s.channel.ifBlank { item.subtitle }, durationMs = s.durationSec * 1000) else item,
                PackKind.VIDEO,
                s?.durationSec ?: v.durationSec.toLong(),
            ) { s?.muxedUrl }
        }
        for (e in fresh) take(e, PackKind.PODCAST, e.durationMs / 1000) { e.link }
    }

    /** Scarica a pezzi da 8 MB (YouTube rallenta le richieste intere), seguendo i rimandi anche fra http e https. */
    private suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        val part = File(target.path + ".part")
        part.delete()
        var address = url
        var start = 0L
        part.outputStream().use { out ->
            while (true) {
                val (conn, final) = open(address, start)
                address = final
                val total = try {
                    // Senza Content-Range il server manda tutto in una volta.
                    val size = conn.getHeaderField("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    start += conn.inputStream.use { it.copyTo(out) }
                    size
                } finally {
                    conn.disconnect()
                }
                if (total == null || start >= total) break
            }
        }
        if (part.length() == 0L) throw IOException("File vuoto")
        if (!part.renameTo(target)) throw IOException("Rinomina non riuscita")
    }

    /** La connessione per il pezzo da [start], dopo i rimandi; con l'indirizzo finale, per i pezzi dopo. */
    private fun open(url: String, start: Long): Pair<HttpURLConnection, String> {
        var address = url
        repeat(7) {
            val conn = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Canvas/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Range", "bytes=$start-${start + CHUNK - 1}")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val next = conn.getHeaderField("Location") ?: throw IOException("Redirect senza indirizzo")
                conn.disconnect()
                address = URL(URL(address), next).toString()
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("HTTP $code")
            }
            return conn to address
        }
        throw IOException("Troppi rimandi")
    }

    private fun save(list: List<PackFile>) {
        _files.value = list
        val d = dir ?: return
        runCatching { File(d, INDEX).writeText(write(list)) }
    }

    internal fun write(list: List<PackFile>): String = JSONArray().apply {
        list.forEach { f ->
            put(JSONObject().put("item", f.item.toJson()).put("kind", f.kind.name).put("file", f.file)
                .put("sec", f.sec).put("event", f.eventId).put("until", f.until))
        }
    }.toString()

    internal fun read(text: String): List<PackFile> {
        val a = JSONArray(text)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                PackFile(
                    ResumeItem.fromJson(o.getJSONObject("item")), PackKind.valueOf(o.getString("kind")), o.getString("file"),
                    o.optLong("sec"), o.optLong("event"), o.optLong("until"),
                )
            }.getOrNull()
        }
    }

    private const val CHUNK = 8L * 1024 * 1024
}
