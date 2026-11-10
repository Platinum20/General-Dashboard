package com.platinum20.dashboard.search

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le ultime ricerche di Cerca: si usa soprattutto per tornare a cose già viste, quindi sotto il
 * campo vuoto ci sono quelle. Restano solo sul telefono.
 */
object SearchRecents {
    const val MAX = 5
    private const val PREFS = "search_recents"
    private const val KEY = "list"
    private lateinit var prefs: SharedPreferences
    private val _list = MutableStateFlow<List<String>>(emptyList())
    val list: StateFlow<List<String>> = _list.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _list.value = prefs.getString(KEY, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    /** Si ricorda la ricerca uscendo da Cerca: le lettere a metà non contano. */
    fun keep(query: String) {
        if (!::prefs.isInitialized) return
        val next = add(_list.value, query)
        if (next == _list.value) return
        _list.value = next
        prefs.edit().putString(KEY, next.joinToString("\n")).apply()
    }

    fun forget(query: String) {
        if (!::prefs.isInitialized) return
        _list.value = _list.value.filterNot { it.equals(query, ignoreCase = true) }
        prefs.edit().putString(KEY, _list.value.joinToString("\n")).apply()
    }

    /** Pura: la più nuova in cima, senza doppioni (anche con maiuscole diverse), al massimo [MAX]. */
    fun add(list: List<String>, query: String): List<String> {
        val q = query.trim().replace('\n', ' ')
        if (q.length < 2) return list
        return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
    }
}
