package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.player.YtErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

class YtErrorsTest {
    private val bot = SignInConfirmNotBotException(
        "YouTube probably temporarily blocked anonymous watch access with this IP , got error LOGIN_REQUIRED: \"Sign in to confirm that you're not a bot\"",
    )

    @Test
    fun controlloAntiBotRiconosciutoAncheAvvolto() {
        assertTrue(YtErrors.isBotCheck(bot))
        assertTrue(YtErrors.isBotCheck(RuntimeException("wrap", bot)))
        assertFalse(YtErrors.isBotCheck(IOException("timeout")))
    }

    @Test
    fun messaggiCortiInItaliano() {
        val msg = YtErrors.message(bot)
        assertFalse("bot" in msg || "YouTube probably" in msg)
        assertTrue(msg.length < 70)
        assertEquals("Questo video è privato", YtErrors.message(PrivateContentException("This video is private")))
        assertEquals("Connessione assente o lenta: riprova", YtErrors.message(IOException("x")))
        assertEquals("Non riesco ad aprire il video", YtErrors.message(IllegalStateException("Some English text")))
    }
}
