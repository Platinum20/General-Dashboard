package com.platinum20.dashboard.media.spotify

import android.content.Context
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.lastfm.FmTrack
import com.platinum20.dashboard.media.lastfm.LastFm
import com.platinum20.dashboard.media.lastfm.LastFmParse
import com.platinum20.dashboard.net.DiskCache
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Raccoglie i dati per le playlist di [MadeForYou] e le tiene salvate (file "sp-made.json": inizia con "sp-" e si
 * cancella con gli altri dati di Spotify). Ognuna si rifà solo quando tocca: Release Radar il venerdì, Discover
 * Weekly il lunedì, Daily Mix e classifiche ogni giorno. I brani di Last.fm si cercano su Spotify una volta sola.
 */
object MadeStore {
    private val scope = MainScope()
    private const val FILE = "sp-made.json"
    private const val RESOLVED_FILE = "sp-made-found.json"
    private const val RESOLVED_MAX = 1500

    private val _mixes = MutableStateFlow<List<SpMix>>(emptyList())
    /** Le tessere della fila Musica: radio (dall'ultima), Release Radar, Discover Weekly, Daily Mix, classifiche. */
    val mixes: StateFlow<List<SpMix>> = _mixes.asStateFlow()

    private val _similarArtists = MutableStateFlow<List<SpItem>>(emptyList())
    /** Artisti simili a quelli che ascolti, che non ascolti ancora: la fila di Scopri. */
    val similarArtists: StateFlow<List<SpItem>> = _similarArtists.asStateFlow()

    private val _similarTracks = MutableStateFlow<List<SpItem>>(emptyList())
    /** Brani simili ai tuoi rimasti fuori da Discover Weekly: l'altra fila di Scopri. */
    val similarTracks: StateFlow<List<SpItem>> = _similarTracks.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    // Le parti, ognuna con il giorno in cui si è fatta.
    private var radar: SpMix? = null
    private var radarKey = ""
    private var discover: SpMix? = null
    private var discoverKey = ""
    /** I candidati di Discover trovati su Spotify, con l'artista da cui vengono: i "simili" dei Daily Mix. */
    private var candidates: List<Pair<SpItem, String>> = emptyList()
    private var daily: List<SpMix> = emptyList()
    private var dailyKey = ""
    private var charts: List<SpMix> = emptyList()
    private var chartsKey = ""
    private var radios: List<SpMix> = emptyList()
    private val resolved = LinkedHashMap<String, SpItem?>()
    private var loaded = false
    private var lastTry = 0L
    private const val RETRY_MS = 30 * 60_000L

    /** Rifà quello che è scaduto. Si può chiamare spesso: fa qualcosa solo se serve. */
    fun refresh(context: Context, force: Boolean = false) {
        LastFm.init(context)
        if (_working.value) return
        // Un tentativo ogni mezz'ora al massimo: prima ogni apertura di Media rifaceva centinaia di ricerche.
        val now = System.currentTimeMillis()
        if (!force && now - lastTry < RETRY_MS) return
        lastTry = now
        _working.value = true
        scope.launch {
            try {
                load()
                val token = SpotifyAuth.token() ?: return@launch
                build(token, LocalDate.now())
            } catch (_: Exception) {
                // Si riprova alla prossima apertura di Media: quello già fatto resta.
            } finally {
                _working.value = false
            }
        }
    }

    private suspend fun build(token: String, today: LocalDate) {
        SpotifyStore.refreshHome()
        SpotifyStore.refreshLibrary()
        val home = withTimeoutOrNull(30_000) { SpotifyStore.home.first { it.data != null || (!it.loading && it.error != null) } }?.data ?: return
        val library = withTimeoutOrNull(20_000) { SpotifyStore.library.first { it.data != null || (!it.loading && it.error != null) } }?.data
        val pool = (home.topTracks + library?.liked.orEmpty() + home.recent).filter { it.kind == SpKind.TRACK }.distinctBy { it.id }
        val known = pool.map(MadeForYou::keyOf).toSet()

        val friday = MadeForYou.radarDay(today).toString()
        if (radarKey != friday) runCatching { buildRadar(token, home, today) }.onSuccess { radarKey = friday; publish(); save() }

        val monday = MadeForYou.discoverDay(today).toString()
        if (LastFm.ready && discoverKey != monday) runCatching { buildDiscover(token, home, library, known, today) }
            .onSuccess { discoverKey = monday; publish(); save() }

        val day = today.toString()
        if (dailyKey != day) runCatching { buildDaily(token, home, pool, today) }.onSuccess { dailyKey = day; publish(); save() }
        if (LastFm.ready && chartsKey != day) runCatching { buildCharts(token) }.onSuccess { chartsKey = day; publish(); save() }
    }

