package com.platinum20.dashboard.notes

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Le versioni precedenti di una nota (1.588): ogni volta che si chiude l'editor dopo aver cambiato il testo, il testo di
 * prima resta qui (al massimo [MAX] per nota). "Altro" → "Versioni precedenti" le mostra e una si rimette con Annulla.
 * Un file a parte: le note restano leggere.
 */
object NoteVersions {
    data class Version(val at: Long, val text: String)

    const val MAX = 15
    private const val FILE = "note-versions.json"
    private var file: File? = null
    private var all: MutableMap<Long, List<Version>> = mutableMapOf()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, FILE)
        file = f
        all = runCatching { parse(f.readText()) }.getOrDefault(mutableMapOf())
    }

    /** Le versioni di [id], dalla più recente. */
    fun of(id: Long): List<Version> = all[id].orEmpty()

    /** Tiene [before] come versione di [id], se è diverso da quello che c'è già in cima. */
    fun keep(id: Long, before: String, at: Long = System.currentTimeMillis()) {
        val next = add(of(id), before, at)
        if (next == of(id)) return
        all[id] = next
        save()
    }

    fun forget(id: Long) {
        if (all.remove(id) != null) save()
    }

    /** Pura: la lista con [text] in cima; niente doppioni, niente testi vuoti, al massimo [MAX]. */
    fun add(list: List<Version>, text: String, at: Long): List<Version> {
        if (text.isBlank() || list.firstOrNull()?.text == text) return list
        return (listOf(Version(at, text)) + list).take(MAX)
    }

    private fun save() {
        val f = file ?: return
        runCatching { f.writeText(write(all)) }
    }

    internal fun write(map: Map<Long, List<Version>>): String = JSONObject().apply {
        map.forEach { (id, list) ->
            put(id.toString(), JSONArray().apply { list.forEach { put(JSONObject().put("at", it.at).put("text", it.text)) } })
        }
    }.toString()

    internal fun parse(raw: String): MutableMap<Long, List<Version>> {
        val json = JSONObject(raw)
        val out = mutableMapOf<Long, List<Version>>()
        json.keys().forEach { k ->
            val arr = json.getJSONArray(k)
            out[k.toLong()] = (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Version(it.getLong("at"), it.getString("text")) } }
        }
        return out
    }
}
