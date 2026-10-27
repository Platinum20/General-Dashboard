package com.platinum20.dashboard

import androidx.test.core.app.ApplicationProvider
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureNotice
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Gli avvisi passano dal fumetto di vetro; il Toast resta solo fuori dalla schermata principale. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CanvasNoticeTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun reset() {
        CaptureFeedback.active = false
    }

    @Test
    fun activeScreenGetsBubbleWithoutFlight() = runBlocking {
        CaptureFeedback.active = true
        val next = async(start = CoroutineStart.UNDISPATCHED) { CaptureFeedback.events.first() }
        CaptureFeedback.tell(context, "Backup salvato")
        val n: CaptureNotice = next.await()
        assertEquals("Backup salvato", n.message)
        assertFalse(n.fly)
        assertNull(n.undo)
        assertNull(ShadowToast.getLatestToast())
    }

    @Test
    fun actionKeepsItsLabel() = runBlocking {
        CaptureFeedback.active = true
        val next = async(start = CoroutineStart.UNDISPATCHED) { CaptureFeedback.events.first() }
        CaptureFeedback.tell(context, "Permesso negato", actionLabel = "Info app", action = {})
        assertEquals("Info app", next.await().actionLabel)
    }

    @Test
    fun withoutMainScreenFallsBackToToast() {
        CaptureFeedback.tell(context, "Dettatura non disponibile")
        assertEquals("Dettatura non disponibile", ShadowToast.getTextOfLatestToast())
    }
}