    private suspend fun buildRadar(token: String, home: SpHome, today: LocalDate) {
        SpotifyStore.refreshReleases()
        val followed = withTimeoutOrNull(60_000) { SpotifyStore.releases.first { it.data != null || (!it.loading && it.error != null) } }?.data.orEmpty()
        // Anche gli artisti che ascolti di più e non segui, come il Release Radar vero.
        val followedIds = SpotifyStore.artists.value.data.orEmpty().map { it.artist.id }.toSet()
        val extra = home.topArtists.filter { it.id !in followedIds }.take(15)
        val responses = coroutineScope {
            extra.chunked(5).flatMap { group -> group.map { a -> async { runCatching { SpotifyApi.artistAlbumsRaw(token, a.id) }.getOrNull() } }.awaitAll() }
        }
        val releases = (followed + NewReleases.pick(responses, today)).distinctBy { it.item.id }
            .filter { !it.released.isBefore(today.minusDays(NewReleases.DAYS)) }
            .sortedByDescending { it.released }.take(20)
        val withTracks = coroutineScope {
            releases.chunked(5).flatMap { group ->
                group.map { r -> async { r to runCatching { SpotifyApi.children(token, r.item) }.getOrDefault(emptyList()) } }.awaitAll()
            }
        }
        val tracks = MadeForYou.radar(withTracks, MadeForYou.radarDay(today))
        radar = MadeForYou.playlist(MadeForYou.RADAR_ID, "Release Radar", "Le uscite nuove dei tuoi artisti", "Novità", tracks)
    }

