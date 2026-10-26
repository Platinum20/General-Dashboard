package com.platinum20.dashboard.media

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Il servizio del player unico: una sola notifica, schermata di blocco e cuffie per qualunque
 * fonte (YouTube, musica, radio, podcast e anche Spotify, che suona nel suo riproduttore).
 */
@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        CanvasPlayer.init(this)
        // Il motore nasce qui se non c'era già: la notifica lo segue dal primo istante.
        CanvasPlayer.player(this)
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.MEDIA, source = SOURCE_PLAYER).writeTo(open)
        val player = SessionPlayer()
        session = MediaSession.Builder(this, player)
            .setId("canvas")
            .setSessionActivity(PendingIntent.getActivity(this, 310, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
            // L'app comanda il motore direttamente, senza collegarsi alla sessione: senza questa riga
            // il servizio non conosce la sessione e la notifica con copertina e comandi non compare mai.
            .also(::addSession)
        scope.launch { combine(CanvasPlayer.now, CanvasPlayer.queue) { _, _ -> }.collect { player.refresh() } }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (CanvasPlayer.now.value?.playing != true) stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        CanvasPlayer.release()
        super.onDestroy()
    }

    companion object {
        const val SOURCE_PLAYER = "PLAYER"
    }
}

/**
 * Quello che il sistema vede (notifica, schermata di blocco, cuffie, auto): lo stato del player unico
 * e i suoi comandi, qualunque cosa suoni. Mostra la voce di prima, quella corrente e quella dopo,
 * così "avanti" e "indietro" ci sono quando servono.
 */
@OptIn(UnstableApi::class)
private class SessionPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    fun refresh() = invalidateState()

    override fun getState(): State {
        val now = CanvasPlayer.now.value
        val queue = CanvasPlayer.queue.value
        val current = queue.now
        val builder = State.Builder()
        if (now == null || current == null || current.id != now.entryId) {
            return builder
                .setAvailableCommands(Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_RELEASE).build())
                .setPlaybackState(Player.STATE_IDLE)
                .build()
        }
        val window = listOfNotNull(
            queue.entries.getOrNull(queue.current - 1),
            current,
            queue.entries.getOrNull(queue.current + 1),
        )
        val seekable = !now.live && now.durationMs > 0
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA, Player.COMMAND_RELEASE,
        ).apply { if (seekable) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }.build()
        return builder
            .setAvailableCommands(commands)
            .setPlaylist(window.map { entry -> itemData(entry, if (entry.id == now.entryId) now else null) })
            .setCurrentMediaItemIndex(window.indexOf(current))
            .setPlayWhenReady(now.playing || now.loading, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (now.loading) Player.STATE_BUFFERING else if (now.error != null) Player.STATE_IDLE else Player.STATE_READY)
            .setContentPositionMs { CanvasPlayer.now.value?.positionMs ?: 0 }
            .build()
    }

    private fun itemData(entry: QueueEntry, now: EngineNow?): MediaItemData {
        val item = entry.item
        val title = if (now != null && now.streamTitle.isNotBlank()) now.streamTitle else item.title
        val artist = if (item.kind == ResumeKind.RADIO) item.title else item.subtitle
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "Caricamento…" })
            .setArtist(artist)
            .apply { if (item.image.isNotBlank()) setArtworkUri(Uri.parse(item.image)) }
            .build()
        val duration = (now?.durationMs ?: item.durationMs).takeIf { it > 0 && item.kind != ResumeKind.RADIO }
        return MediaItemData.Builder(entry.id)
            .setMediaItem(MediaItem.Builder().setMediaId(entry.id.toString()).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setDurationUs(duration?.let { it * 1000 } ?: C.TIME_UNSET)
            .setIsSeekable(duration != null)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val now = CanvasPlayer.now.value
        if (playWhenReady != (now?.playing == true)) {
            if (playWhenReady) CanvasPlayer.toggle() else CanvasPlayer.pause()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        CanvasPlayer.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val current = currentMediaItemIndex
        when {
            mediaItemIndex > current -> Playback.next()
            mediaItemIndex in 0 until current -> CanvasPlayer.previous()
            else -> CanvasPlayer.seekTo(if (positionMs == C.TIME_UNSET) 0 else positionMs)
        }
        return Futures.immediateVoidFuture()
    }
}
