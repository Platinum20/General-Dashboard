package com.platinum20.dashboard.today

import com.platinum20.dashboard.media.Resume
import com.platinum20.dashboard.media.ResumeItem

/**
 * La card "Riprendi" della Home: la cosa lasciata a metà nelle ultime 24 ore, in un tocco.
 * Sta fuori da "Adesso", che dice solo cose con un orario o una scadenza.
 */
object HomeResume {
    /** Niente card se qualcosa sta già suonando: c'è il mini player, e si ascolta una cosa alla volta. */
    fun pick(items: List<ResumeItem>, now: Long, somethingPlaying: Boolean): ResumeItem? =
        if (somethingPlaying) null else Resume.forAdesso(items, now)

    /** "YouTube · 12:30 di 45:00": fonte e punto da cui riparte. */
    fun detail(item: ResumeItem): String =
        listOf(item.subtitle, Resume.progressLabel(item)).filter { it.isNotBlank() }.joinToString(" · ")
}
