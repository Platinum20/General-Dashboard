package com.platinum20.dashboard

import com.platinum20.dashboard.news.NewsLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsLayoutTest {
    @Test
    fun fotoVereRestano() {
        val url = "https://img.fanpage.it/wp-content/uploads/2026/10/foto-1200x675.jpg?w=1200"
        assertEquals(url, NewsLayout.usableImage(url))
        assertEquals("https://www.ilpost.it/a.webp", NewsLayout.usableImage("  https://www.ilpost.it/a.webp "))
    }

    @Test
    fun senzaFotoLaCardESoloTesto() {
        assertNull(NewsLayout.usableImage(null))
        assertNull(NewsLayout.usableImage(""))
        assertNull(NewsLayout.usableImage("/wp-content/foto.jpg"))
        assertNull(NewsLayout.usableImage("data:image/png;base64,AAAA"))
        assertNull(NewsLayout.usableImage("https://www.internazionale.it/static/logo-internazionale.svg"))
        assertNull(NewsLayout.usableImage("https://cdn.example.it/img/placeholder-news.jpg"))
        assertNull(NewsLayout.usableImage("https://feeds.example.it/pixel.gif?id=3"))
        assertNull(NewsLayout.usableImage("https://www.example.it/images/default.png"))
    }

    @Test
    fun chipDelleFontiFerme() {
        assertNull(NewsLayout.failureChip(0))
        assertEquals("1 fonte non risponde", NewsLayout.failureChip(1))
        assertEquals("3 fonti non rispondono", NewsLayout.failureChip(3))
        assertEquals("Fanpage non risponde, riprovo al prossimo aggiornamento", NewsLayout.failureDetail(listOf("Fanpage")))
        assertEquals(
            "Fanpage, Il Post non rispondono, riprovo al prossimo aggiornamento",
            NewsLayout.failureDetail(listOf("Fanpage", "Il Post")),
        )
    }
}
