package com.platinum20.dashboard.media

import android.content.Context
import android.net.Uri
import com.platinum20.dashboard.Routes

/** Fa partire una voce (di Continua, di una nota) nel player unico. */
object MediaLauncher {
    /**
     * Avvia [item] e restituisce la schermata da aprire per vederlo: sempre il player unico, che
     * mostra il video se c'è. [autoplay] falso: si carica fermo, in attesa del play.
     */
    fun start(context: Context, item: ResumeItem, autoplay: Boolean = true): String {
        CanvasPlayer.play(context, item, autoplay)
        return Routes.PLAYER
    }

    /** Un video o un audio del telefono o da un link: entra nei Recenti e suona nel player unico. */
    fun playVideo(context: Context, uri: Uri): String {
        VideoLibrary.init(context)
        return start(context, ResumeItem.fromVideo(VideoLibrary.open(context, uri)))
    }
}
