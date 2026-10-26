package com.platinum20.dashboard.news.voice

import android.content.Context
import com.platinum20.dashboard.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Le voci che leggono notizie e punto del giorno. Le due Piper sono reti neurali sul telefono
 * (offline, gratis), scaricate una volta sola; la voce del telefono è il ripiego sempre pronto.
 */
enum class ReadingVoice(val label: String, val description: String, val archive: String?, val sizeMb: Int) {
    PAOLA("Paola", "Femminile, la più naturale", "vits-piper-it_IT-paola-medium", 60),
    RICCARDO("Riccardo", "Maschile, più leggera", "vits-piper-it_IT-riccardo-x_low", 30),
    SYSTEM("Voce del telefono", "Quella di Google, senza scaricare niente", null, 0);

    val neural: Boolean get() = archive != null

    /** I modelli Piper già convertiti per sherpa-onnx, con i dati di pronuncia di espeak-ng. */
    val url: String? get() = archive?.let { "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$it.tar.bz2" }

    companion object {
        val DEFAULT = PAOLA
        fun of(name: String?): ReadingVoice = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** I file che servono a sherpa-onnx per una voce Piper. */
data class VoiceFiles(val model: File, val tokens: File, val dataDir: File)

sealed interface VoiceStatus {
    data object Missing : VoiceStatus
    data class Downloading(val progress: Float) : VoiceStatus
    data object Ready : VoiceStatus
    data class Failed(val message: String) : VoiceStatus
}

object VoiceStore {
    private lateinit var root: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow<Map<ReadingVoice, VoiceStatus>>(emptyMap())
    val status: StateFlow<Map<ReadingVoice, VoiceStatus>> = _status.asStateFlow()

    fun init(context: Context) {
        if (::root.isInitialized) return
        AppSettings.init(context)
        root = File(context.applicationContext.filesDir, "voices")
        _status.value = ReadingVoice.entries.filter { it.neural }
            .associateWith { if (files(it) != null) VoiceStatus.Ready else VoiceStatus.Missing }
    }

    fun statusOf(voice: ReadingVoice): VoiceStatus =
        if (!voice.neural) VoiceStatus.Ready else _status.value[voice] ?: VoiceStatus.Missing

    /** La voce scelta, se è neurale e già sul telefono: altrimenti si legge con quella di sistema. */
    fun readyVoice(): Pair<ReadingVoice, VoiceFiles>? {
        if (!::root.isInitialized) return null
        val voice = AppSettings.readingVoice.value
        if (!voice.neural || statusOf(voice) != VoiceStatus.Ready) return null
        return files(voice)?.let { voice to it }
    }

    /** Al primo ascolto scarica la voce scelta in sottofondo; intanto legge la voce del telefono. */
    fun ensureDownloaded(context: Context) {
        init(context)
        val voice = AppSettings.readingVoice.value
        if (voice.neural && statusOf(voice) is VoiceStatus.Missing) download(voice)
    }

    fun download(voice: ReadingVoice) {
        val url = voice.url ?: return
        val now = statusOf(voice)
        if (now is VoiceStatus.Downloading || now == VoiceStatus.Ready) return
        _status.update { it + (voice to VoiceStatus.Downloading(0f)) }
        scope.launch {
            val target = File(root, voice.archive!!)
            val partial = File(root, voice.archive + ".part")
            val result = runCatching {
                partial.deleteRecursively()
                fetch(url, partial) { progress -> _status.update { it + (voice to VoiceStatus.Downloading(progress)) } }
                checkNotNull(locate(partial)) { "Voce incompleta" }
                target.deleteRecursively()
                check(partial.renameTo(target)) { "Non riesco a salvare la voce" }
            }
            partial.deleteRecursively()
            _status.update {
                it + (voice to if (result.isSuccess && files(voice) != null) VoiceStatus.Ready
                else VoiceStatus.Failed("Download non riuscito: riprova con la rete"))
            }
        }
    }

    private fun files(voice: ReadingVoice): VoiceFiles? = voice.archive?.let { locate(File(root, it)) }

    /** Scarica il tar.bz2 e lo apre direttamente nella cartella, senza tenere l'archivio. */
    private fun fetch(url: String, into: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            var read = 0L
            var shown = 0
            val counting = object : FilterInputStream(connection.inputStream.buffered(1 shl 16)) {
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
                    if (n > 0 && total > 0) {
                        read += n
                        val percent = (read * 100 / total).toInt()
                        if (percent != shown) { shown = percent; onProgress(percent / 100f) }
                    }
                }
            }
            untar(BZip2CompressorInputStream(counting), into)
        } finally {
            connection.disconnect()
        }
    }

    private fun untar(input: InputStream, into: File) {
        TarArchiveInputStream(input).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                val out = entryTarget(into, entry.name) ?: continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { tar.copyTo(it) }
                }
            }
        }
    }

    /**
     * Dove finisce un file dell'archivio: senza la cartella in cima ("vits-piper-…/") e mai fuori
     * da [root] (niente "../"). Funzione pura, provata nei test.
     */
    internal fun entryTarget(root: File, name: String): File? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.size < 2 || parts.any { it == ".." }) return null
        return File(root, parts.drop(1).joinToString(File.separator))
    }

    /** Il modello .onnx, tokens.txt e i dati di espeak-ng in una cartella, se ci sono tutti. */
    internal fun locate(dir: File): VoiceFiles? {
        val model = dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".onnx") } ?: return null
        val tokens = File(dir, "tokens.txt").takeIf { it.isFile } ?: return null
        val data = File(dir, "espeak-ng-data").takeIf { it.isDirectory } ?: return null
        return VoiceFiles(model, tokens, data)
    }
}
