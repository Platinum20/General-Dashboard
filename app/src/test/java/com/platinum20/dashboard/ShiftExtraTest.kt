package com.platinum20.dashboard

import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.shifts.ShiftEarning
import com.platinum20.dashboard.shifts.ShiftPay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Turno allungato su Creators Pro: la fine vera viene dalla busta paga, ovunque. */
class ShiftExtraTest {
    private val h = 3_600_000L
    private val start = 1_800_000_000_000L
    private val shift = Shift("sh1", start, start + 5 * h)
    private fun pay(from: Long, to: Long) = ShiftEarning("sh1", from, to, 0.0, 0.0, 0.1, 0.0)

    @Test fun extraSpostaLaFine() {
        // 17–22 allungato di un'ora: finisce alle 23.
        assertEquals(start + 6 * h, ShiftPay.endWithExtra(shift, pay(start, start + 6 * h)))
    }

    @Test fun oraSfasataNonContaComeExtra() {
        // La busta paga un'ora avanti (ora estiva) ma lunga 5 ore: nessun extra.
        assertEquals(shift.end, ShiftPay.endWithExtra(shift, pay(start + h, start + 6 * h)))
    }

    @Test fun maiPiuCortoNeAssurdo() {
        assertEquals(shift.end, ShiftPay.endWithExtra(shift, pay(start, start + 3 * h)))
        assertEquals(shift.end, ShiftPay.endWithExtra(shift, pay(start, start + 20 * h)))
        assertEquals(shift.end, ShiftPay.endWithExtra(shift, null))
    }

    @Test fun soloIlTurnoAllungatoCambia() {
        val other = Shift("sh2", start + 24 * h, start + 29 * h)
        val out = ShiftPay.withExtra(listOf(shift, other), listOf(pay(start, start + 7 * h)))
        assertEquals(start + 7 * h, out[0].end)
        assertSame(other, out[1])
    }
}
