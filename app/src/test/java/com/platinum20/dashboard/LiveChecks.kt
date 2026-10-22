package com.platinum20.dashboard

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.radio.RadioApi
import com.platinum20.dashboard.net.Http
import com.platinum20.dashboard.news.NewsRecap
import com.platinum20.dashboard.news.NewsRepository
import com.platinum20.dashboard.news.NewsSources
import com.platinum20.dashboard.news.reader.ArticleExtractor
import com.platinum20.dashboard.update.UpdateRepository
import com.platinum20.dashboard.weather.ForecastParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Controlli dal vivo con la rete vera (feed delle notizie, lettore, meteo, aggiornamenti, radio),
 * con lo stesso codice dell'app. Girano solo con: gradle testDebugUnitTest -Plive=true
 * Scrivono il resoconto in build/live/funzioni.md.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LiveChecks {

    private val report = StringBuilder("# Verifica dal vivo delle funzioni\n\n")
    private val problems = mutableListOf<String>()

    private fun line(ok: Boolean, text: String) {
        report.append(if (ok) "- ✅ " else "- ❌ ").append(text).append('\n')
        if (!ok) problems += text
    }

    @Test
    fun funzioni() = runBlocking {
        assumeTrue(System.getProperty("live") == "true")

        report.append("## Notizie\n\n")
        val result = runCatching { NewsRepository().fetchAll() }
        val news = result.getOrNull()
        line(news != null && news.items.isNotEmpty(), "Feed: ${news?.items?.size ?: 0} notizie in tutto" + (result.exceptionOrNull()?.let { " ($it)" } ?: ""))
        for (source in NewsSources.all) {
            val items = news?.items.orEmpty().filter { it.source.id == source.id }
            val failure = news?.failures?.firstOrNull { it.source.id == source.id }
            val dated = items.count { it.publishedAt != null }
            val withImage = items.count { it.imageUrl != null }
            line(
                items.isNotEmpty() && dated == items.size,
                "${source.name}: ${items.size} notizie, $dated con data, $withImage con immagine" + (failure?.let { " — errore: ${it.reason}" } ?: ""),
            )
        }
        val themes = news?.items?.let { NewsRecap.build(it) }.orEmpty()
        line(themes.isNotEmpty(), "Recap: ${themes.size} temi del giorno" + themes.take(3).joinToString("") { "\n  - ${it.headline.title}" })

        report.append("\n## Lettore (modalità lettura)\n\n")
        for (source in NewsSources.all) {
            val item = news?.items?.firstOrNull { it.source.id == source.id } ?: continue
            val article = runCatching { ArticleExtractor.extractBest(ArticleExtractor.download(item.link)) }
            val length = article.getOrNull()?.textLength ?: 0
            line(
                length >= ArticleExtractor.GOOD_TEXT_LENGTH,
                "${source.name}: $length caratteri da ${item.link}" +
                    (article.exceptionOrNull()?.let { " — errore: $it" } ?: if (length < ArticleExtractor.GOOD_TEXT_LENGTH) " (l'app proverà il caricamento nel browser invisibile)" else ""),
            )
        }

        report.append("\n## Meteo\n\n")
        val weather = runCatching {
            val raw = Http.getText(ForecastParser.url(41.9, 12.5))
            val air = runCatching { Http.getText(ForecastParser.airUrl(41.9, 12.5)) }.getOrNull()
            ForecastParser.parse(raw, "Roma", 41.9, 12.5, System.currentTimeMillis(), air)
        }
        weather.getOrNull().let { f ->
            line(
                f != null && f.hourly.size >= 12 && f.daily.size >= 5 && !f.current.temperature.isNaN(),
                if (f != null) "Roma: ${f.current.temperature}°, ${f.hourly.size} ore, ${f.daily.size} giorni, " +
                    "nowcast ${f.nowcast.size} punti, aria ${f.air?.label ?: "non disponibile"}, oggi ${f.today?.date}"
                else "Open-Meteo: ${weather.exceptionOrNull()}",
            )
        }
        val geo = runCatching { Http.getText("https://geocoding-api.open-meteo.com/v1/search?name=Milano&count=1&language=it&format=json") }
        line(geo.getOrNull()?.contains("Milano") == true, "Ricerca città: " + (geo.exceptionOrNull()?.toString() ?: "ok"))

        report.append("\n## Aggiornamenti dell'app\n\n")
        val releases = runCatching { UpdateRepository(ApplicationProvider.getApplicationContext()).fetchReleases() }
        releases.getOrNull().let { list ->
            val latest = list?.firstOrNull()
            line(latest?.apkUrl != null, "GitHub: ${list?.size ?: 0} versioni, ultima ${latest?.version} con APK ${latest?.apkUrl != null}" +
                (releases.exceptionOrNull()?.let { " — $it" } ?: ""))
            latest?.apkUrl?.let { url ->
                val code = runCatching { (URL(url).openConnection() as HttpURLConnection).run { requestMethod = "HEAD"; responseCode } }.getOrNull()
                line(code in 200..399, "Download dell'APK raggiungibile (HTTP $code)")
            }
        }

        report.append("\n## Radio\n\n")
        val stations = runCatching { RadioApi.popular() }
        line(stations.getOrNull().orEmpty().size >= 10, "Stazioni italiane: ${stations.getOrNull()?.size ?: 0}" +
            (stations.exceptionOrNull()?.let { " — $it" } ?: ""))
        val stream = stations.getOrNull()?.firstOrNull()
        if (stream != null) {
            val code = runCatching {
                (URL(stream.streamUrl).openConnection() as HttpURLConnection).run {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    responseCode.also { disconnect() }
                }
            }.getOrNull()
            line(code in 200..399, "Flusso di ${stream.name} raggiungibile (HTTP $code)")
        }
        val search = runCatching { RadioApi.search("radio deejay") }
        line(search.getOrNull().orEmpty().isNotEmpty(), "Ricerca \"radio deejay\": ${search.getOrNull()?.size ?: 0} risultati")

        val dir = File(System.getProperty("live.dir") ?: "build/live").apply { mkdirs() }
        File(dir, "funzioni.md").writeText(report.toString())
        println(report)
        assertTrue("Problemi: $problems", problems.isEmpty())
    }
}
