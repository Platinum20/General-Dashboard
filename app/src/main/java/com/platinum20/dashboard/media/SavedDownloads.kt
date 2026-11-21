package com.platinum20.dashboard.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.youtube.player.YtResolver
import com.platinum20.dashboard.net.HeavyDownloads
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.settings.AppSettings
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
import java.nio.ByteBuffer

/** Cosa si scarica: un episodio di podcast o un video di YouTube. I brani di Spotify no: sono protetti (DRM). */
enum class PackKind { PODCAST, VIDEO }

/** Un media salvato già sul telefono: il file e la voce da riprodurre. */
data class PackFile(
    val item: ResumeItem,
    val kind: PackKind,
    val file: String,
    val sec: Long,
    /** Qualità del file (video): 360 = il file unico di YouTube. */
    val height: Int = 360,
    /** La qualità scelta in Impostazioni quando si è scaricato: se poi la alzi, il video si riscarica. */
    val asked: Int = 360,
)

/** La qualità dei video salvati, scelta in Impostazioni › Media (1.557, Antonio: Full HD di default). */
enum class SavedQuality(val height: Int, val label: String, val detail: String) {
    FULL_HD(1080, "Full HD", "1080p · circa 1,4 GB l'ora"),
    HD(720, "HD", "720p · circa 700 MB l'ora"),
    LIGHT(360, "Leggera", "360p · circa 300 MB l'ora");

    companion object {
        val DEFAULT = FULL_HD
        fun of(height: Int): SavedQuality = entries.firstOrNull { it.height == height } ?: DEFAULT
    }
}

/**
 * Le regole pure dei download dei salvati (2026-11-21, Antonio: al posto dello zaino del treno, si scarica quello che
 * salvi, col Wi-Fi se "Scarica solo col Wi-Fi" è acceso). Testate.
 */
object SavedDownloadPlan {
    /** Mai più di 2 GB sul telefono. */
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    /** Durata ipotetica di un episodio che il feed non dice. */
    const val UNKNOWN_SEC = 30 * 60L

    /** Peso stimato: audio a 128 kbit/s; video a 360p circa 640 kbit/s, 720p 1,5 Mbit/s, 1080p 3,2 Mbit/s (audio compreso). */
    fun estimateBytes(kind: PackKind, sec: Long, height: Int = 360): Long = when (kind) {
        PackKind.PODCAST -> sec * 16_000
        PackKind.VIDEO -> sec * when {
            height > 720 -> 400_000
            height > 480 -> 190_000
            height > 360 -> 110_000
            else -> 80_000
        }
    }

    /**
     * La qualità da scaricare fra quelle che ci sono ([available], solo video senza audio): la più alta fino a [wanted]
     * che sta nei 2 GB accanto a [usedBytes]; null = il file unico a 360p (scelta Leggera, o niente di meglio che ci stia).
     */
    fun pickHeight(available: List<Int>, wanted: Int, sec: Long, usedBytes: Long): Int? =
        available.filter { it in 361..wanted && fits(usedBytes, estimateBytes(PackKind.VIDEO, sec, it)) }.maxOrNull()

    /** Il tipo di file per un media salvato; null = non si scarica (Spotify, radio, video del telefono). */
    fun kindOf(item: ResumeItem): PackKind? = when (item.kind) {
        ResumeKind.YOUTUBE -> PackKind.VIDEO
        ResumeKind.PODCAST -> PackKind.PODCAST.takeIf { item.link.startsWith("http") }
        else -> null
    }

    /** Quello che manca da scaricare, dal salvato più recente; un video scaricato sotto la qualità [wanted] di adesso manca ancora. */
    fun missing(saved: List<ResumeItem>, have: List<PackFile>, wanted: Int = 360): List<ResumeItem> =
        saved.filter { s ->
            kindOf(s) != null && have.none {
                it.item.key == s.key && it.kind == kindOf(s) && (it.kind != PackKind.VIDEO || it.asked >= wanted)
            }
        }.distinctBy { it.kind to it.key }

    /** I file da buttare: le cose che non sono più fra i salvati. */
    fun stale(saved: List<ResumeItem>, have: List<PackFile>): List<PackFile> =
        have.filter { f -> saved.none { it.key == f.item.key && kindOf(it) == f.kind } }

    /** Ci sta ancora un file da [itemBytes] accanto a [usedBytes]? */
    fun fits(usedBytes: Long, itemBytes: Long): Boolean = usedBytes + itemBytes <= MAX_BYTES

    /** La riga in Impostazioni: "3 pronti senza rete · 420 MB". */
    fun readyLine(count: Int, bytes: Long): String? {
        if (count <= 0) return null
        val mb = (bytes + 512 * 1024) / (1024 * 1024)
        val size = if (mb >= 1024) "%.1f GB".format(java.util.Locale.ITALY, mb / 1024.0) else "$mb MB"
        return (if (count == 1) "1 pronto senza rete" else "$count pronti senza rete") + " · " + size
    }
}

