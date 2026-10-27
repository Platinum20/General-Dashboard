package com.platinum20.dashboard

import androidx.test.core.app.ApplicationProvider
import com.platinum20.dashboard.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Escludi Shorts / live stanno in Impostazioni e restano scelti (anche nel backup). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoFilterSettingsTest {
    @Test
    fun filtriRestanoScelti() {
        AppSettings.init(ApplicationProvider.getApplicationContext())
        AppSettings.setHideShorts(false)
        AppSettings.setHideLive(true)
        AppSettings.setHideMembers(false)
        val saved = AppSettings.snapshot()
        assertEquals(false, saved["video_hide_shorts"])
        assertEquals(true, saved["video_hide_live"])
        assertEquals(false, saved["video_hide_members"])
        AppSettings.setHideShorts(true)
        AppSettings.setHideLive(false)
        AppSettings.setHideMembers(true)
        AppSettings.restore(saved)
        assertFalse(AppSettings.hideMembers.value)
        assertFalse(AppSettings.hideShorts.value)
        assertTrue(AppSettings.hideLive.value)
        AppSettings.setHideShorts(true)
        AppSettings.setHideLive(false)
        AppSettings.setHideMembers(true)
    }
}
