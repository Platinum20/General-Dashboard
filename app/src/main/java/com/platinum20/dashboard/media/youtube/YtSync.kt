package com.platinum20.dashboard.media.youtube

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.youtube.player.YtResolver
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Cosa cambiare dalle due parti perché una lista di Canvas e una di YouTube tornino uguali.
 * [synced] sono le cose che all'ultima sincronizzazione erano in tutte e due: se una manca da una parte
 * vuol dire che l'hai tolta lì, altrimenti è nuova e va aggiunta all'altra. Funzione pura, provata nei test.
 */
internal data class SyncPlan(
    val addRemote: Set<String>,
    val removeRemote: Set<String>,
    val addLocal: Set<String>,
    val removeLocal: Set<String>,
) {
    /** Le cose in comune dopo aver applicato il piano, da ricordare per la prossima volta. */
    fun synced(remote: Set<String>, local: Set<String>, previous: Set<String>, remoteComplete: Boolean, kept: Set<String> = emptySet()): Set<String> {
        val remoteAfter = remote + addRemote - removeRemote
        val localAfter = local + addLocal - removeLocal
        // Le cose oltre le pagine lette di YouTube non si vedono: restano com'erano.
        val unseen = if (remoteComplete) emptySet() else previous intersect localAfter
        // Tolte da Canvas ma non (ancora) da YouTube: non vanno riaggiunte a Canvas.
        val pending = previous intersect (remoteAfter - localAfter)
        return (remoteAfter intersect localAfter) + unseen + pending + kept
    }

    companion object {
        /**
         * @param remoteComplete YouTube ha dato tutta la lista: solo allora una cosa che manca là si toglie da Canvas.
         * @param pushRemovals togliere da YouTube le cose sparite da Canvas (per i Mi piace no: li toglie il tocco sul cuore).
         * @param ignore cose da non riportare in Canvas (Mi piace tolti qui e non ancora tolti su YouTube).
         */
        fun of(
            remote: Set<String>,
            local: Set<String>,
            synced: Set<String>,
            remoteComplete: Boolean = true,
            pushRemovals: Boolean = true,
            ignore: Set<String> = emptySet(),
        ): SyncPlan {
            val onlyRemote = remote - local - ignore
            val onlyLocal = local - remote
            return SyncPlan(
                addRemote = onlyLocal - synced,
                removeRemote = if (pushRemovals) onlyRemote intersect synced else emptySet(),
                addLocal = onlyRemote - synced,
                removeLocal = if (remoteComplete) onlyLocal intersect synced else emptySet(),
            )
        }
    }
}

/**
 * Tiene Canvas uguale al tuo YouTube (quello che usi dal PC), con l'accesso fatto nell'app:
 * Guarda più tardi = i video nelle Note, i Mi piace = il cuore del player, la cronologia = le barre
 * rosse di "già visto", e i video guardati qui finiscono nella cronologia di YouTube.
 * L'API ufficiale di Google non permette Guarda più tardi né la cronologia, per questo passa dal sito.
 */
@OptIn(FlowPreview::class)
object YtSync {
    private const val PREFS = "yt_sync"
    private const val KEY_LATER = "later"
    private const val KEY_LIKES = "likes"
    private const val KEY_LIKE_PENDING = "like_pending"
    private const val KEY_UNLIKE_PENDING = "unlike_pending"
    private const val KEY_WATCHED = "watched"
    private const val KEY_AT = "at"
    private const val KEY_STATUS = "status"
    /** Guarda più tardi ha al massimo 5000 video: 50 pagine da 100 lo leggono tutto. */
    private const val LATER_PAGES = 50
    /** Dei Mi piace bastano i più recenti. */
    private const val LIKE_PAGES = 5
    /** Al ritorno nell'app si risincronizza solo se è passato almeno questo tempo. */
    private const val MIN_GAP_MS = 3 * 60_000L

    private val scope = MainScope()
    private val mutex = Mutex()
    private var prefs: SharedPreferences? = null
    private var started = false
    private var lastLocalLater: Set<String>? = null