/**
 * I video di YouTube e gli episodi salvati, scaricati sul telefono (filesDir/salvati): il player li usa al posto
 * della rete ([localPodcast], [localVideo]). Si scaricano quando li salvi, si cancellano quando li togli dai salvati.
 */
object SavedDownloads {
    private const val DIR = "salvati"
    private const val OLD_DIR = "zaino"
    private const val INDEX = "index.json"
    private val lock = Mutex()
    private var dir: File? = null

    private val _files = MutableStateFlow<List<PackFile>>(emptyList())
    val files: StateFlow<List<PackFile>> = _files.asStateFlow()

    fun init(context: Context) {
        if (dir != null) return
        val app = context.applicationContext
        // Lo zaino del treno non c'è più: i suoi file se ne vanno.
        runCatching { File(app.filesDir, OLD_DIR).takeIf { it.exists() }?.deleteRecursively() }
        val d = File(app.filesDir, DIR).apply { mkdirs() }
        dir = d
        _files.value = runCatching { read(File(d, INDEX).readText()) }.getOrDefault(emptyList())
            .filter { File(d, it.file).exists() }
    }

    /** Il file dell'episodio, se è scaricato: si ascolta senza rete. */
    fun localPodcast(key: String): Uri? {
        val d = dir ?: return null
        val f = _files.value.firstOrNull { it.kind == PackKind.PODCAST && it.item.key == key } ?: return null
        return File(d, f.file).takeIf { it.exists() }?.let(Uri::fromFile)
    }

    /** Il file del video di YouTube, se è scaricato. */
    fun localVideo(id: String): PackFile? = _files.value.firstOrNull { it.kind == PackKind.VIDEO && it.item.key == id }
        ?.takeIf { f -> dir?.let { File(it, f.file).exists() } == true }

    fun fileOf(f: PackFile): File? = dir?.let { File(it, f.file) }

    /** Quanto pesa tutto quello scaricato. */
    fun bytes(): Long = dir?.let { d -> _files.value.sumOf { File(d, it.file).length() } } ?: 0L

    /**
     * Allinea il telefono ai salvati: butta i file delle cose tolte e scarica quelle nuove, una alla volta, finché la
     * rete lo permette (con "Scarica solo col Wi-Fi" si ferma appena non c'è il Wi-Fi). Spento in Impostazioni: butta e basta.
     */
    suspend fun sync(context: Context) {
        init(context)
        val d = dir ?: return
        AppSettings.init(context)
        NotesStore.init(context)
        fun saved() = SavedLinks.later(NotesStore.data.value.notes.filter { it.archivedAt == null }).map { it.item }
        // Prima le copertine che mancano ai salvati di Spotify: una richiesta piccola, anche coi dati.
        runCatching { com.platinum20.dashboard.capture.SavedCovers.fill(context) }
        val on = AppSettings.downloadSaved.value
        lock.withLock {
            val gone = if (on) SavedDownloadPlan.stale(saved(), _files.value) else _files.value
            withContext(Dispatchers.IO) {
                gone.forEach { File(d, it.file).delete() }
                val keep = _files.value - gone.toSet()
                // File rimasti a metà o non più nell'indice.
                d.listFiles()?.filter { f -> f.name != INDEX && keep.none { it.file == f.name } }?.forEach { it.delete() }
                save(keep)
            }
        }
        if (!on) return
        val wanted = AppSettings.savedQuality.value
        val tried = mutableSetOf<String>()
        while (true) {
            if (HeavyDownloads.blocked(context)) return
            val next = SavedDownloadPlan.missing(saved(), _files.value, wanted).firstOrNull { it.key !in tried } ?: return
            tried += next.key
            runCatching { take(d, next, wanted) }
        }
    }

