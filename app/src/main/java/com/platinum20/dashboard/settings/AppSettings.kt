package com.platinum20.dashboard.settings

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.news.voice.ReadingVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTheme(val label: String, val description: String) {
    CANVAS("Canvas", "Carta e inchiostro, chiaro o scuro col telefono"),
    DYNAMIC("Material You", "I colori seguono lo sfondo del telefono"),
    // Il nome resta AMOLED per le preferenze salvate: ora segue chiaro e scuro del telefono.
    AMOLED("Puro", "Bianco di giorno, nero AMOLED di sera"),
}

/**
 * Le card della Dashboard, nell'ordine predefinito: prima quello che serve subito, poi le cose
 * da sistemare, infine le letture. Le descrizioni stanno su una riga sola in Personalizza.
 */
enum class HomeCard(val label: String, val description: String) {
    // Il nome resta WEATHER per le preferenze salvate.
    WEATHER("Adesso", "Meteo e cose da sapere ora"),
    RESUME("Media", "Quello che suona o da riprendere"),
    // Il nome resta DAY per le preferenze salvate: dalla 1.186 è "Prossime cose" (oggi sta in Adesso).
    DAY("Prossime cose", "I prossimi 7 giorni, tutto insieme"),
    MONEY("Spese", "Il mese, il budget e gli addebiti"),
    // Il nome resta MAIL per le preferenze salvate: dalla 1.182 tiene anche "Trovati per te".
    MAIL("Dalla posta", "Cose da aggiungere e mail importanti"),
    WEEK("La tua settimana", "Domenica sera e lunedì: com'è andata"),
    RECAP("Il punto del giorno", "Le notizie principali"),
}

data class HomeLayout(val order: List<HomeCard>, val hidden: Set<HomeCard>) {
    val visible: List<HomeCard> get() = order.filter { it !in hidden }

    companion object {
        val DEFAULT = HomeLayout(HomeCard.entries, emptySet())

        /**
         * 2 = 1.182: via "Domani" (sta in Adesso), "Trovati per te" dentro "Dalla posta", ordine nuovo.
         * 3 = 1.186: "La giornata" diventa "Prossime cose" e torna visibile anche se era nascosta.
         */
        const val VERSION = 3

        /**
         * Legge ordine e card nascoste salvate. Da una versione vecchia l'ordine torna quello
         * predefinito e "Dalla posta" resta nascosta solo se lo erano sia Posta sia Trovati per te.
         */
        fun read(orderRaw: String?, hiddenRaw: String?, version: Int): HomeLayout {
            fun names(raw: String?) = raw.orEmpty().split(',').filter { it.isNotBlank() }
            fun parse(list: List<String>) = list.mapNotNull { name -> HomeCard.entries.firstOrNull { it.name == name } }
            val oldHidden = names(hiddenRaw).let { if (version < 3) it - "DAY" else it }
            if (version < 2) {
                val hidden = parse(oldHidden).toMutableSet()
                if (hiddenRaw != null && "MAIL" in oldHidden && "FOUND" !in oldHidden) hidden -= HomeCard.MAIL
                return HomeLayout(DEFAULT.order, hidden)
            }
            val order = parse(names(orderRaw)).distinct().toMutableList()
            // Le card aggiunte in versioni successive finiscono nella loro posizione predefinita.
            HomeCard.entries.forEach { card ->
                if (card !in order) {
                    val before = HomeCard.entries.take(card.ordinal).lastOrNull { it in order }
                    order.add(if (before == null) 0 else order.indexOf(before) + 1, card)
                }
            }
            return HomeLayout(order, parse(oldHidden).toSet())
        }
    }
}

data class MorningBrief(val enabled: Boolean = false, val hour: Int = 7, val minute: Int = 30) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

