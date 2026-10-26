package com.platinum20.dashboard.media

import android.content.Context
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
}