    private suspend fun take(d: File, item: ResumeItem, wanted: Int) {
        val kind = SavedDownloadPlan.kindOf(item) ?: return
        val name = (if (kind == PackKind.VIDEO) "yt-" else "pod-") + item.key.hashCode().toUInt().toString(16) +
            if (kind == PackKind.VIDEO) ".mp4" else ".audio"
        val file = File(d, name)
        // Se il video c'era già in qualità più bassa, il suo peso non conta: lo sostituisce.
        val used = bytes() - (_files.value.firstOrNull { it.file == name }?.let { fileOf(it)?.length() } ?: 0L)
        if (kind == PackKind.PODCAST) {
            val sec = item.durationMs / 1000
            val estimate = SavedDownloadPlan.estimateBytes(kind, sec.takeIf { it > 0 } ?: SavedDownloadPlan.UNKNOWN_SEC)
            if (!SavedDownloadPlan.fits(used, estimate)) return
            if (runCatching { download(item.link, file) }.isFailure) return
            lock.withLock { save(_files.value.filterNot { it.file == name } + PackFile(item, kind, name, sec)) }
            return
        }
        // Le dirette non si scaricano.
        val s = YtResolver.forDownload("https://www.youtube.com/watch?v=${item.key}")
        if (s.live) return
        val sec = s.durationSec.takeIf { it > 0 } ?: SavedDownloadPlan.UNKNOWN_SEC
        val entry = item.copy(title = s.title.ifBlank { item.title }, subtitle = s.channel.ifBlank { item.subtitle }, durationMs = s.durationSec * 1000)
        val height = SavedDownloadPlan.pickHeight(s.videos.map { it.height }, wanted, sec, used)
            ?.takeIf { s.audioUrl != null }
        // Full HD: YouTube dà video e audio separati, si scaricano tutti e due e si uniscono qui in un MP4.
        val got = height?.let { h ->
            val video = s.videos.first { it.height == h }.url
            runCatching { downloadJoined(d, video, s.audioUrl!!, file) }.isSuccess.takeIf { it }?.let { h }
        } ?: run {
            // Riserva: il file unico a 360p, audio e video insieme. Se c'è già, si tiene quello.
            val old = _files.value.firstOrNull { it.file == name }
            if (old != null && file.exists()) {
                lock.withLock { save(_files.value.map { if (it.file == name) it.copy(asked = wanted) else it }) }
                return
            }
            val muxed = s.muxedUrl ?: return
            if (!SavedDownloadPlan.fits(used, SavedDownloadPlan.estimateBytes(kind, sec))) return
            if (runCatching { download(muxed, file) }.isFailure) return
            360
        }
        lock.withLock {
            save(_files.value.filterNot { it.file == name } + PackFile(entry, kind, name, s.durationSec, height = got, asked = wanted))
        }
    }

    /** Scarica video e audio a parte e li unisce in [target]; il file di prima resta finché quello nuovo non è pronto. */
    private suspend fun downloadJoined(d: File, video: String, audio: String, target: File) {
        val v = File(d, target.name + ".v")
        val a = File(d, target.name + ".a")
        val out = File(d, target.name + ".mux")
        try {
            download(video, v)
            download(audio, a)
            withContext(Dispatchers.IO) { mux(v, a, out) }
            if (!out.renameTo(target)) throw IOException("Rinomina non riuscita")
        } finally {
            v.delete()
            a.delete()
            out.delete()
        }
    }

    /**
     * Unisce la traccia video e quella audio (MP4 a pezzi di YouTube) in un MP4 normale, senza ricodificare: copia i
     * campioni alternandoli per tempo, così il file si legge dall'inizio senza saltare avanti e indietro.
     */
    private fun mux(video: File, audio: File, out: File) {
        val sources = listOf(video, audio).map { f -> MediaExtractor().apply { setDataSource(f.path) } }
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            var maxSample = 2 * 1024 * 1024
            val tracks = sources.mapIndexed { n, e ->
                val prefix = if (n == 0) "video/" else "audio/"
                val i = (0 until e.trackCount).firstOrNull { e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true }
                    ?: throw IOException("Traccia mancante")
                e.selectTrack(i)
                val format = e.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) maxSample = maxOf(maxSample, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                muxer.addTrack(format)
            }
            muxer.start()
            val buffer = ByteBuffer.allocateDirect(maxSample)
            val info = MediaCodec.BufferInfo()
            val done = BooleanArray(sources.size)
            while (!done.all { it }) {
                // Il campione più indietro nel tempo fra le due tracce.
                val n = sources.indices.filter { !done[it] }.minBy { sources[it].sampleTime }
                val e = sources[n]
                buffer.clear()
                val size = e.readSampleData(buffer, 0)
                if (size < 0) {
                    done[n] = true
                    continue
                }
                val key = e.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                info.set(0, size, e.sampleTime, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(tracks[n], buffer, info)
                if (!e.advance()) done[n] = true
            }
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            sources.forEach { runCatching { it.release() } }
        }
    }

    /**
     * Scarica a pezzi da 8 MB (YouTube rallenta le richieste intere), seguendo i rimandi anche fra http e https. Scrive
     * accanto (.part) e sostituisce [target] solo alla fine: se va male, il file di prima resta.
     */
    private suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        val part = File(target.path + ".part")
        part.delete()
        var address = url
        var start = 0L
        runCatching {
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
        }.onFailure {
            part.delete()
            throw it
        }
        if (part.length() == 0L) {
            part.delete()
            throw IOException("File vuoto")
        }
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
            put(
                JSONObject().put("item", f.item.toJson()).put("kind", f.kind.name).put("file", f.file).put("sec", f.sec)
                    .put("height", f.height).put("asked", f.asked),
            )
        }
    }.toString()

    internal fun read(text: String): List<PackFile> {
        val a = JSONArray(text)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                PackFile(
                    ResumeItem.fromJson(o.getJSONObject("item")), PackKind.valueOf(o.getString("kind")), o.getString("file"),
                    o.optLong("sec"), o.optInt("height", 360), o.optInt("asked", 360),
                )
            }.getOrNull()
        }
    }

    private const val CHUNK = 8L * 1024 * 1024
}