/** Preferenze dell'app, salvate sul telefono e osservabili dalla UI. */
object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_THEME = "theme"
    private const val KEY_THEME_VERSION = "theme_version"
    /** 2 = nuovo design 1.55: chi aveva Material You o Sistema passa una volta a Canvas. */
    private const val THEME_VERSION = 2
    private const val KEY_CARD_ORDER = "home_card_order"
    private const val KEY_CARD_HIDDEN = "home_card_hidden"
    private const val KEY_CARD_VERSION = "home_card_version"
    private const val KEY_MORNING_ENABLED = "morning_enabled"
    private const val KEY_MORNING_HOUR = "morning_hour"
    private const val KEY_MORNING_MINUTE = "morning_minute"
    private const val KEY_AD_BLOCK = "ad_block"
    private const val KEY_UPDATE_NOTIFY = "update_notify"
    private const val KEY_RAIN_ALERT = "rain_alert"
    private const val KEY_READING_VOICE = "reading_voice"
    private const val KEY_WIFI_ONLY = "wifi_only_downloads"

    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(AppTheme.CANVAS)
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
    private val _rainAlert = MutableStateFlow(false)
    /** Notifica quando sta per piovere (spenta di default). */
    val rainAlert: StateFlow<Boolean> = _rainAlert.asStateFlow()
    private val _readingVoice = MutableStateFlow(ReadingVoice.DEFAULT)
    /** La voce che legge notizie e punto del giorno. */
    val readingVoice: StateFlow<ReadingVoice> = _readingVoice.asStateFlow()
    private val _wifiOnlyDownloads = MutableStateFlow(false)
    /** Voci e aggiornamenti si scaricano solo col Wi-Fi (spento: scarica con qualunque rete). */
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        load()
    }

    /** Le impostazioni come stanno nel file, per il backup. */
    fun snapshot(): Map<String, Any?> = if (::prefs.isInitialized) prefs.all else emptyMap()

    /** Sostituisce le impostazioni con quelle di un backup e le rilegge subito. */
    fun restore(values: Map<String, Any?>) {
        if (!::prefs.isInitialized) return
        prefs.edit().clear().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
        load()
    }

    private fun load() {
        val savedTheme = prefs.getString(KEY_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
        // Il nuovo design si vede una volta per tutti; chi vuole torna a Material You dalle Impostazioni.
        _theme.value = if (prefs.getInt(KEY_THEME_VERSION, 1) < THEME_VERSION) {
            prefs.edit().putInt(KEY_THEME_VERSION, THEME_VERSION).apply()
            if (savedTheme == AppTheme.AMOLED) AppTheme.AMOLED else AppTheme.CANVAS.also { setTheme(it) }
        } else {
            savedTheme ?: AppTheme.CANVAS
        }
        _homeLayout.value = readLayout()
        _morning.value = MorningBrief(
            enabled = prefs.getBoolean(KEY_MORNING_ENABLED, false),
            hour = prefs.getInt(KEY_MORNING_HOUR, 7),
            minute = prefs.getInt(KEY_MORNING_MINUTE, 30),
        )
        _adBlock.value = prefs.getBoolean(KEY_AD_BLOCK, true)
        _updateNotify.value = prefs.getBoolean(KEY_UPDATE_NOTIFY, true)
        _rainAlert.value = prefs.getBoolean(KEY_RAIN_ALERT, false)
        _readingVoice.value = ReadingVoice.of(prefs.getString(KEY_READING_VOICE, null))
        _wifiOnlyDownloads.value = prefs.getBoolean(KEY_WIFI_ONLY, false)
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

    fun setWifiOnlyDownloads(enabled: Boolean) {
        _wifiOnlyDownloads.value = enabled
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
    }

    fun setReadingVoice(voice: ReadingVoice) {
        _readingVoice.value = voice
        prefs.edit().putString(KEY_READING_VOICE, voice.name).apply()
    }

    fun setRainAlert(enabled: Boolean) {
        _rainAlert.value = enabled
        prefs.edit().putBoolean(KEY_RAIN_ALERT, enabled).apply()
    }

    private fun readLayout(): HomeLayout {
        val version = prefs.getInt(KEY_CARD_VERSION, 1)
        val layout = HomeLayout.read(prefs.getString(KEY_CARD_ORDER, null), prefs.getString(KEY_CARD_HIDDEN, null), version)
        if (version < HomeLayout.VERSION) {
            prefs.edit()
                .putString(KEY_CARD_ORDER, layout.order.joinToString(",") { it.name })
                .putString(KEY_CARD_HIDDEN, layout.hidden.joinToString(",") { it.name })
                .putInt(KEY_CARD_VERSION, HomeLayout.VERSION)
                .apply()
        }
        return layout
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

    /** Sposta [card] al posto di [target] (trascinandola). */
    fun moveCardTo(card: HomeCard, target: HomeCard) {
        val order = _homeLayout.value.order
        moveCard(card, order.indexOf(target) - order.indexOf(card))
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
