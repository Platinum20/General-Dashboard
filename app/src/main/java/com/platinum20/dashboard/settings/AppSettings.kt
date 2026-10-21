package com.platinum20.dashboard.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme(val label: String, val description: String) {
    DYNAMIC("Material You", "I colori seguono lo sfondo del telefono"),
    SYSTEM("Sistema", "Palette classica, chiaro o scuro come il telefono"),
    AMOLED("AMOLED", "Nero puro, ideale per gli schermi OLED"),
}

/** Le card della Dashboard, nell'ordine predefinito. */
enum class HomeCard(val label: String, val description: String) {
    WEATHER("Meteo", "Il tempo dove sei, tocca per il dettaglio"),
    CALENDAR("Agenda di oggi", "Gli eventi di oggi dal calendario del telefono"),
    TASKS("Da fare", "I promemoria ancora aperti"),
    RECAP("Il punto del giorno", "I temi principali delle notizie"),
    MODULES("Moduli", "Scorciatoie alle sezioni dell'app"),
}

data class HomeLayout(val order: List<HomeCard>, val hidden: Set<HomeCard>) {
    val visible: List<HomeCard> get() = order.filter { it !in hidden }
}

/** Preferenze dell'app, salvate sul telefono e osservabili dalla UI. */
object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_CARD_ORDER = "home_card_order"
    private const val KEY_CARD_HIDDEN = "home_card_hidden"

    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(AppTheme.DYNAMIC)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()
    private val _homeLayout = MutableStateFlow(HomeLayout(HomeCard.entries, emptySet()))
    val homeLayout: StateFlow<HomeLayout> = _homeLayout.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.DYNAMIC
        _homeLayout.value = readLayout()
    }

    private fun readLayout(): HomeLayout {
        fun parse(raw: String?) = raw.orEmpty().split(',')
            .mapNotNull { name -> HomeCard.entries.firstOrNull { it.name == name } }
        val saved = parse(prefs.getString(KEY_CARD_ORDER, null)).distinct()
        // Le card aggiunte in versioni successive finiscono nella loro posizione predefinita.
        val order = saved.toMutableList()
        HomeCard.entries.forEach { card ->
            if (card !in order) {
                val before = HomeCard.entries.take(card.ordinal).lastOrNull { it in order }
                order.add(if (before == null) 0 else order.indexOf(before) + 1, card)
            }
        }
        return HomeLayout(order, parse(prefs.getString(KEY_CARD_HIDDEN, null)).toSet())
    }

    private fun saveLayout(layout: HomeLayout) {
        _homeLayout.value = layout
        prefs.edit()
            .putString(KEY_CARD_ORDER, layout.order.joinToString(",") { it.name })
            .putString(KEY_CARD_HIDDEN, layout.hidden.joinToString(",") { it.name })
            .apply()
    }

    fun moveCard(card: HomeCard, delta: Int) {
        val layout = _homeLayout.value
        val order = layout.order.toMutableList()
        val from = order.indexOf(card)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return
        order.add(to, order.removeAt(from))
        saveLayout(layout.copy(order = order))
    }

    fun setCardVisible(card: HomeCard, visible: Boolean) {
        val layout = _homeLayout.value
        saveLayout(layout.copy(hidden = if (visible) layout.hidden - card else layout.hidden + card))
    }

    fun resetLayout() = saveLayout(HomeLayout(HomeCard.entries, emptySet()))

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }
}
