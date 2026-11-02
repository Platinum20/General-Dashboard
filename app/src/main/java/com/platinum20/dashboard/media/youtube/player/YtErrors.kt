package com.platinum20.dashboard.media.youtube.player

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

/**
 * Errori di YouTube detti in una riga in italiano: mai il testo inglese dell'estrattore sullo schermo.
 */
object YtErrors {
    /** YouTube chiede di "dimostrare di non essere un bot" a chi guarda senza account da questa rete. */
    fun isBotCheck(e: Throwable): Boolean = chain(e).any { c ->
        c is SignInConfirmNotBotException || c is ReCaptchaException ||
            c.message.orEmpty().let { "not a bot" in it || "LOGIN_REQUIRED" in it && "bot" in it }
    }

    fun message(e: Throwable): String {
        val all = chain(e)
        return when {
            isBotCheck(e) -> "YouTube blocca questa rete per un po': riprova o passa al Wi-Fi"
            all.any { it is AgeRestrictedContentException } -> "Video vietato ai minori: YouTube lo mostra solo con l'accesso"
            all.any { it is PrivateContentException } -> "Questo video è privato"
            all.any { it is PaidContentException } -> "Video solo per gli abbonati del canale"
            all.any { it is GeographicRestrictionException } -> "Video non disponibile in Italia"
            all.any { it is IOException } -> "Connessione assente o lenta: riprova"
            else -> "Non riesco ad aprire il video"
        }
    }

    private fun chain(e: Throwable): List<Throwable> = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }.take(8).toList()
}
