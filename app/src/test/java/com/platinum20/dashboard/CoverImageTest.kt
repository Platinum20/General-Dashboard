package com.platinum20.dashboard

import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.coverImage
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.493: i video del telefono senza immagine usano il file per il fotogramma; quelli da link no. */
class CoverImageTest {
    private fun item(kind: ResumeKind, key: String, image: String = "") = ResumeItem(kind, key, "T", "S", image, at = 1)

    @Test fun videoDelTelefonoUsaIlFile() {
        assertEquals("content://media/1", item(ResumeKind.VIDEO, "content://media/1").coverImage())
    }

    @Test fun videoDaLinkResterebbeVuoto() {
        assertEquals("", item(ResumeKind.VIDEO, "https://x.it/v.mp4").coverImage())
    }

    @Test fun lImmagineVinceSempre() {
        assertEquals("img", item(ResumeKind.VIDEO, "content://media/1", "img").coverImage())
        assertEquals("", item(ResumeKind.SPOTIFY, "content://x").coverImage())
    }
}
