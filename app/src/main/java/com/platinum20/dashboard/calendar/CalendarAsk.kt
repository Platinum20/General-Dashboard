package com.platinum20.dashboard.calendar

import android.content.Context

/**
 * Richiesta del permesso del calendario: dopo due "no" Android non mostra più la finestra,
 * quindi la card dell'Agenda si nasconde invece di restare con un "Consenti" che non fa nulla.
 */
object CalendarAsk {
    const val MAX_DENIALS = 2
    private const val PREFS = "calendar_ask"
    private const val KEY_DENIALS = "denials"

    /** Un "no" in più: true se la card va nascosta. */
    fun shouldHide(denials: Int): Boolean = denials >= MAX_DENIALS

    private const val KEY_OFF = "off"
    private val _off = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    /** "Non ora" sull'invito dell'agenda: Oggi non lo ripropone più. */
    fun isOff(context: Context): Boolean =
        _off.value ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OFF, false).also { _off.value = it }

    val off: kotlinx.coroutines.flow.StateFlow<Boolean?> get() = _off

    fun setOff(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OFF, true).apply()
        _off.value = true
    }

    fun recordDenial(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val denials = prefs.getInt(KEY_DENIALS, 0) + 1
        prefs.edit().putInt(KEY_DENIALS, denials).apply()
        return denials
    }
}
