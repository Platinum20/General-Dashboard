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

/** Preferenze dell'app, salvate sul telefono e osservabili dalla UI. */
object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_THEME = "theme"

    private lateinit var prefs: SharedPreferences
    private val _theme = MutableStateFlow(AppTheme.DYNAMIC)
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _theme.value = prefs.getString(KEY_THEME, null)
            ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
            ?: AppTheme.DYNAMIC
    }

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        prefs.edit().putString(KEY_THEME, theme.name).apply()
    }
}
