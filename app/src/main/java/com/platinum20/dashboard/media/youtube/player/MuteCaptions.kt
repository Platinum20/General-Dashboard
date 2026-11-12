package com.platinum20.dashboard.media.youtube.player

/**
 * Sottotitoli col volume a zero (Antonio, 1.490): senza audio il video si segue leggendo, quindi si
 * accendono da soli e si spengono quando il volume risale. Una scelta fatta a mano vince sempre:
 * sottotitoli scelti restano accesi, "Spenti" scelto a volume zero resta spento fino al prossimo zero.
 */
object MuteCaptions {
    enum class Step { ON, OFF, NONE }

    /**
     * Cosa fare quando il volume cambia. [chosen] = lingua scelta a mano (null = nessuna),
     * [autoOn] = accesi da noi, [declined] = spenti a mano durante questo zero, [showing] = lingua a schermo.
     */
    fun step(muted: Boolean, chosen: String?, autoOn: Boolean, declined: Boolean, showing: String?): Step = when {
        chosen != null -> Step.NONE
        muted && !declined && showing == null -> Step.ON
        !muted && autoOn -> Step.OFF
        else -> Step.NONE
    }

    /**
     * Quali accendere: quelli nella lingua originale del video (Antonio, 1.498), scritti a mano prima
     * degli automatici; mai una traduzione di YouTube se c'è altro.
     */
    fun pick(subtitles: List<YtSubtitle>): YtSubtitle? = subtitles.minWithOrNull(
        compareBy<YtSubtitle>({ !it.original }, { "tlang=" in it.url }, { it.auto }),
    )
}
