package com.platinum20.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.security.Vault
import com.platinum20.dashboard.update.UpdateTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SecurityTest {
    private val repo = "Platinum20/General-Dashboard"

    @Test fun soloLeReleaseDelRepository() {
        assertTrue(UpdateTrust.trustedUrl("https://github.com/$repo/releases/download/v1.2.0/General-Dashboard-1.2.0.apk", repo))
        assertTrue(UpdateTrust.trustedUrl("https://github.com/platinum20/general-dashboard/releases/download/v1/a.apk", repo))
        assertFalse(UpdateTrust.trustedUrl("http://github.com/$repo/releases/download/v1/a.apk", repo))
        assertFalse(UpdateTrust.trustedUrl("https://github.com/altro/General-Dashboard/releases/download/v1/a.apk", repo))
        assertFalse(UpdateTrust.trustedUrl("https://github.com.evil.it/$repo/releases/download/v1/a.apk", repo))
        assertFalse(UpdateTrust.trustedUrl("https://github.com/$repo/releases/download/../../x/a.apk", repo))
    }

    @Test fun firmaDiversaScartata() {
        val mine = setOf("aa")
        assertNull(UpdateTrust.problem("p", mine, "p", setOf("aa")))
        assertNotNull(UpdateTrust.problem("p", mine, "p", setOf("bb")))
        assertNotNull(UpdateTrust.problem("p", mine, "altro", setOf("aa")))
        // Firma illeggibile: decide Android, non blocchiamo.
        assertNull(UpdateTrust.problem("p", mine, null, emptySet()))
        assertNull(UpdateTrust.problem("p", emptySet(), "p", setOf("bb")))
    }

    @Test fun valoriVecchiInChiaroSiLeggonoAncora() {
        assertEquals("BQabc", Vault.open("BQabc"))
        assertNull(Vault.open(null))
        // Cifrato ma illeggibile (chiave persa): niente valore, si rifà l'accesso.
        assertNull(Vault.open(Vault.PREFIX + "rotto"))
    }

    @Test fun letturaEScritturaDallePreferenze() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("vault_test", Context.MODE_PRIVATE)
        prefs.edit().putString("old", "token-vecchio").apply()
        assertEquals("token-vecchio", Vault.read(prefs, "old"))
        Vault.put(prefs.edit(), "new", "segreto").apply()
        assertEquals("segreto", Vault.read(prefs, "new"))
    }
}
