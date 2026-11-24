package com.platinum20.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La regola della barra e del + (1.622): elenchi sì, dentro una cosa sola no. */
class BarRuleTest {
    @Test
    fun elenchiHannoLaBarra() {
        listOf(
            Routes.HOME, Routes.NEWS, Routes.MEDIA, Routes.NOTES, Routes.LATER_ARCHIVE, Routes.JOTTINGS_ARCHIVE,
            Routes.WEATHER, Routes.SIM, Routes.TAG, Routes.PERSON, Routes.RADIO, Routes.PODCAST, Routes.SPOTIFY_ITEM,
            Routes.YOUTUBE_CHANNEL, Routes.YOUTUBE_PLAYLIST, Routes.MEDIA_MIX, Routes.SAVED_MEDIA, Routes.MUSIC_ALL,
        ).forEach { assertTrue(it, Routes.hasBar(it)) }
    }

    @Test
    fun dentroUnaCosaNiente() {
        listOf(
            Routes.NOTE_EDIT, Routes.NOTE_ITEM, Routes.CALENDAR_EVENT, Routes.CALENDAR_EDIT, Routes.MAIL, Routes.DOCUMENT,
            Routes.READER, Routes.PLAYER, Routes.YOUTUBE_WATCH, Routes.SETTINGS, Routes.SETTINGS_PAGE, Routes.SEARCH_PATTERN,
            Routes.CUSTOMIZE, Routes.LOGIN, Routes.WEATHER_PLACE, null,
        ).forEach { assertFalse(it.toString(), Routes.hasBar(it)) }
    }

    @Test
    fun laSchedaAccesaEQuellaDaCuiVieni() {
        assertEquals(Routes.HOME, Routes.barTab(Routes.LATER_ARCHIVE))
        assertEquals(Routes.HOME, Routes.barTab(Routes.TAG))
        assertEquals(Routes.HOME, Routes.barTab(Routes.WEATHER))
        assertEquals(Routes.MEDIA, Routes.barTab(Routes.RADIO))
        assertEquals(Routes.MEDIA, Routes.barTab(Routes.SPOTIFY_ITEM))
        assertEquals(Routes.NEWS, Routes.barTab(Routes.NEWS))
    }
}
