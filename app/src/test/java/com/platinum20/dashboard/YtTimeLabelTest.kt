package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.timeLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il minutaggio sul video non deve mai dire "0:00 / 0:00" quando la durata si conosce già (V18). */
class YtTimeLabelTest {
    @Test fun durataDelPlayer() = assertEquals("1:05 / 3:33", timeLabel(65_000, 213_000, 0))

    @Test fun primaDelCaricamentoUsaLaDurataDelVideo() = assertEquals("0:00 / 3:33", timeLabel(0, 0, 213))

    @Test fun ilPlayerVinceSullaDurataNota() = assertEquals("0:10 / 3:34", timeLabel(10_000, 214_000, 213))

    @Test fun durataSconosciuta() = assertEquals("--:-- / --:--", timeLabel(0, 0, 0))

    @Test fun oreENegativi() {
        assertEquals("0:00 / 1:02:03", timeLabel(-5, 3_723_000, 0))
    }
}
