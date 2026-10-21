package com.platinum20.dashboard.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import androidx.core.app.ServiceCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servizio in primo piano mentre YouTube o Spotify suonano: l'audio continua a schermo spento
 * e in notifica (e sulla schermata di blocco) ci sono copertina, titolo e comandi.
 */
class MediaPlaybackService : Service() {
    private val scope = MainScope()
    private lateinit var session: MediaSession
    private var artworkUrl: String? = null
    private var artwork: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
        session = MediaSession(this, "GeneralDashboard").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = toggleIf(playing = false)
                override fun onPause() = toggleIf(playing = true)
                override fun onSkipToNext() = send(PlayerCommand.NEXT)
                override fun onSkipToPrevious() = send(PlayerCommand.PREVIOUS)
                override fun onStop() = close()
            })
            isActive = true
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(NowPlaying.current),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        scope.launch { NowPlaying.tracks.collect { refresh() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> send(PlayerCommand.TOGGLE)
            ACTION_NEXT -> send(PlayerCommand.NEXT)
            ACTION_PREVIOUS -> send(PlayerCommand.PREVIOUS)
            ACTION_CLOSE -> close()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        session.release()
        NowPlaying.onServiceStopped()
        super.onDestroy()
    }

    private fun send(command: PlayerCommand) {
        NowPlaying.current?.let { NowPlaying.command(it.service, command) }
    }

    private fun toggleIf(playing: Boolean) {
        if (NowPlaying.current?.playing == playing) send(PlayerCommand.TOGGLE)
    }

    private fun close() {
        MediaWebViews.releaseAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refresh() {
        val track = NowPlaying.current
        if (track != null && track.artworkUrl != artworkUrl) {
            artworkUrl = track.artworkUrl
            artwork = null
            if (track.artworkUrl.isNotBlank()) loadArtwork(track.artworkUrl)
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, track?.title.orEmpty())
                .putString(MediaMetadata.METADATA_KEY_ARTIST, track?.artist.orEmpty())
                .apply { artwork?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP
                )
                .setState(
                    if (track?.playing == true) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1f,
                )
                .build()
        )
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(track))
    }

    private fun loadArtwork(url: String) {
        scope.launch {
            val request = ImageRequest.Builder(this@MediaPlaybackService).data(url).allowHardware(false).build()
            val bitmap = runCatching { imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            if (url == artworkUrl && bitmap != null) {
                artwork = bitmap
                refresh()
            }
        }
    }

    private val imageLoader by lazy { SingletonImageLoader.get(this) }

    private fun buildNotification(track: Track?): Notification {
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.MEDIA, source = track?.service?.name.orEmpty()).writeTo(open)
        val playing = track?.playing == true
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(track?.title ?: "Player aperto")
            .setContentText(listOfNotNull(track?.artist?.takeIf { it.isNotBlank() }, track?.service?.label).joinToString(" · "))
            .setLargeIcon(artwork)
            .setContentIntent(PendingIntent.getActivity(this, 200, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(playing)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, "Indietro", ACTION_PREVIOUS))
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, "Pausa", ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, "Riproduci", ACTION_TOGGLE)
            )
            .addAction(action(android.R.drawable.ic_media_next, "Avanti", ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Chiudi", ACTION_CLOSE))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun action(icon: Int, title: String, action: String): Notification.Action {
        val intent = Intent(this, MediaPlaybackService::class.java).setAction(action)
        val pending = PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pending).build()
    }

    companion object {
        private const val CHANNEL = "media_playback"
        private const val NOTIFICATION_ID = 3001
        private const val ACTION_TOGGLE = "com.platinum20.dashboard.media.TOGGLE"
        private const val ACTION_NEXT = "com.platinum20.dashboard.media.NEXT"
        private const val ACTION_PREVIOUS = "com.platinum20.dashboard.media.PREVIOUS"
        private const val ACTION_CLOSE = "com.platinum20.dashboard.media.CLOSE"

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "In riproduzione", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Comandi di YouTube e Spotify mentre suonano"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
