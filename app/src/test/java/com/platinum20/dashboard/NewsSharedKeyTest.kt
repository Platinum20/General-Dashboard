package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.NewsShared
import com.platinum20.dashboard.ui.screens.SharedRows
import com.platinum20.dashboard.ui.screens.newsSharedKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** La card News si allarga nella pagina: i titoli della card e quelli della pagina non devono volare fra loro. */
class NewsSharedKeyTest {
    @Test
    fun cardAndPageRowsHaveDifferentKeys() {
        assertNotEquals(newsSharedKey("https://a", SharedRows.HOME), newsSharedKey("https://a", SharedRows.PAGE))
    }

    @Test
    fun readerFollowsTheListItCameFrom() {
        NewsShared.origin = SharedRows.HOME
        assertEquals(newsSharedKey("https://a", SharedRows.HOME), newsSharedKey("https://a"))
        NewsShared.origin = SharedRows.PAGE
        assertEquals(newsSharedKey("https://a", SharedRows.PAGE), newsSharedKey("https://a"))
    }
}
