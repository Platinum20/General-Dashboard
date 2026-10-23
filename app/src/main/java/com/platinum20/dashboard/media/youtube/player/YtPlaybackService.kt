package com.platinum20.dashboard.media.youtube.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.media.MediaService

/** YouTube in sottofondo con il player nativo: notifica, schermata di blocco e cuffie. */
class YtPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.MEDIA, source = MediaService.YOUTUBE.name).writeTo(open)
        session = MediaSession.Builder(this, YtPlayer.player(this))
            .setId("youtube")
            .setSessionActivity(PendingIntent.getActivity(this, 310, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
            // L'app comanda il player direttamente, senza collegarsi alla sessione: senza questa riga
            // il servizio non conosce la sessione e la notifica con copertina e comandi non compare mai.
            .also(::addSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        YtPlayer.release()
        super.onDestroy()
    }
}
