package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.sim.WindTreParse
import com.platinum20.dashboard.ui.screens.gbLabel
import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.ui.screens.meterText
import com.platinum20.dashboard.ui.screens.renewalLine
import com.platinum20.dashboard.ui.screens.renewalLabel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Il formato di WindTre non è pubblico: la lettura deve capire le forme più comuni. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WindTreParseTest {
    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun contatoriInMbConTipo() {
        val json = """
            {"data":{"lineInfo":{"offerName":"Easy Pay 150","residualCredit":"4,20","nextRenewalDate":"2026-11-15"},
             "counters":[
               {"type":"DATA","unit":"MB","residual":43520,"total":153600},
               {"type":"DATA","unit":"MB","residual":500,"total":1024,"description":"Opzione roaming"},
               {"type":"VOICE","unlimited":true},
               {"type":"SMS","residual":95,"total":100}
             ]}}
        """.trimIndent()
        val s = WindTreParse.fromJson(json, today)!!
        assertEquals(42.5, s.data!!.left, 0.01)
        assertEquals(150.0, s.data!!.total!!, 0.01)
        assertTrue(s.minutes!!.unlimited)
        assertEquals(95.0, s.sms!!.left, 0.0)
        assertEquals(4.20, s.credit!!, 0.001)
        assertEquals(LocalDate.of(2026, 11, 15), s.renewal)
        assertEquals("Easy Pay 150", s.offer)
        assertEquals(1.0, s.roaming!!.total!!, 0.01)
    }

    @Test
    fun usatoETotaleSenzaResiduo() {
        val s = WindTreParse.fromJson("""[{"name":"Giga","usedAmount":"10 GB","totalAmount":"100 GB"}]""", today)!!
        assertEquals(90.0, s.data!!.left, 0.01)
    }

    @Test
    fun rispostaSenzaContatoriNonDaNiente() {
        assertNull(WindTreParse.fromJson("""{"user":{"name":"Antonio","msisdn":"3331234567"}}""", today))
        assertNull(WindTreParse.fromJson("non è json", today))
    }

    @Test
    fun testoDellaPagina() {
        val text = "Il tuo traffico\n42,5 GB su 150 GB\nMinuti illimitati\n95 SMS su 100\nCredito residuo 4,20 €\nSi rinnova il 15/11"
        val s = WindTreParse.fromText(text, today)!!
        assertEquals(42.5, s.data!!.left, 0.01)
        assertTrue(s.minutes!!.unlimited)
        assertEquals(100.0, s.sms!!.total!!, 0.0)
        assertEquals(4.20, s.credit!!, 0.001)
        assertEquals(LocalDate.of(2026, 11, 15), s.renewal)
    }

    @Test
    fun statoSalvatoERiletto() {
        val s = WindTreParse.fromText("10 GB su 50 GB\nCredito 3,00 €", today)!!.copy(at = 123)
        assertEquals(s, SimStatus.fromJson(JSONObject(s.toJson().toString())))
    }

    @Test
    fun accessoMaiSalvato() {
        assertTrue(WindTreParse.isPrivate("https://apigw.windtre.it/api/v1/login/otp"))
    }

    @Test
    fun etichette() {
        assertEquals("42 GB", gbLabel(42.4))
        assertEquals("3,5 GB", gbLabel(3.5))
        assertEquals("512 MB", gbLabel(0.5))
        assertEquals("tra 13 giorni", renewalLabel(LocalDate.of(2026, 11, 15), today))
    }

    @Test
    fun configurazioneIgnorata() {
        assertTrue(WindTreParse.isConfiguration("https://apigw.windtre.it/api/v0/configuration/internationalization/it"))
        assertTrue(!WindTreParse.isConfiguration("https://apigw.windtre.it/api/v1/lines/residual-credit"))
    }

    @Test
    fun dizionarioDiTestiNonDaNumeri() {
        val o = JSONObject()
        for (i in 0 until 100) o.put("label.$i", "Ti restano $i GB su 100 GB, credito 5 euro")
        o.put("credit", 4.2)
        assertEquals(null, WindTreParse.fromJson(o.toString(), today))
    }

    @Test
    fun paginaDellOffertaVera() {
        val text = "La tua Offerta\nARRICCHISCI OFFERTA\nStato: Attivo\nGO 200 XXS\nNAZIONALE\n141,4\n/ 200 Giga\nMinuti illimitati\n50\n/ 50 SMS\n" +
            "ROAMING UE\n7,5\n/ 7,5 Giga\nRinnovo:\n07/11\nDettagli e Limitazioni"
        val s = WindTreParse.fromText(text, today)!!
        assertEquals(141.4, s.data!!.left, 0.01)
        assertEquals(200.0, s.data!!.total!!, 0.01)
        assertTrue(s.minutes!!.unlimited)
        assertEquals(50.0, s.sms!!.left, 0.0)
        assertEquals(7.5, s.roaming!!.total!!, 0.01)
        assertEquals(LocalDate.of(2026, 11, 7), s.renewal)
        assertEquals("GO 200 XXS", s.offer)
    }

    @Test
    fun righeDellaCard() {
        assertEquals("illimitati", meterText(Allowance(0.0, null, true), ""))
        assertEquals("50 di 50", meterText(Allowance(50.0, 50.0), ""))
        assertEquals("7,5 di 7,5 GB", meterText(Allowance(7.5, 7.5), "GB"))
        assertEquals("Rinnovo 7/11 · tra 5 giorni", renewalLine(LocalDate.of(2026, 11, 7), today))
    }
}