    private val _status = MutableStateFlow<String?>(null)
    /** Una riga per Impostazioni: quando è andata l'ultima sincronizzazione, o cosa non va. */
    val status: StateFlow<String?> = _status.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        prefs ?: context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).also {
            prefs = it
            _status.value = it.getString(KEY_STATUS, null)
        }

    private fun active(): Boolean = runCatching { Accounts.isLoggedIn(MediaService.YOUTUBE) }.getOrDefault(false)

    /** Parte con l'app: sincronizza subito e ogni volta che cambiano i video salvati nelle Note. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        prefs(app)
        NotesStore.init(app)
        scope.launch {
            NotesStore.data
                .map { laterIds(it.notes) }
                .distinctUntilChanged()
                .debounce(2_500)
                .collect { ids -> if (ids != lastLocalLater) sync(app) }
        }
    }

    /** L'app torna in primo piano: le cose fatte dal PC arrivano senza aspettare. */
    fun foreground(context: Context) {
        val p = prefs(context)
        if (System.currentTimeMillis() - p.getLong(KEY_AT, 0) >= MIN_GAP_MS) scope.launch { sync(context.applicationContext) }
    }

    /** Sincronizza tutto: Guarda più tardi, Mi piace e cronologia. */
    suspend fun sync(context: Context) {
        val app = context.applicationContext
        if (!active()) return
        mutex.withLock {
            val p = prefs(app)
            val result = runCatching {
                YouTubeWeb.withAccount(app) { page ->
                    syncLater(app, p, page)
                    syncLikes(app, p, page)
                    syncHistory(app, page)
                }
            }
            p.edit().putLong(KEY_AT, System.currentTimeMillis()).apply()
            setStatus(
                p,
                result.fold(
                    onSuccess = { "Sincronizzato con YouTube alle " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) },
                    onFailure = { e -> if (e is YouTubeWeb.NotLoggedIn) "Accedi a YouTube per sincronizzare" else "Sincronizzazione non riuscita, riprovo" },
                ),
            )
        }
    }

    private fun setStatus(p: SharedPreferences, text: String) {
        _status.value = text
        p.edit().putString(KEY_STATUS, text).apply()
    }

    // ---------- Guarda più tardi = video di YouTube nelle Note ----------

    private suspend fun syncLater(context: Context, p: SharedPreferences, page: YouTubeWeb.AccountPage) {
        val (remoteVideos, complete) = readList(page, "WL", LATER_PAGES)
        val notes = NotesStore.data.value.notes
        val local = laterIds(notes)
        val remote = remoteVideos.map { it.id }.toSet()
        val synced = p.getStringSet(KEY_LATER, emptySet()).orEmpty()
        val plan = SyncPlan.of(remote, local, synced, remoteComplete = complete)

        if (plan.addRemote.isNotEmpty() || plan.removeRemote.isNotEmpty()) {
            val actions = plan.addRemote.map { JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", it) } +
                plan.removeRemote.map { JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", it) }
            actions.chunked(50).forEach { chunk -> editPlaylist(page, "WL", chunk) }
        }
        // I nuovi da YouTube entrano nelle Note nell'ordine in cui sono nella lista.
        remoteVideos.filter { it.id in plan.addLocal }.forEach { video ->
            NotesStore.saveNote(null, SavedLinks.noteText(SavedLinks.youtube(video)))
        }
        val kept = mutableSetOf<String>()
        plan.removeLocal.forEach { id ->
            val removable = removableNotes(NotesStore.data.value.notes, id)
            if (removable.isEmpty()) kept += id else removable.forEach { NotesStore.deleteNote(it.id) }
        }
        val next = plan.synced(remote, local, synced, complete, kept)
        p.edit().putStringSet(KEY_LATER, next).apply()
        lastLocalLater = laterIds(NotesStore.data.value.notes)
    }

    private suspend fun editPlaylist(page: YouTubeWeb.AccountPage, playlistId: String, actions: List<JSONObject>) {
        val body = JSONObject().put("playlistId", playlistId).put("actions", JSONArray(actions))
        val reply = page.run("__cvApi('browse/edit_playlist', $body).then(function(r) { return r.status || ''; })")
        if (!reply.contains("SUCCEEDED")) throw java.io.IOException("YouTube non ha aggiornato la playlist: $reply")
    }

    // ---------- Mi piace ----------

    /** Il cuore del player: il Mi piace va subito anche su YouTube (se non riesce, alla prossima sincronizzazione). */
    fun like(context: Context, video: YtVideo, liked: Boolean) {
        val app = context.applicationContext
        val p = prefs(app)
        val likes = p.getStringSet(KEY_LIKE_PENDING, emptySet()).orEmpty()
        val unlikes = p.getStringSet(KEY_UNLIKE_PENDING, emptySet()).orEmpty()
        p.edit()
            .putStringSet(KEY_LIKE_PENDING, if (liked) likes + video.id else likes - video.id)
            .putStringSet(KEY_UNLIKE_PENDING, if (liked) unlikes - video.id else unlikes + video.id)
            .apply()
        if (!active()) return
        scope.launch {
            mutex.withLock {
                runCatching { YouTubeWeb.withAccount(app) { page -> pushLikes(p, page) } }
            }
            YouTubeStore.refreshLiked(app, force = true)
        }
    }

    /** Manda a YouTube i Mi piace messi o tolti in Canvas e non ancora arrivati. */
    private suspend fun pushLikes(p: SharedPreferences, page: YouTubeWeb.AccountPage) {
        val synced = p.getStringSet(KEY_LIKES, emptySet()).orEmpty().toMutableSet()
        p.getStringSet(KEY_LIKE_PENDING, emptySet()).orEmpty().forEach { id ->
            runCatching { rate(page, id, true) }.onSuccess {
                synced += id
                p.edit().putStringSet(KEY_LIKE_PENDING, p.getStringSet(KEY_LIKE_PENDING, emptySet()).orEmpty() - id).apply()
            }
        }
        p.getStringSet(KEY_UNLIKE_PENDING, emptySet()).orEmpty().forEach { id ->
            runCatching { rate(page, id, false) }.onSuccess {
                synced -= id
                p.edit().putStringSet(KEY_UNLIKE_PENDING, p.getStringSet(KEY_UNLIKE_PENDING, emptySet()).orEmpty() - id).apply()
            }
        }
        p.edit().putStringSet(KEY_LIKES, synced).apply()
    }

    private suspend fun rate(page: YouTubeWeb.AccountPage, id: String, like: Boolean) {
        val body = JSONObject().put("target", JSONObject().put("videoId", id))
        page.run("__cvApi('${if (like) "like/like" else "like/removelike"}', $body).then(function() { return true; })")
    }

    private suspend fun syncLikes(context: Context, p: SharedPreferences, page: YouTubeWeb.AccountPage) {
        pushLikes(p, page)
        val (remoteVideos, complete) = readList(page, "LL", LIKE_PAGES)
        YtFavorites.init(context)
        val localVideos = YtFavorites.items.value
        val local = localVideos.map { it.id }.toSet()
        val remote = remoteVideos.map { it.id }.toSet()
        val synced = p.getStringSet(KEY_LIKES, emptySet()).orEmpty()
        val unliking = p.getStringSet(KEY_UNLIKE_PENDING, emptySet()).orEmpty()
        val plan = SyncPlan.of(remote, local, synced, remoteComplete = complete, pushRemovals = false, ignore = unliking)
        val pushed = mutableSetOf<String>()
        plan.addRemote.forEach { id -> if (runCatching { rate(page, id, true) }.isSuccess) pushed += id }
        val added = remoteVideos.filter { it.id in plan.addLocal }
        if (added.isNotEmpty() || plan.removeLocal.isNotEmpty()) {
            YtFavorites.replace(context, mergeLikes(localVideos, remoteVideos, plan.addLocal, plan.removeLocal))
        }
        val applied = plan.copy(addRemote = pushed)
        p.edit().putStringSet(KEY_LIKES, applied.synced(remote, local, synced, complete)).apply()
        if (added.isNotEmpty() || pushed.isNotEmpty()) YouTubeStore.refreshLiked(context, force = true)
    }

    // ---------- Cronologia ----------

    private suspend fun syncHistory(context: Context, page: YouTubeWeb.AccountPage) {
        val raw = page.run("__cvApi('browse', {browseId: 'FEhistory'}).then(function(r) { return __cvCollect(r).videos; })")
        val videos = YouTubeWeb.videos(runCatching { JSONArray(raw) }.getOrNull())
        if (videos.isNotEmpty()) YtHistory.mergeRemote(context, videos)
    }

    /**
     * Un video guardato in Canvas va anche nella cronologia di YouTube, col punto a cui sei arrivato:
     * una volta quando l'hai iniziato davvero e una quando l'hai finito.
     */
    fun watched(context: Context, id: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 30_000) return
        val app = context.applicationContext
        val p = prefs(app)
        val done = durationMs > 0 && positionMs >= durationMs - 15_000
        val state = if (done) "d" else "s"
        val sent = readWatched(p)
        if (sent[id] == "d" || sent[id] == state) return
        sent[id] = state
        writeWatched(p, sent)
        if (!active()) return
        scope.launch {
            mutex.withLock {
                runCatching {
                    YouTubeWeb.withAccount(app) { page -> markWatched(page, id, positionMs / 1000) }
                }.onFailure {
                    // Riprova la prossima volta che salvi il punto.
                    writeWatched(p, readWatched(p).apply { remove(id) })
                }
            }
        }
    }

    /** Come fa il sito: chiede il video col tuo accesso e segnala la visione agli indirizzi di statistica. */
    private suspend fun markWatched(page: YouTubeWeb.AccountPage, id: String, seconds: Long) {
        page.run(
            """
            (async function() {
              var p = await __cvApi('player', {videoId: ${JSONObject.quote(id)}, contentCheckOk: true, racyCheckOk: true});
              var t = p && p.playbackTracking;
              if (!t || !t.videostatsPlaybackUrl) throw new Error('niente statistiche per questo video');
              var abc = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_';
              var cpn = '';
              for (var i = 0; i < 16; i++) cpn += abc[Math.floor(Math.random() * 64)];
              var at = String($seconds);
              async function ping(base, extra) {
                var url = new URL(base.indexOf('//') === 0 ? 'https:' + base : base);
                var q = {ver: '2', cpn: cpn, cmt: at, el: 'detailpage'};
                for (var k in extra) q[k] = extra[k];
                for (var k2 in q) url.searchParams.set(k2, q[k2]);
                await fetch(url.toString(), {credentials: 'include', mode: 'no-cors'});
              }
              await ping(t.videostatsPlaybackUrl.baseUrl, {});
              if (t.videostatsWatchtimeUrl) await ping(t.videostatsWatchtimeUrl.baseUrl, {st: at, et: at});
              return true;
            })()
            """.trimIndent(),
        )
    }

    private fun readWatched(p: SharedPreferences): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        runCatching {
            val o = JSONObject(p.getString(KEY_WATCHED, "{}") ?: "{}")
            o.keys().forEach { map[it] = o.getString(it) }
        }
        return map
    }

    private fun writeWatched(p: SharedPreferences, map: Map<String, String>) {
        val o = JSONObject()
        map.entries.toList().takeLast(300).forEach { o.put(it.key, it.value) }
        p.edit().putString(KEY_WATCHED, o.toString()).apply()
    }

    // ---------- Comuni ----------

    /** Tutta una playlist del tuo account, pagina dopo pagina; dice anche se l'ha letta fino in fondo. */
    private suspend fun readList(page: YouTubeWeb.AccountPage, playlistId: String, maxPages: Int): Pair<List<YtVideo>, Boolean> {
        val raw = page.run(
            """
            (async function() {
              var r = await __cvApi('browse', {browseId: ${JSONObject.quote("VL$playlistId")}});
              var all = __cvCollect(r).videos;
              var token = __cvCont(r);
              var pages = 1;
              while (token && pages < $maxPages) {
                r = await __cvApi('browse', {continuation: token});
                all = all.concat(__cvCollect(r).videos);
                token = __cvCont(r);
                pages++;
              }
              return {videos: all, complete: !token};
            })()
            """.trimIndent(),
            timeoutMs = 120_000,
        )
        val o = JSONObject(raw)
        return YouTubeWeb.videos(o.optJSONArray("videos")) to o.optBoolean("complete")
    }

    /** I video di YouTube nelle Note (Più tardi della pagina Video). */
    internal fun laterIds(notes: List<Note>): Set<String> = SavedLinks.youtubeVideos(notes).map { it.id }.toSet()

    /**
     * Le note da cancellare quando il video sparisce da Guarda più tardi: quelle salvate da Canvas e
     * quelle che sono solo il link; una nota con altro testo scritto da te non si tocca.
     */
    internal fun removableNotes(notes: List<Note>, id: String): List<Note> = notes.filter { note ->
        val saved = (SavedLinks.find(note.text) as? SavedLink.Media)?.item
        if (saved != null) return@filter saved.kind == ResumeKind.YOUTUBE && saved.key == id
        val links = Regex("""https?://\S*(?:youtube\.com|youtu\.be)/\S+""").findAll(note.text).map { it.value }.toList()
        if (links.none { YtResolver.videoId(it) == id }) return@filter false
        links.fold(note.text) { text, link -> text.replace(link, "") }.isBlank()
    }

    /** I Mi piace dopo la sincronizzazione: i nuovi da YouTube in cima, nell'ordine di YouTube. */
    internal fun mergeLikes(local: List<YtVideo>, remote: List<YtVideo>, add: Set<String>, remove: Set<String>): List<YtVideo> =
        (remote.filter { it.id in add } + local.filterNot { it.id in remove }).distinctBy { it.id }
}