    private suspend fun buildDiscover(token: String, home: SpHome, library: SpLibrary?, known: Set<String>, today: LocalDate) {
        val seeds = (home.topTracks.take(12) + library?.liked.orEmpty().take(6)).distinctBy { it.id }
        val similar = coroutineScope {
            seeds.chunked(4).flatMap { group ->
                group.map { s ->
                    async {
                        val artist = MadeForYou.artistsOf(s).firstOrNull().orEmpty()
                        artist to runCatching { LastFm.similarTracks(FmTrack(s.title, artist), 15) }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }
        }
        val picks = MadeForYou.discover(similar, known, MadeForYou.discoverDay(today).toEpochDay())
        val found = resolveAll(token, picks.map { it.first })
        val knownIds = (home.topTracks + library?.liked.orEmpty() + home.recent).map { it.id }.toSet()
        candidates = picks.mapNotNull { (t, from) -> found[t.key]?.takeIf { it.id !in knownIds }?.let { it to from } }
        val chosen = candidates.take(MadeForYou.SIZE).map { it.first }
        discover = MadeForYou.playlist(MadeForYou.DISCOVER_ID, "Discover Weekly", "Musica nuova simile alla tua", "Scopri", chosen)
        _similarTracks.value = candidates.drop(MadeForYou.SIZE).map { it.first }
        _similarArtists.value = similarArtists(token, home)
    }

    /** Gli artisti simili ai tuoi primi cinque che non ascolti ancora, cercati su Spotify per la foto. */
    private suspend fun similarArtists(token: String, home: SpHome): List<SpItem> {
        val mine = home.topArtists.map { LastFmParse.norm(it.title) }.toSet()
        val names = coroutineScope {
            home.topArtists.take(5).map { a -> async { runCatching { LastFm.similarArtists(a.title, 8) }.getOrDefault(emptyList()) } }.awaitAll()
        }.flatMap { it.withIndex() }.sortedBy { it.index }.map { it.value }
            .distinctBy(LastFmParse::norm).filter { LastFmParse.norm(it) !in mine }.take(16)
        return coroutineScope {
            names.chunked(4).flatMap { group ->
                group.map { n ->
                    async {
                        runCatching { SpotifyApi.searchKind(token, "artist:$n", "artist", 3) }.getOrDefault(emptyList())
                            .firstOrNull { LastFmParse.norm(it.title) == LastFmParse.norm(n) }
                    }
                }.awaitAll()
            }
        }.filterNotNull().distinctBy { it.id }
    }

    private suspend fun buildDaily(token: String, home: SpHome, pool: List<SpItem>, today: LocalDate) {
        val artists = home.topArtists.take(20)
        // I generi da Spotify; se non li dà più (alle app personali capita), dai tag di Last.fm.
        val fromSpotify = runCatching { SpotifyApi.topGenres(token) }.getOrDefault(emptyList())
            .groupBy({ it.second }, { it.first })
        val genres = if (fromSpotify.values.any { it.isNotEmpty() }) {
            artists.map { it.title to fromSpotify[it.title].orEmpty() }
        } else if (LastFm.ready) {
            coroutineScope {
                artists.take(15).chunked(5).flatMap { group ->
                    group.map { a -> async { a.title to runCatching { LastFm.artistTags(a.title) }.getOrDefault(emptyList()) } }.awaitAll()
                }
            }
        } else emptyList()
        val groups = MadeForYou.genres(genres.filter { it.second.isNotEmpty() })
        val images = artists.associate { LastFmParse.norm(it.title) to it.image }
        daily = MadeForYou.daily(groups, pool, candidates, images, today.toEpochDay())
    }

    private suspend fun buildCharts(token: String) {
        val italy = runCatching { LastFm.topItaly() }.getOrDefault(emptyList())
        val world = runCatching { LastFm.topWorld() }.getOrDefault(emptyList())
        if (italy.isEmpty() && world.isEmpty()) throw IllegalStateException("classifiche vuote")
        val found = resolveAll(token, italy + world)
        charts = listOfNotNull(
            MadeForYou.playlist(MadeForYou.TOP_IT_ID, "Top 50 Italia", "I più ascoltati in Italia", "Top 50", italy.mapNotNull { found[it.key] }),
            MadeForYou.playlist(MadeForYou.TOP_WORLD_ID, "Top 50 mondo", "I più ascoltati nel mondo", "Top 50", world.mapNotNull { found[it.key] }),
        )
    }

    /**
     * Fa partire la radio di un brano (tenendolo premuto, "Radio"): i simili da Last.fm o, senza la chiave, altri
     * brani dello stesso artista. La radio resta nella fila Musica, al suo posto per ultimo ascolto.
     */
    fun startRadio(context: Context, seed: SpItem, onError: (String) -> Unit) {
        LastFm.init(context)
        scope.launch {
            load()
            val token = SpotifyAuth.token() ?: return@launch onError("Ricollega Spotify")
            val artist = MadeForYou.artistsOf(seed).firstOrNull().orEmpty()
            val similar = try {
                if (LastFm.ready) {
                    val fm = LastFm.similarTracks(FmTrack(seed.title, artist), 50)
                    val found = resolveAll(token, fm.take(40))
                    fm.mapNotNull { found[it.key] }
                } else {
                    SpotifyApi.searchKind(token, "artist:$artist", "track", 30).filter { it.id != seed.id }.shuffled()
                }
            } catch (e: Exception) {
                return@launch onError(e.message ?: "La radio non è partita, riprova")
            }
            if (similar.isEmpty()) return@launch onError("Non ho trovato brani simili a ${seed.title}")
            val radio = MadeForYou.radio(seed, similar)
            radios = MadeForYou.addRadio(radios, radio)
            publish()
            save()
            play(context, radio)
        }
    }

    /** Un mix nella coda unica, segnato col suo "mix:id" così la fila Musica sa quando l'hai ascoltato. */
    private fun play(context: Context, mix: SpMix) {
        val items = mix.tracks.map {
            ResumeItem(ResumeKind.SPOTIFY, it.uri, it.title, it.subtitle, it.image, at = System.currentTimeMillis(), link = "mix:" + mix.id)
        }
        CanvasPlayer.playList(context, items, 0)
    }

    /** I brani di Last.fm su Spotify: prima quelli già cercati, poi a gruppi di 5; un "troppe richieste" ferma il resto. */
    private suspend fun resolveAll(token: String, tracks: List<FmTrack>): Map<String, SpItem> {
        val missing = tracks.distinctBy { it.key }.filter { it.key !in resolved }
        var stop = false
        for (group in missing.chunked(5)) {
            if (stop) break
            val found = coroutineScope {
                group.map { t ->
                    async {
                        try {
                            t.key to MadeForYou.match(t, SpotifyApi.searchKind(token, "track:${t.title} artist:${t.artist}", "track", 5))
                        } catch (e: SpotifyApiException) {
                            if (e.code == 429) stop = true
                            null
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll()
            }
            found.filterNotNull().forEach { (k, v) -> resolved[k] = v }
        }
        while (resolved.size > RESOLVED_MAX) resolved.remove(resolved.keys.first())
        saveResolved()
        return tracks.mapNotNull { t -> resolved[t.key]?.let { t.key to it } }.toMap()
    }

    private fun publish() {
        _mixes.value = radios + listOfNotNull(radar, discover) + daily + charts
    }

    private suspend fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            val o = JSONObject(DiskCache.read(FILE) ?: return@runCatching)
            fun mix(k: String) = o.optJSONObject(k)?.let(MadeForYou::mixFrom)
            fun mixes(k: String) = with(MadeForYou) { o.optJSONArray(k).objects().map(::mixFrom) }
            radar = mix("radar"); radarKey = o.optString("radarKey")
            discover = mix("discover"); discoverKey = o.optString("discoverKey")
            daily = mixes("daily"); dailyKey = o.optString("dailyKey")
            charts = mixes("charts"); chartsKey = o.optString("chartsKey")
            radios = mixes("radios")
            candidates = with(MadeForYou) { o.optJSONArray("candidates").objects().map { itemFrom(it.getJSONObject("t")) to it.optString("f") } }
            _similarTracks.value = with(MadeForYou) { o.optJSONArray("simTracks").objects().map(::itemFrom) }
            _similarArtists.value = with(MadeForYou) { o.optJSONArray("simArtists").objects().map(::itemFrom) }
        }
        runCatching {
            val o = JSONObject(DiskCache.read(RESOLVED_FILE) ?: return@runCatching)
            o.keys().forEach { k -> resolved[k] = o.optJSONObject(k)?.let(MadeForYou::itemFrom) }
        }
        publish()
    }

    private suspend fun save() {
        val o = JSONObject()
        fun arr(list: List<SpMix>) = JSONArray().apply { list.forEach { put(MadeForYou.mixJson(it)) } }
        radar?.let { o.put("radar", MadeForYou.mixJson(it)) }
        discover?.let { o.put("discover", MadeForYou.mixJson(it)) }
        o.put("radarKey", radarKey).put("discoverKey", discoverKey).put("dailyKey", dailyKey).put("chartsKey", chartsKey)
        o.put("daily", arr(daily)).put("charts", arr(charts)).put("radios", arr(radios))
        o.put("candidates", JSONArray().apply { candidates.forEach { (t, f) -> put(JSONObject().put("t", MadeForYou.itemJson(t)).put("f", f)) } })
        o.put("simTracks", JSONArray().apply { _similarTracks.value.forEach { put(MadeForYou.itemJson(it)) } })
        o.put("simArtists", JSONArray().apply { _similarArtists.value.forEach { put(MadeForYou.itemJson(it)) } })
        DiskCache.write(FILE, o.toString())
    }

    private suspend fun saveResolved() {
        val o = JSONObject()
        resolved.forEach { (k, v) -> o.put(k, v?.let(MadeForYou::itemJson) ?: JSONObject.NULL) }
        DiskCache.write(RESOLVED_FILE, o.toString())
    }

    /** Scollegando Spotify si dimentica tutto (i file "sp-" li cancella già [SpotifyStore.clear]). */
    fun clear() {
        radar = null; discover = null; daily = emptyList(); charts = emptyList(); radios = emptyList(); candidates = emptyList()
        radarKey = ""; discoverKey = ""; dailyKey = ""; chartsKey = ""
        resolved.clear()
        _mixes.value = emptyList()
        _similarArtists.value = emptyList()
        _similarTracks.value = emptyList()
        loaded = true
    }

    /** Il brano che suona o che hai tenuto premuto, come brano di Spotify (per la radio). */
    fun seedOf(item: ResumeItem): SpItem? {
        if (item.kind != ResumeKind.SPOTIFY || !item.key.startsWith("spotify:track:")) return null
        return SpItem(item.key.substringAfterLast(':'), item.key, SpKind.TRACK, item.title, item.subtitle, item.image, item.durationMs)
    }

    /** Una delle nostre playlist (non un "Mix di"): la pagina del mix lo dice sotto il titolo. */
    fun isMade(id: String): Boolean = id.startsWith("made-") || id.startsWith(MadeForYou.RADIO_PREFIX)
}
