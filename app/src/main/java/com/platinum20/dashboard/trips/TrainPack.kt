package com.platinum20.dashboard.trips

/** Cosa si porta nello zaino: un episodio di podcast o un video di YouTube. */
enum class PackKind { PODCAST, VIDEO }

/**
 * Lo zaino del treno (1.468): la sera prima del viaggio, col Wi-Fi, si scarica quello che servirà in treno
 * (podcast salvati e nuovi, video di "Più tardi"), così sul treno la coda è piena e la SIM resta intatta.
 * Qui le regole pure: quanto portare, quanto pesa, quando buttarlo, cosa dire. Testate.
 */
object TrainPack {
    /** Mai più di 2 GB sul telefono. */
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024
    /** Si prepara per i treni che partono entro un giorno. */
    const val LOOKAHEAD_MS = 24 * 3_600_000L
    /** Dopo il ritorno resta ancora qualche ora (un episodio a metà sull'autobus), poi si cancella. */
    const val KEEP_AFTER_MS = 6 * 3_600_000L
    /** Durata ipotetica di un episodio che il feed non dice. */
    const val UNKNOWN_SEC = 30 * 60L

    /** Quanto portare: un quarto in più del viaggio, così non si resta a secco a metà. */
    fun target(tripSec: Long): Long = tripSec + tripSec / 4

    /** Peso stimato: audio a 128 kbit/s, video a 360p (circa 640 kbit/s). */
    fun estimateBytes(kind: PackKind, sec: Long): Long = when (kind) {
        PackKind.PODCAST -> sec * 16_000
        PackKind.VIDEO -> sec * 80_000
    }

    /**
     * Si prende ancora [itemSec]? Sì finché non si arriva a [target] e c'è posto; un video più lungo del viaggio
     * no (non lo finiresti), un episodio lungo sì (si riprende dopo).
     */
    fun keep(kind: PackKind, tripSec: Long, packedSec: Long, packedBytes: Long, itemSec: Long, itemBytes: Long): Boolean {
        if (packedSec >= target(tripSec)) return false
        if (packedBytes + itemBytes > MAX_BYTES) return false
        if (kind == PackKind.VIDEO && itemSec > tripSec) return false
        return true
    }

    /** Da buttare: il viaggio è finito da più di [KEEP_AFTER_MS]. */
    fun expired(until: Long, now: Long): Boolean = now > until + KEEP_AFTER_MS

    /** La riga sotto il treno: "Pronti 3 h 20 per il viaggio". Null se non c'è niente. */
    fun readyLine(totalSec: Long): String? {
        if (totalSec <= 0) return null
        val minutes = (totalSec + 59) / 60
        val h = minutes / 60
        val m = minutes % 60
        val time = when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m"
        }
        return "Pronti $time per il viaggio"
    }
}
