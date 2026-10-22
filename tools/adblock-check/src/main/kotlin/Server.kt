package com.platinum20.dashboard.adblock.check

import com.platinum20.dashboard.adblock.FilterEngine
import com.platinum20.dashboard.adblock.Json
import com.platinum20.dashboard.adblock.Request
import com.platinum20.dashboard.adblock.RequestType
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Il motore dei filtri come piccolo server locale, per il controllo con Chromium:
 *   GET /match?url=...&page=...&type=script   -> BLOCK, ALLOW o NONE
 *   GET /page?url=...                          -> i filtri della pagina (lo stesso JSON che riceve lo script nell'app)
 *   POST /generic?host=...  (chiavi una per riga) -> selettori generici
 * Uso: gradle run --args="<cartella assets/adblock> <porta>"
 */
fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "../../app/src/main/assets/adblock" })
    val port = args.getOrElse(1) { "8790" }.toInt()
    val builder = FilterEngine.Builder()
    File(dir, "lists").listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }.forEach {
        builder.addList(it.readText(), trusted = it.name.startsWith("ublock"))
        println("Lista ${it.name}")
    }
    builder.addList(File(dir, "builtin.txt").readText(), trusted = true)
    val engine = builder.build()
    println("Regole: ${engine.ruleCount}")

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.executor = Executors.newFixedThreadPool(8)
    server.createContext("/match") { ex ->
        val q = query(ex)
        val type = RequestType.entries.firstOrNull { it.name.equals(q["type"], ignoreCase = true) } ?: RequestType.UNKNOWN
        reply(ex, engine.match(q["url"].orEmpty(), q["page"].orEmpty(), type).name)
    }
    server.createContext("/page") { ex ->
        val url = query(ex)["url"].orEmpty()
        reply(ex, Json.page(engine.pageFilters(url, Request.hostOf(url))))
    }
    server.createContext("/generic") { ex ->
        val host = query(ex)["host"].orEmpty()
        val keys = ex.requestBody.bufferedReader().readText().split('\n').filter { it.isNotEmpty() }
        reply(ex, Json.strings(engine.genericSelectors(host, keys)))
    }
    server.createContext("/ready") { ex -> reply(ex, "ok") }
    server.start()
    println("In ascolto su $port")
}

private fun query(ex: HttpExchange): Map<String, String> =
    ex.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

private fun reply(ex: HttpExchange, body: String) {
    val bytes = body.toByteArray()
    ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    ex.sendResponseHeaders(200, bytes.size.toLong())
    ex.responseBody.use { it.write(bytes) }
}
