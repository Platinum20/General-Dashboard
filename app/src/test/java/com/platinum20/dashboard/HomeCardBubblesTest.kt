package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.BubbleSlot
import com.platinum20.dashboard.ui.screens.musicCardActions
import com.platinum20.dashboard.ui.screens.videoCardActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le card Musica e Video tenute premute: ogni riga ha la sua frase, Apri è il tasto grande e porta alla pagina della card. */
class HomeCardBubblesTest {
    @Test
    fun musicBubbleOpensDiscoverAndRowsHaveSubtitles() {
        val opened = mutableListOf<String>()
        val actions = musicCardActions { opened += it }
        actions.filter { it.slot == null }.forEach { assertTrue(it.title, it.detail.isNotBlank()) }
        actions.single { it.slot == BubbleSlot.MAIN }.onClick()
        assertEquals(listOf(Routes.MUSIC_DISCOVER), opened)
    }

    @Test
    fun videoBubbleOpensScopri() {
        val opened = mutableListOf<String>()
        videoCardActions { opened += it }.single { it.slot == BubbleSlot.MAIN }.onClick()
        assertEquals(listOf(Routes.MEDIA_NEWS), opened)
    }
}
