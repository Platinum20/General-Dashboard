package com.platinum20.dashboard.adblock

import com.platinum20.dashboard.adblock.FilterEngine.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class FilterEngineTest {

    private fun engine(vararg lines: String, trusted: Boolean = true) =
        FilterEngine.Builder().addList(lines.joinToString("\n"), trusted).build()

    private fun FilterEngine.verdict(url: String, page: String = "https://www.sito.it/", type: RequestType = RequestType.UNKNOWN) =
        match(url, page, type)

    @Test
    fun hostAnchor() {
        val e = engine("||ads.example.com^")
        assertEquals(Verdict.BLOCK, e.verdict("https://ads.example.com/banner.js"))
        assertEquals(Verdict.BLOCK, e.verdict("https://x.ads.example.com/"))
        assertEquals(Verdict.NONE, e.verdict("https://ads.example.com.evil.org/"))
        assertEquals(Verdict.NONE, e.verdict("https://badads.example.com/"))
        assertEquals(Verdict.NONE, e.verdict("https://example.com/ads.example.com"))
    }

    @Test
    fun wildcardsAndSeparators() {
        val e = engine("/banner/*/img^", "|https://track.", "swf|", "&adtype=")
        assertEquals(Verdict.BLOCK, e.verdict("https://x.it/banner/foo/img?x=1"))
        assertEquals(Verdict.BLOCK, e.verdict("https://x.it/banner/foo/img"))
        assertEquals(Verdict.NONE, e.verdict("https://x.it/banner/foo/imgs"))
        assertEquals(Verdict.BLOCK, e.verdict("https://track.foo.it/p"))
        assertEquals(Verdict.NONE, e.verdict("https://foo.it/?u=https://track.x"))
        assertEquals(Verdict.BLOCK, e.verdict("https://foo.it/a.swf"))
        assertEquals(Verdict.NONE, e.verdict("https://foo.it/a.swf?x"))
        assertEquals(Verdict.BLOCK, e.verdict("https://foo.it/q?a=1&adtype=2"))
    }

    @Test
    fun partyTypesAndDomains() {
        val e = engine(
            "||cdn.net^\$third-party",
            "/pixel.\$image",
            "||widget.com/w.js\$script,domain=a.it|~b.a.it",
            "||nope.com^\$~script",
        )
        assertEquals(Verdict.BLOCK, e.verdict("https://cdn.net/x.js"))
        assertEquals(Verdict.NONE, e.verdict("https://cdn.net/x.js", page = "https://www.cdn.net/"))
        assertEquals(Verdict.BLOCK, e.verdict("https://x.it/pixel.gif", type = RequestType.IMAGE))
        assertEquals(Verdict.NONE, e.verdict("https://x.it/pixel.gif", type = RequestType.SCRIPT))
        assertEquals(Verdict.BLOCK, e.verdict("https://x.it/pixel.gif", type = RequestType.UNKNOWN))
        assertEquals(Verdict.BLOCK, e.verdict("https://widget.com/w.js", page = "https://www.a.it/", type = RequestType.SCRIPT))
        assertEquals(Verdict.NONE, e.verdict("https://widget.com/w.js", page = "https://b.a.it/", type = RequestType.SCRIPT))
        assertEquals(Verdict.NONE, e.verdict("https://widget.com/w.js", page = "https://c.it/", type = RequestType.SCRIPT))
        assertEquals(Verdict.NONE, e.verdict("https://nope.com/a.js", type = RequestType.SCRIPT))
        assertEquals(Verdict.BLOCK, e.verdict("https://nope.com/a.png", type = RequestType.IMAGE))
    }

    @Test
    fun exceptionsImportantAndBadfilter() {
        val e = engine(
            "||ads.com^",
            "@@||ads.com/ok.js",
            "||track.com^",
            "||track.com^\$important",
            "@@||track.com^",
            "||broken.com^",
            "||broken.com^\$badfilter",
            "/regex-ad[0-9]+/",
        )
        assertEquals(Verdict.BLOCK, e.verdict("https://ads.com/x.js"))
        assertEquals(Verdict.ALLOW, e.verdict("https://ads.com/ok.js"))
        assertEquals(Verdict.BLOCK, e.verdict("https://track.com/p"))
        assertEquals(Verdict.NONE, e.verdict("https://broken.com/p"))
        assertEquals(Verdict.BLOCK, e.verdict("https://x.com/regex-ad12"))
    }

    @Test
    fun documentException() {
        val e = engine("||ads.com^", "@@||trusted.it^\$document", "@@||nocosmetic.it^\$elemhide", "##.ad", "nocosmetic.it##.box")
        assertEquals(Verdict.NONE, e.verdict("https://ads.com/x.js", page = "https://www.trusted.it/a"))
        assertEquals(Verdict.BLOCK, e.verdict("https://ads.com/x.js", page = "https://www.other.it/a"))
        val p = e.pageFilters("https://nocosmetic.it/", "nocosmetic.it")
        assertTrue(p.hide.isEmpty())
        assertFalse(p.generic)
    }

    @Test
    fun preprocessor() {
        val e = engine(
            "!#if env_mobile", "||mobile.com^", "!#else", "||desktop.com^", "!#endif",
            "!#if !ext_ublock", "||never.com^", "!#endif",
            "!#if env_chromium && !env_firefox", "||chrome.com^", "!#endif",
        )
        assertEquals(Verdict.BLOCK, e.verdict("https://mobile.com/"))
        assertEquals(Verdict.NONE, e.verdict("https://desktop.com/"))
        assertEquals(Verdict.NONE, e.verdict("https://never.com/"))
        assertEquals(Verdict.BLOCK, e.verdict("https://chrome.com/"))
    }

    @Test
    fun cosmetic() {
        val e = engine(
            "##.ad-banner",
            "###sponsor > div",
            "##a[href*=\"doubleclick\"]",
            "~eccezione.it##.adsbox",
            "##.adsbox",
            "sito.it,~shop.sito.it##.box-adv",
            "sito.it#@#.ad-banner",
            "sito.it##.titolo:style(color: red !important)",
            "sito.it##div.post:has-text(/Sponsorizzato/i)",
            "sito.it##+js(set, adsEnabled, false)",
            "sito.it##+js(json-prune, playerResponse.adPlacements playerResponse.playerAds)",
            "altro.it#@#+js()",
            "altro.it##+js(nostif, ads)",
            "google.*##.g-ad",
            "#@#.globally-disabled",
            "##.globally-disabled",
        )
        val p = e.pageFilters("https://www.sito.it/x", "www.sito.it")
        assertEquals(listOf(".box-adv"), p.hide)
        assertEquals(listOf(".titolo" to "color: red !important"), p.styles)
        assertEquals(listOf("div.post:has-text(/Sponsorizzato/i)"), p.procedural)
        assertEquals(
            listOf(listOf("set", "adsEnabled", "false"), listOf("json-prune", "playerResponse.adPlacements playerResponse.playerAds")),
            p.scriptlets,
        )
        assertEquals(listOf("a[href*=\"doubleclick\"]"), p.highlyGeneric)
        assertEquals(emptyList<String>(), e.pageFilters("https://shop.sito.it/", "shop.sito.it").hide)
        assertTrue(e.pageFilters("https://altro.it/", "altro.it").scriptlets.isEmpty())
        assertEquals(listOf(".g-ad"), e.pageFilters("https://www.google.it/", "www.google.it").hide)

        // Generici per classe e id: sito.it ha l'eccezione per .ad-banner.
        assertEquals(listOf(".ad-banner"), e.genericSelectors("www.altro.it", listOf(".ad-banner", ".x")))
        assertEquals(emptyList<String>(), e.genericSelectors("www.sito.it", listOf(".ad-banner")))
        assertEquals(listOf("#sponsor > div"), e.genericSelectors("x.it", listOf("#sponsor")))
        assertEquals(listOf(".adsbox", ".adsbox"), e.genericSelectors("x.it", listOf(".adsbox")))
        assertEquals(emptyList<String>(), e.genericSelectors("eccezione.it", listOf(".adsbox")))
        assertEquals(emptyList<String>(), e.genericSelectors("x.it", listOf(".globally-disabled")))
    }

    @Test
    fun scriptletArguments() {
        assertEquals(listOf("set", "a.b", "undefined"), Cosmetic.parseScriptletArgs("set, a.b, undefined"))
        assertEquals(listOf("trusted-rpfr", "\"adPlacements\"", "\"no_ads\"", "player?"),
            Cosmetic.parseScriptletArgs("trusted-rpfr, '\"adPlacements\"', '\"no_ads\"', player?"))
        assertEquals(listOf("nostif", "()\\,a".replace("\\", ""), "5000"), Cosmetic.parseScriptletArgs("nostif, ()\\,a, 5000"))
        assertEquals(listOf("x", ""), Cosmetic.parseScriptletArgs("x, "))
        val untrusted = engine("a.it##+js(trusted-set, x, 1)", "a.it##+js(set, y, 1)", trusted = false)
        assertEquals(listOf(listOf("set", "y", "1")), untrusted.pageFilters("https://a.it/", "a.it").scriptlets)
    }

    @Test
    fun regexTokens() {
        assertEquals("locopelis", NetworkFilter.regexToken("""^https:\/\/www\.locopelis\.com\/[a-z0-9]{6}\.js$"""))
        assertEquals(null, NetworkFilter.regexToken("""^https?:\/\/[a-z]{8,15}\.xyz\/$""").takeIf { it != "xyz" })
        assertEquals(null, NetworkFilter.regexToken("""ads|banner"""))
        assertEquals(null, NetworkFilter.regexToken("""ad[0-9]+"""))
        val e = engine("""/^https:\/\/www\.locopelis\.com\/[a-z0-9]{6}\.js$/""")
        assertEquals(Verdict.BLOCK, e.verdict("https://www.locopelis.com/abc123.js"))
        assertEquals(Verdict.NONE, e.verdict("https://www.locopelis.com/abc1234.js"))
    }

    @Test
    fun domains() {
        assertEquals("example.co.uk", Domains.registrable("news.example.co.uk"))
        assertEquals("youtube.com", Domains.registrable("m.youtube.com"))
        assertTrue(Domains.isThirdParty("doubleclick.net", "m.youtube.com"))
        assertFalse(Domains.isThirdParty("i.ytimg.com".replace("ytimg", "youtube"), "m.youtube.com"))
        assertTrue(Domains.matches("www.google.co.uk", "google.*"))
        assertFalse(Domains.matches("notgoogle.it", "google.*"))
        assertTrue("google.*" in Domains.lookupKeys("www.google.it"))
    }

    @Test
    fun json() {
        val e = engine("a.it##.x[title=\"a\\\\b\"]")
        val json = Json.page(e.pageFilters("https://a.it/", "a.it"))
        assertTrue(json, json.contains("\".x[title=\\\"a\\\\\\\\b\\\"]\""))
    }

    /** Con le liste vere (scaricate da scripts/update_filter_lists.py): esempi noti e tempi. */
    @Test
    fun realLists() {
        val dir = File(System.getProperty("lists") ?: "src/main/assets/adblock").let { if (it.isDirectory) it else File("src/main/assets/adblock") }
        val lists = File(dir, "lists").listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
        assumeTrue("liste non scaricate", lists.isNotEmpty())
        val start = System.nanoTime()
        val builder = FilterEngine.Builder()
        lists.forEach { builder.addList(it.readText(), trusted = it.name.startsWith("ublock")) }
        File(dir, "builtin.txt").takeIf { it.exists() }?.let { builder.addList(it.readText(), trusted = true) }
        val e = builder.build()
        val buildMs = (System.nanoTime() - start) / 1_000_000
        println("Regole: ${e.ruleCount}, costruzione: $buildMs ms")
        println(e.stats())

        val page = "https://www.repubblica.it/cronaca/articolo"
        assertEquals(Verdict.BLOCK, e.match("https://securepubads.g.doubleclick.net/tag/js/gpt.js", page, RequestType.SCRIPT))
        assertEquals(Verdict.BLOCK, e.match("https://www.googletagmanager.com/gtag/js?id=G-1", page, RequestType.SCRIPT))
        assertEquals(Verdict.BLOCK, e.match("https://cdn.taboola.com/libtrc/x/loader.js", page, RequestType.SCRIPT))
        assertEquals(Verdict.BLOCK, e.match("https://www.google-analytics.com/analytics.js", page, RequestType.SCRIPT))
        assertEquals(Verdict.BLOCK, e.match("https://m.youtube.com/pagead/viewthroughconversion/1/", "https://m.youtube.com/watch?v=x", RequestType.IMAGE))
        assertEquals(Verdict.BLOCK, e.match("https://www.youtube.com/api/stats/ads?ver=2", "https://m.youtube.com/watch?v=x", RequestType.XHR))
        assertTrue(e.match("https://www.repubblica.it/static/main.css", page, RequestType.STYLESHEET) != Verdict.BLOCK)
        assertTrue(e.match("https://m.youtube.com/youtubei/v1/player?key=x", "https://m.youtube.com/watch?v=x", RequestType.XHR) != Verdict.BLOCK)
        assertTrue(e.match("https://rr3---sn-x.googlevideo.com/videoplayback?expire=1", "https://m.youtube.com/watch?v=x", RequestType.MEDIA) != Verdict.BLOCK)
        assertTrue(e.match("https://i.ytimg.com/vi/abc/hqdefault.jpg", "https://m.youtube.com/", RequestType.IMAGE) != Verdict.BLOCK)
        assertTrue(e.match("https://open.spotify.com/", "https://open.spotify.com/", RequestType.XHR) != Verdict.BLOCK)

        val yt = e.pageFilters("https://m.youtube.com/watch?v=x", "m.youtube.com")
        val names = yt.scriptlets.map { it[0] }
        println("YouTube: ${yt.hide.size} nascosti, ${yt.procedural.size} procedurali, scriptlet ${names.distinct()}")
        assertTrue(names.contains("set") || names.contains("set-constant"))
        assertTrue(names.contains("json-prune"))
        println("Generici sempre attivi: ${yt.highlyGeneric.size}, JSON pagina: ${Json.page(yt).length / 1024} KB")

        val urls = listOf(
            "https://www.repubblica.it/img/foto.jpg", "https://www.ilpost.it/wp-content/themes/x/main.js?ver=1.2",
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js", "https://static.xx.fbcdn.net/rsrc.php/v3/x.js",
            "https://www.ansa.it/sito/static/js/ansa.js", "https://ads.pubmatic.com/AdServer/js/pwt/123/pwt.js",
        )
        val t0 = System.nanoTime()
        repeat(20000) { i -> e.match(urls[i % urls.size] + "?n=$i", page, RequestType.UNKNOWN) }
        val perRequestUs = (System.nanoTime() - t0) / 1000 / 20000
        println("Tempo medio per richiesta: $perRequestUs µs")
        assertTrue(perRequestUs < 2000)
    }
}
