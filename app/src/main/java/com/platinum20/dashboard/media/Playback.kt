package com.platinum20.dashboard.media

import com.platinum20.dashboard.news.RecapListening
import com.platinum20.dashboard.news.RecapSpeaker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Da dove arriva quello che suona: decide colore, icona ed etichetta del player unico. */
enum class PlaySource(val label: String) {
    VIDEO("YouTube"),
    MUSIC("YouTube Music"),
    SPOTIFY("Spotify"),
    RADIO("Radio"),
    PODCAST("Podcast"),
    /** Un video del telefono. */
    LOCAL("Video"),
    /** Il punto del giorno letto dalla voce del telefono. */
    SPEECH("Lettura"),
}

/** Quello che sta suonando, uguale per tutte le fonti: lo mostrano il mini player e il player grande. */
data class Playing(
    val source: PlaySource,
    val title: String,
    val subtitle: String,
    val artwork: String,
    val playing: Boolean,
    val loading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    /** In diretta (radio, dirette di YouTube): niente barra del tempo. */
    val live: Boolean = false,
    /** Ha un'immagine in movimento: il player grande la mostra al posto della copertina. */
    val isVideo: Boolean = false,
) {
    val canSeek: Boolean get() = !live && durationMs > 0
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Il player unico visto dalle schermate: quello che suona nel motore ([CanvasPlayer]) oppure la
 * lettura del punto del giorno, con gli stessi comandi.
 */
object Playback {
    private val scope = MainScope()
    /** La lettura è stata l'ultima a suonare (vince quando tutto è in pausa). */
    private var speechLast = false

    val now: StateFlow<Playing?> = combine(CanvasPlayer.now, RecapSpeaker.state) { engine, speech ->
        if (speech?.speaking == true) speechLast = true
        if (engine?.playing == true || engine?.loading == true || speech == null) speechLast = false
        if (speech != null && (speechLast || engine == null)) speechPlaying(speech) else engine?.let(::playing)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Lo stato del motore come lo mostrano i player. Funzione pura, provata nei test. */
    internal fun playing(n: EngineNow): Playing {
        val item = n.item
        val source = sourceOf(item.kind)
        return Playing(
            source = source,
            title = when {
                item.kind == ResumeKind.RADIO -> item.title
                item.title.isNotBlank() -> item.title
                else -> source.label
            },
            subtitle = n.error ?: when {
                item.kind == ResumeKind.RADIO -> n.streamTitle.ifBlank { if (n.loading) "Collegamento…" else "In diretta" }
                n.loading && item.subtitle.isBlank() -> "Caricamento…"
                else -> item.subtitle
            },
            artwork = item.image,
            playing = n.playing,
            loading = n.loading,
            positionMs = n.positionMs,
            durationMs = n.durationMs,
            error = n.error,
            live = n.live,
            isVideo = n.hasVideo,
        )
    }

    private fun speechPlaying(it: RecapListening) = Playing(
        PlaySource.SPEECH, "Il punto del giorno",
        it.error ?: if (it.part == 0) "Apertura" else "Tema ${it.part} di ${it.total - 1}",
        "", it.speaking, positionMs = it.positionMs, durationMs = it.durationMs, error = it.error,
    )

    fun sourceOf(kind: ResumeKind): PlaySource = when (kind) {
        ResumeKind.YOUTUBE -> PlaySource.VIDEO
        ResumeKind.MUSIC -> PlaySource.MUSIC
        ResumeKind.SPOTIFY -> PlaySource.SPOTIFY
        ResumeKind.RADIO -> PlaySource.RADIO
        ResumeKind.PODCAST -> PlaySource.PODCAST
        ResumeKind.VIDEO -> PlaySource.LOCAL
    }

    private fun speech(): Boolean = now.value?.source == PlaySource.SPEECH

    /**
     * Quello che suona adesso come voce da riaprire (per "Salva nelle note"), con il punto a cui si è.
     * Null se non suona nulla o se è la lettura del punto del giorno.
     */
    fun currentAsItem(at: Long = System.currentTimeMillis()): ResumeItem? {
        if (speech()) return null
        val n = CanvasPlayer.now.value ?: return null
        return n.item.copy(
            positionMs = if (n.live) 0 else n.positionMs,
            durationMs = if (n.live) 0 else n.durationMs.takeIf { it > 0 } ?: n.item.durationMs,
            at = at,
        )
    }

    fun toggle() {
        if (speech()) RecapSpeaker.toggle() else CanvasPlayer.toggle()
    }

    /** Avanti: la voce dopo nella coda (di qualunque fonte); finita la coda, il video proposto. */
    fun next() {
        if (speech()) return RecapSpeaker.next()
        if (CanvasPlayer.next()) return
        val n = CanvasPlayer.now.value ?: return
        if (n.item.kind == ResumeKind.YOUTUBE) {
            com.platinum20.dashboard.media.youtube.player.YtPlayer.suggestedAfter(n.item.key)?.let { link ->
                com.platinum20.dashboard.media.youtube.player.YtResolver.videoId(link)?.let { id ->
                    CanvasPlayer.addNext(ResumeItem(ResumeKind.YOUTUBE, id, "", at = System.currentTimeMillis()))
                    CanvasPlayer.next()
                }
            }
        }
    }

    /** Indietro: all'inizio; nei primi secondi la voce prima nella coda. */
    fun previous() {
        if (speech()) return RecapSpeaker.previous()
        val current = now.value ?: return
        if (current.canSeek && current.positionMs > RESTART_MS) return CanvasPlayer.seekTo(0)
        if (!CanvasPlayer.previous()) CanvasPlayer.seekTo(0)
    }

    /** Il tasto indietro riporta all'inizio; entro questi primi millisecondi va a quello prima. */
    const val RESTART_MS = 3_000L

    fun seekBy(deltaMs: Long) {
        if (speech()) RecapSpeaker.seekTo(((now.value?.positionMs ?: 0) + deltaMs).coerceAtLeast(0)) else CanvasPlayer.seekBy(deltaMs)
    }

    fun seekTo(positionMs: Long) {
        if (speech()) RecapSpeaker.seekTo(positionMs) else CanvasPlayer.seekTo(positionMs)
    }

    /** Ferma e chiude quello che suona (il mini player sparisce). */
    fun stop() {
        if (speech()) RecapSpeaker.stop() else CanvasPlayer.stop()
    }
}
