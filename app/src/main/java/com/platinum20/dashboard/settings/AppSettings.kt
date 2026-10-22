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
    WEATHER("Adesso", "Il meteo e le poche cose da sapere in questo momento: prossimo impegno, pioggia, scadenze"),
    MODULES("Scorciatoie", "Meteo, punto del giorno, video, YouTube e Spotify a un tocco"),
    CALENDAR("Agenda", "Gli impegni di oggi; la sera, finiti quelli, quelli di domani. Sparisce se non ce ne sono"),
    TASKS("Da fare", "I promemoria ancora aperti"),
    COUNTDOWN("In arrivo", "Compleanni, viaggi e scadenze della prossima settimana. Sparisce se non ce ne sono"),
    RECAP("Il punto del giorno", "I temi principali delle notizie"),
}

data class HomeLayout(val order: List<HomeCard>, val hidden: Set<HomeCard>) {
    val visible: List<HomeCard> get() = order.filter { it !in hidden }

    companion object {
        /** Le scorciatoie ripetono la barra in basso e le card: si accendono da Personalizza. */
        val DEFAULT = HomeLayout(HomeCard.entries, setOf(HomeCard.MODULES))
    }
}

data class MorningBrief(val enabled: Boolean = false, val hour: Int = 7, val minute: Int = 30) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

/** Preferenze dell'app, salvate sul telefono e osservabili dalla UI. */
object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_CARD_ORDER = "home_card_order"
    private const val KEY_CARD_HIDDEN = "home_card_hidden"
    private const val KEY_LAYOUT_VERSION = "home_layout_version"
    private const val LAYOUT_VERSION = 2
    private const val KEY_MORNING_ENABLED = "morning_enabled"
    private const val KEY_MORNING_HOUR = "morning_hour"
    private const val KEY_MORNING_MINUTE = "morning_minute"
    private const val KEY_AD_BLOCK = "ad_block"
    private const val KEY_UPDATE_NOTIFY = "update_notify"

    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(AppTheme.DYNAMIC)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()
    private val _homeLayout = MutableStateFlow(HomeLayout.DEFAULT)
    val homeLayout: StateFlow<HomeLayout> = _homeLayout.asStateFlow()
    private val _morning = MutableStateFlow(MorningBrief())
    val morning: StateFlow<MorningBrief> = _morning.asStateFlow()
    private val _adBlock = MutableStateFlow(true)
    /** Blocco pubblicità nei player di YouTube e Spotify. */
    val adBlock: StateFlow<Boolean> = _adBlock.asStateFlow()
    private val _updateNotify = MutableStateFlow(true)
    /** Notifica quando su GitHub esce una nuova versione dell'app. */
    val updateNotify: StateFlow<Boolean> = _updateNotify.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.DYNAMIC
        _homeLayout.value = readLayout()
        if (prefs.getInt(KEY_LAYOUT_VERSION, 1) < LAYOUT_VERSION) {
            // 1.19: Oggi diventa "cosa serve adesso", le scorciatoie si nascondono una volta sola.
            if (prefs.contains(KEY_CARD_ORDER)) saveLayout(_homeLayout.value.let { it.copy(hidden = it.hidden + HomeCard.MODULES) })
            prefs.edit().putInt(KEY_LAYOUT_VERSION, LAYOUT_VERSION).apply()
        }
        _morning.value = MorningBrief(
            enabled = prefs.getBoolean(KEY_MORNING_ENABLED, false),
            hour = prefs.getInt(KEY_MORNING_HOUR, 7),
            minute = prefs.getInt(KEY_MORNING_MINUTE, 30),
        )
        _adBlock.value = prefs.getBoolean(KEY_AD_BLOCK, true)
        _updateNotify.value = prefs.getBoolean(KEY_UPDATE_NOTIFY, true)
    }

    fun setMorning(brief: MorningBrief) {
        _morning.value = brief
        prefs.edit()
            .putBoolean(KEY_MORNING_ENABLED, brief.enabled)
            .putInt(KEY_MORNING_HOUR, brief.hour)
            .putInt(KEY_MORNING_MINUTE, brief.minute)
            .apply()
    }

    fun setAdBlock(enabled: Boolean) {
        _adBlock.value = enabled
        prefs.edit().putBoolean(KEY_AD_BLOCK, enabled).apply()
    }

    fun setUpdateNotify(enabled: Boolean) {
        _updateNotify.value = enabled
        prefs.edit().putBoolean(KEY_UPDATE_NOTIFY, enabled).apply()
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
        val hidden = prefs.getString(KEY_CARD_HIDDEN, null)?.let { parse(it).toSet() } ?: HomeLayout.DEFAULT.hidden
        return HomeLayout(order, hidden)
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

    fun resetLayout() = saveLayout(HomeLayout.DEFAULT)

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }
}
