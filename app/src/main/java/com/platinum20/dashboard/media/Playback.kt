package com.platinum20.dashboard.media

import android.content.Context
import com.platinum20.dashboard.media.podcast.PodcastNow
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.radio.RadioNow
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.SpPlayback
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.player.YtNow
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.news.RecapListening
import com.platinum20.dashboard.news.RecapSpeaker
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Da dove arriva quello che suona: decide colore, icona e comandi del player unico. */
enum class PlaySource(val label: String) {
    VIDEO("YouTube"),
    MUSIC("YouTube Music"),
    SPOTIFY("Spotify"),
    RADIO("Radio"),
    PODCAST("Podcast"),
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
) {
    /** Si vede un video (YouTube non in solo audio): il player grande è quello con il video. */
    val isVideo: Boolean get() = source == PlaySource.VIDEO
    val canSeek: Boolean get() = !live && durationMs > 0
    val canSkip: Boolean get() = source != PlaySource.PODCAST
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Il player unico dell'app: mette insieme YouTube (video e musica), Spotify, radio e podcast in
 * un solo "in riproduzione", con gli stessi comandi. Ognuno continua a suonare con il suo motore
 * (Spotify è protetto e resta nel suo riproduttore ufficiale), ma per chi usa l'app è un player solo.
 */
object Playback {
    private val scope = MainScope()
    /** L'ultima fonte chiesta o partita: vince quando tutte sono in pausa. */
    private var lastSource: PlaySource? = null

    /**
     * La fonte appena chiesta, con quello che si sa già (titolo, copertina) finché il suo motore non
     * risponde: Spotify ci mette qualche secondo, e intanto il mini player deve già mostrarlo.
     */
    internal data class Claim(val source: PlaySource, val placeholder: Playing?)

    private val claim = MutableStateFlow<Claim?>(null)
    /** Fonti messe da parte (è partita un'altra, o sono state chiuse): pronte, ma fuori dal mini player. */
    private val dismissed = MutableStateFlow<Set<PlaySource>>(emptySet())
    private var wasPlaying: Set<PlaySource> = emptySet()
    private var placeholderJob: Job? = null

    private val spotify = combine(NowPlaying.tracks, SpotifyStore.playback) { tracks, remote -> tracks[MediaService.SPOTIFY] to remote }
    private val shared = combine(RecapSpeaker.state, claim, dismissed, SpotifyPlayer.error) { speech, claim, dismissed, error ->
        Shared(speech, claim, dismissed, error)
    }

    private data class Shared(val speech: RecapListening?, val claim: Claim?, val dismissed: Set<PlaySource>, val spotifyError: String?)

    val now: StateFlow<Playing?> = combine(YtPlayer.now, spotify, RadioStore.now, PodcastStore.now, shared) { yt, sp, radio, podcast, shared ->
        val all = candidates(yt, sp.first, sp.second, radio, podcast, shared.speech)
        val playingNow = all.filter { it.playing }.map { family(it.source) }.toSet()
        val started = (playingNow - wasPlaying).firstOrNull()
        wasPlaying = playingNow
        var current = shared.claim
        var hidden = shared.dismissed
        if (started != null) {
            // Partita (anche da sola, es. Spotify dal telefono): è lei quella del mini player.
            lastSource = all.first { family(it.source) == started }.source
            hidden = PlaySource.entries.filter { family(it) != started }.toSet()
            if (current?.placeholder != null && family(current.source) == started) current = current.copy(placeholder = null)
            if (current != shared.claim) claim.value = current
            if (hidden != shared.dismissed) dismissed.value = hidden
        }
        choose(all, lastSource, current, hidden, shared.spotifyError)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** VIDEO e MUSIC sono lo stesso player di YouTube. */
    internal fun family(source: PlaySource): PlaySource = if (source == PlaySource.MUSIC) PlaySource.VIDEO else source

    /**
     * Quello da mostrare tenendo conto della fonte appena chiesta e di quelle messe da parte.
     * Funzione pura, provata nei test.
     */
    internal fun choose(all: List<Playing>, last: PlaySource?, claim: Claim?, dismissed: Set<PlaySource>, spotifyError: String? = null): Playing? {
        val visible = all.filter { family(it.source) !in dismissed }
        claim?.placeholder?.let { placeholder ->
            val real = visible.firstOrNull { family(it.source) == family(placeholder.source) }
            if (real?.playing != true) {
                return if (placeholder.source == PlaySource.SPOTIFY && spotifyError != null)
                    placeholder.copy(loading = false, subtitle = spotifyError, error = spotifyError)
                else placeholder
            }
        }
        return pick(visible, last)
    }

    /**
     * Una fonte sta per partire: il mini player passa subito a lei (con [title] e [artwork] se si
     * sanno già) e le altre, messe in pausa, escono dal mini player.
     */
    fun starting(source: PlaySource, title: String = "", subtitle: String = "", artwork: String = "") {
        val placeholder = title.takeIf { it.isNotBlank() }?.let {
            Playing(source, it, subtitle.ifBlank { "Caricamento…" }, artwork, playing = false, loading = true)
        }
        lastSource = source
        claim.value = Claim(source, placeholder)
        dismissed.value = PlaySource.entries.filter { family(it) != family(source) }.toSet()
        placeholderJob?.cancel()
        // Se il motore non risponde, dopo un po' si torna allo stato vero (anche vuoto).
        if (placeholder != null) placeholderJob = scope.launch {
            delay(25_000)
            settle(source)
        }
    }

    /** La fonte ha risposto (o è stata caricata ferma): si toglie il segnaposto. */
    fun settle(source: PlaySource) {
        val current = claim.value ?: return
        if (family(current.source) == family(source) && current.placeholder != null) claim.value = current.copy(placeholder = null)
    }

    /** Tutte le fonti aperte, nello stesso formato. Funzione pura, provata nei test. */
    internal fun candidates(
        yt: YtNow?,
        spotifyTrack: Track?,
        spotifyRemote: SpPlayback?,
        radio: RadioNow?,
        podcast: PodcastNow?,
        speech: RecapListening? = null,
    ): List<Playing> =
        listOfNotNull(
            yt?.let {
                val stream = it.stream
                Playing(
                    source = if (it.audioOnly) PlaySource.MUSIC else PlaySource.VIDEO,
                    title = stream?.title ?: "YouTube",
                    subtitle = it.error ?: stream?.channel.orEmpty(),
                    artwork = stream?.thumbnail.orEmpty(),
                    playing = it.playing,
                    loading = it.loading,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs.takeIf { d -> d > 0 } ?: ((stream?.durationSec ?: 0) * 1000L),
                    error = it.error,
                    live = stream?.hlsUrl != null,
                )
            },
            spotifyTrack?.let {
                Playing(PlaySource.SPOTIFY, it.title, it.artist, it.artworkUrl, it.playing, positionMs = it.positionMs, durationMs = it.durationMs)
            } ?: spotifyRemote?.takeIf { it.item != null }?.let {
                // Spotify su un altro dispositivo (app di Spotify, casse): si comanda da qui, e in pausa resta.
                val item = it.item!!
                Playing(
                    PlaySource.SPOTIFY, item.title,
                    listOf(item.subtitle, it.device).filter(String::isNotBlank).joinToString(" · "),
                    item.image, it.playing, positionMs = it.progressMs, durationMs = it.durationMs,
                )
            },
            radio?.let {
                Playing(
                    PlaySource.RADIO, it.station.name,
                    it.error ?: it.track.ifBlank { if (it.buffering) "Collegamento…" else "In diretta" },
                    it.station.logo, it.playing, loading = it.buffering, error = it.error, live = true,
                )
            },
            podcast?.let {
                Playing(
                    PlaySource.PODCAST, it.episode.title, it.error ?: it.episode.podcastTitle, it.episode.image, it.playing,
                    loading = it.buffering, positionMs = it.positionMs, durationMs = it.durationMs, error = it.error,
                )
            },
            speech?.let {
                Playing(
                    PlaySource.SPEECH, "Il punto del giorno",
                    it.error ?: if (it.part == 0) "Apertura" else "Tema ${it.part} di ${it.total - 1}",
                    "", it.speaking, positionMs = it.positionMs, durationMs = it.durationMs, error = it.error,
                )
            },
        )

    /** Quello da mostrare: ciò che suona, se no l'ultima fonte partita, se no la prima aperta. */
    internal fun pick(candidates: List<Playing>, last: PlaySource?): Playing? =
        candidates.firstOrNull { it.playing } ?: candidates.firstOrNull { it.source == last } ?: candidates.firstOrNull()

    /**
     * Quello che suona adesso come voce da riaprire (per "Salva nelle note"), con il punto a cui si è.
     * Null se non suona nulla o se è la lettura del punto del giorno.
     */
    fun currentAsItem(at: Long = System.currentTimeMillis()): ResumeItem? {
        val source = now.value?.source ?: return null
        val exo = YtPlayer.current()
        return itemFor(
            source, YtPlayer.now.value, exo?.currentPosition ?: 0, exo?.duration ?: 0,
            NowPlaying.tracks.value[MediaService.SPOTIFY], SpotifyStore.playback.value,
            RadioStore.now.value, PodcastStore.now.value, at,
        )
    }

    /** La voce da riaprire per [source], dagli stati delle fonti. Funzione pura, provata nei test. */
    internal fun itemFor(
        source: PlaySource,
        yt: YtNow?,
        ytPositionMs: Long,
        ytDurationMs: Long,
        spotifyTrack: Track?,
        spotifyRemote: SpPlayback?,
        radio: RadioNow?,
        podcast: PodcastNow?,
        at: Long,
    ): ResumeItem? = when (source) {
        PlaySource.VIDEO, PlaySource.MUSIC -> yt?.stream?.let { YtPlayer.resumeEntry(it, ytPositionMs, ytDurationMs, at, yt.audioOnly) }
        PlaySource.SPOTIFY -> spotifyTrack?.takeIf { it.uri.isNotBlank() }?.let {
            ResumeItem(
                ResumeKind.SPOTIFY, it.uri, it.title, it.artist, it.artworkUrl,
                positionMs = it.positionMs, durationMs = it.durationMs, at = at, link = it.contextUri,
            )
        } ?: spotifyRemote?.item?.let {
            ResumeItem(
                ResumeKind.SPOTIFY, it.uri, it.title, it.subtitle, it.image,
                positionMs = spotifyRemote.progressMs, durationMs = spotifyRemote.durationMs, at = at,
            )
        }
        PlaySource.RADIO -> radio?.station?.let {
            ResumeItem(ResumeKind.RADIO, it.id, it.name, "Radio", it.logo, at = at, station = it)
        }
        PlaySource.PODCAST -> podcast?.episode?.let {
            ResumeItem(
                ResumeKind.PODCAST, it.id, it.title, it.podcastTitle, it.image,
                positionMs = podcast.positionMs,
                durationMs = podcast.durationMs.takeIf { d -> d > 0 } ?: (it.durationSec * 1000),
                at = at, link = it.audioUrl, feed = it.feedUrl,
            )
        }
        PlaySource.SPEECH -> null
    }

    fun toggle(context: Context) {
        val current = now.value ?: return
        when (current.source) {
            PlaySource.VIDEO, PlaySource.MUSIC -> YtPlayer.toggle(context)
            PlaySource.SPOTIFY -> if (current.playing) spotifyCommand(PlayerCommand.PAUSE) { SpotifyApi.pause(it) } else resumeSpotify(context)
            PlaySource.RADIO -> RadioStore.togglePause()
            PlaySource.PODCAST -> PodcastStore.togglePause()
            PlaySource.SPEECH -> RecapSpeaker.toggle()
        }
    }

    /** Avanti: la voce dopo nella coda unica; se la coda è finita, quello che sa fare la fonte. */
    fun next(context: Context) {
        val source = now.value?.source ?: return
        if (source == PlaySource.SPEECH) return RecapSpeaker.next()
        // Il podcast ha i suoi "+30 secondi" al posto di avanti.
        if (source == PlaySource.PODCAST) return PodcastStore.seekBy(30_000)
        if (PlayQueue.next(context)) return
        when (source) {
            PlaySource.VIDEO, PlaySource.MUSIC -> YtPlayer.next(context)
            PlaySource.SPOTIFY -> spotifyCommand(PlayerCommand.NEXT) { SpotifyApi.next(it) }
            else -> Unit
        }
    }

    /** Indietro: all'inizio; nei primi secondi la voce prima nella coda (o quello che sa fare la fonte). */
    fun previous(context: Context) {
        val current = now.value ?: return
        when (current.source) {
            PlaySource.SPEECH -> return RecapSpeaker.previous()
            PlaySource.PODCAST -> return PodcastStore.seekBy(-10_000)
            else -> Unit
        }
        if (current.canSeek && current.positionMs > RESTART_MS) return seekTo(context, 0)
        if (PlayQueue.previous(context)) return
        when (current.source) {
            PlaySource.VIDEO, PlaySource.MUSIC -> YtPlayer.previous(context)
            PlaySource.SPOTIFY -> spotifyCommand(PlayerCommand.PREVIOUS) { SpotifyApi.previous(it) }
            else -> Unit
        }
    }

    /** Il tasto indietro riporta all'inizio; entro questi primi millisecondi va a quello prima. */
    const val RESTART_MS = 3_000L

    fun seekBy(context: Context, deltaMs: Long) {
        when (now.value?.source) {
            PlaySource.VIDEO, PlaySource.MUSIC -> YtPlayer.seekBy(context, deltaMs)
            PlaySource.PODCAST -> PodcastStore.seekBy(deltaMs)
            else -> Unit
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        val current = now.value ?: return
        when (current.source) {
            PlaySource.VIDEO, PlaySource.MUSIC -> YtPlayer.seekTo(context, positionMs)
            PlaySource.PODCAST -> PodcastStore.seekBy(positionMs - current.positionMs)
            PlaySource.SPEECH -> RecapSpeaker.seekTo(positionMs)
            PlaySource.SPOTIFY -> if (NowPlaying.tracks.value.containsKey(MediaService.SPOTIFY)) NowPlaying.seek(MediaService.SPOTIFY, positionMs)
                else SpotifyStore.control { SpotifyApi.seek(it, positionMs) }
            else -> Unit
        }
    }

    /**
     * Fa ripartire Spotify. Nell'app prima il tasto del riproduttore; se Spotify nel frattempo ha
     * perso il brano (succede dopo pause lunghe) lo si riprende da dov'era con la Web API.
     */
    private fun resumeSpotify(context: Context) {
        val local = NowPlaying.tracks.value[MediaService.SPOTIFY]
        if (local == null) {
            NowPlaying.pauseOthers(MediaService.SPOTIFY, radio = true)
            SpotifyStore.control { SpotifyApi.resume(it) }
            return
        }
        NowPlaying.pauseOthers(MediaService.SPOTIFY, radio = true)
        NowPlaying.command(MediaService.SPOTIFY, PlayerCommand.PLAY)
        if (local.uri.isBlank()) return
        scope.launch {
            delay(1_500)
            if (NowPlaying.tracks.value[MediaService.SPOTIFY]?.playing != true) {
                SpotifyPlayer.resume(context, local.uri, local.contextUri, local.positionMs, title = local.title, subtitle = local.artist, artwork = local.artworkUrl)
            }
        }
    }

    /** Ferma e chiude quello che suona (il mini player sparisce). */
    fun stop(context: Context) {
        val source = now.value?.source
        if (source != null) {
            settle(source)
            dismissed.value = dismissed.value + family(source) + source
        }
        when (source) {
            PlaySource.VIDEO, PlaySource.MUSIC -> YtPlayer.stop(context)
            PlaySource.SPOTIFY -> if (MediaService.SPOTIFY in MediaWebViews.openServices()) MediaWebViews.release(MediaService.SPOTIFY)
                else SpotifyStore.closeRemote()
            PlaySource.RADIO -> RadioStore.stop()
            PlaySource.PODCAST -> PodcastStore.stop()
            PlaySource.SPEECH -> RecapSpeaker.stop()
            null -> Unit
        }
    }

    /** Spotify nell'app si comanda dal suo riproduttore; quello su un altro dispositivo con la Web API. */
    private fun spotifyCommand(local: PlayerCommand, remote: suspend (String) -> Unit) {
        if (NowPlaying.tracks.value.containsKey(MediaService.SPOTIFY)) NowPlaying.command(MediaService.SPOTIFY, local)
        else SpotifyStore.control(remote)
    }

    private var sleepJob: Job? = null
    private val _sleepAt = MutableStateFlow<Long?>(null)
    /** Quando il timer di spegnimento metterà in pausa tutto (millisecondi), o null. */
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()

    /** Timer di spegnimento per qualunque fonte: allo scadere si mette in pausa tutto. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _sleepAt.value = null
        if (minutes == null) return
        _sleepAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            NowPlaying.pauseOthers(null, radio = true)
            _sleepAt.value = null
        }
    }
}
