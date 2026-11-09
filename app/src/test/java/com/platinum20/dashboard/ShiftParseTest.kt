package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.shifts.ShiftParse
import com.platinum20.dashboard.ui.screens.shiftsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/** Il formato della timeline di Creators Pro non è pubblico: la lettura deve capire le forme comuni e prendere solo Antonio. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ShiftParseTest {
    private val zone = ZoneId.of("Europe/Rome")
    private fun at(d: Int, h: Int) = ZonedDateTime.of(2026, 11, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun turniConPersonaDentro() {
        val json = """
            {"data":[
              {"id":"a1","start":"2026-11-10T07:00:00+01:00","end":"2026-11-10T12:00:00+01:00","user":{"id":7,"name":"Antonio Nesca"}},
              {"id":"a2","start":"2026-11-10T12:00:00+01:00","end":"2026-11-10T17:00:00+01:00","user":{"id":8,"name":"Mario Rossi"}}
            ]}
        """.trimIndent()
        val read = ShiftParse.read(json, zone = zone)!!
        assertEquals(listOf("a1"), read.mine.map { it.id })
        assertEquals(at(10, 7), read.mine[0].start)
        assertEquals(at(10, 12), read.mine[0].end)
        assertEquals(at(10, 7), read.from)
        assertEquals(at(10, 17), read.to)
    }

    @Test
    fun righeDellaTimelineConId() {
        val json = """
            {"resources":[{"id":"u1","title":"Antonio Nesca"},{"id":"u2","title":"Giulia Bianchi"}],
             "events":[
               {"id":"e1","resourceId":"u1","startTime":"2026-11-11 12:00:00","endTime":"2026-11-11 17:00:00","title":"Chat"},
               {"id":"e2","resourceId":"u2","startTime":"2026-11-11 07:00:00","endTime":"2026-11-11 12:00:00"}
             ]}
        """.trimIndent()
        val read = ShiftParse.read(json, zone = zone)!!
        assertEquals(listOf("e1"), read.mine.map { it.id })
        assertEquals(at(11, 12), read.mine[0].start)
        assertEquals("Turno · Chat", read.mine[0].title)
        assertTrue("u1" in read.myIds)
    }

    @Test
    fun gruppoPerPersonaEOreSeparate() {
        val json = """
            [{"employee":{"first_name":"Antonio","last_name":"Nesca"},
              "shifts":[{"date":"2026-11-12","from":"22:00","to":"06:00"}]},
             {"employee":{"first_name":"Luca","last_name":"Verdi"},
              "shifts":[{"date":"2026-11-12","from":"07:00","to":"12:00"}]}]
        """.trimIndent()
        val read = ShiftParse.read(json, zone = zone)!!
        assertEquals(1, read.mine.size)
        // Turno di notte: finisce il giorno dopo.
        assertEquals(at(12, 22), read.mine[0].start)
        assertEquals(at(13, 6), read.mine[0].end)
    }

    @Test
    fun idGiaNotiEdEpoch() {
        val json = """{"items":[{"_id":"s9","userId":"42","startsAt":${at(14, 7) / 1000},"endsAt":${at(14, 12) / 1000}}]}"""
        assertEquals(listOf("s9"), ShiftParse.read(json, known = setOf("42"), zone = zone)!!.mine.map { it.id })
        assertTrue(ShiftParse.read(json, zone = zone)!!.mine.isEmpty())
    }

    @Test
    fun utenteCollegatoInCimaNonPrendeTuttiITurni() {
        val json = """
            {"currentUser":"Antonio Nesca","shifts":[
              {"id":"x","start":"2026-11-10T07:00:00Z","end":"2026-11-10T12:00:00Z","user":{"name":"Mario Rossi"}}]}
        """.trimIndent()
        assertTrue(ShiftParse.read(json, zone = zone)!!.mine.isEmpty())
    }

    @Test
    fun nienteTurniNienteLettura() {
        assertNull(ShiftParse.read("""{"messages":[{"from":"a","to":"b"}]}""", zone = zone))
        assertNull(ShiftParse.read("<html>", zone = zone))
    }

    @Test
    fun turnoToltoSparisceFuoriPeriodoResta() {
        val old = listOf(
            Shift("a", at(10, 7), at(10, 12)),
            Shift("b", at(11, 7), at(11, 12)),
            Shift("c", at(20, 7), at(20, 12)),
        )
        val json = """[{"id":"a","start":"2026-11-10T07:00:00+01:00","end":"2026-11-10T12:00:00+01:00","name":"Antonio Nesca"},
                       {"id":"z","start":"2026-11-11T12:00:00+01:00","end":"2026-11-11T17:00:00+01:00","name":"Mario Rossi"}]"""
        val merged = ShiftParse.merge(old, ShiftParse.read(json, zone = zone)!!)
        assertEquals(listOf("a", "c"), merged.map { it.id })
        // Solo aggiunte: non toglie niente.
        assertEquals(listOf("a", "b", "c"), ShiftParse.merge(old, ShiftParse.read(json, zone = zone)!!.additive()).map { it.id })
    }

    @Test
    fun periodoDallIndirizzoAncheSenzaTurni() {
        val read = ShiftParse.read("""{"shifts":[]}""", zone = zone, url = "https://api.creatorspro.com/shifts?start=2026-11-09&end=2026-11-16")!!
        assertEquals(at(9, 0), read.from)
        assertEquals(at(16, 0), read.to)
        assertNull(ShiftParse.read("""{"items":[]}""", zone = zone, url = "https://api.creatorspro.com/messages?from=2026-11-09&to=2026-11-16"))
    }

    @Test
    fun nomeSenzaAccentiEInvertito() {
        assertTrue(ShiftParse.isMe("NESCA Antonio"))
        assertTrue(ShiftParse.isMe("antonio.nesca@gmail.com"))
        assertTrue(!ShiftParse.isMe("Antonio Rossi"))
    }

    @Test
    fun salvataggioERiga() {
        val list = listOf(Shift("a", 1_000, 2_000, "Turno · Chat"))
        assertEquals(list, ShiftParse.fromJson(ShiftParse.toJson(list)))
        assertEquals("1 turno in arrivo", shiftsLine(list, now = 0))
        assertEquals("Nessun turno in arrivo", shiftsLine(list, now = 5_000))
        assertTrue(list[0].eventId < -1)
    }
}
