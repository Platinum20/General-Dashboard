package com.platinum20.dashboard.update

import android.content.Context
import com.platinum20.dashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRelease(
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val apkUrl: String?,
    val apkSize: Long,
) {
    /** Versione senza la "v" iniziale del tag, es. "v1.2.0" -> "1.2.0". */
    val version: String get() = tag.removePrefix("v").removePrefix("V")
}

class UpdateRepository(private val context: Context) {

    private val repo = BuildConfig.GITHUB_REPO

    /** Tutte le release pubblicate, dalla più recente. */
    suspend fun fetchReleases(): List<GitHubRelease> = withContext(Dispatchers.IO) {
        val conn = open("https://api.github.com/repos/$repo/releases?per_page=50").apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            when (val code = conn.responseCode) {
                200 -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> throw IOException("Repository non raggiungibile. Controlla che sia pubblica.")
                403, 429 -> throw IOException("Troppe richieste a GitHub, riprova tra qualche minuto.")
                else -> throw IOException("Errore GitHub ($code).")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Scarica l'APK nella cache dell'app e restituisce il file. */
    suspend fun downloadApk(
        release: GitHubRelease,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw IOException("Questa release non contiene un APK.")
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "General-Dashboard-${release.version}.apk")

        val conn = open(url).apply { setRequestProperty("Accept", "application/octet-stream") }
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("Download non riuscito (${conn.responseCode}).")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        target
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "General-Dashboard/${BuildConfig.VERSION_NAME}")
        }

    private fun parse(json: String): List<GitHubRelease> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) return@mapNotNull null

            val assets = obj.optJSONArray("assets") ?: JSONArray()
            val apk = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }

            val tag = obj.optString("tag_name")
            GitHubRelease(
                tag = tag,
                title = obj.optString("name").ifBlank { tag },
                notes = obj.optString("body"),
                publishedAt = obj.optString("published_at"),
                apkUrl = apk?.optString("browser_download_url"),
                apkSize = apk?.optLong("size") ?: 0L,
            )
        }
    }
}

/** Confronta due versioni "1.2.10" numero per numero. */
fun compareVersions(a: String, b: String): Int {
    val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}
