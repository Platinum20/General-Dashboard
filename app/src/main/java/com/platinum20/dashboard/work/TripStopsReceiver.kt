package com.platinum20.dashboard.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Il tasto "Fermate" della notifica del treno: apre o chiude l'elenco delle fermate e ridisegna subito. */
class TripStopsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_EVENT, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } ?: return
        val prefs = context.getSharedPreferences(TripLiveWorker.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(TripLiveWorker.stopsKey(id), !prefs.getBoolean(TripLiveWorker.stopsKey(id), false)).apply()
        TripLiveWorker.now(context)
    }

    companion object {
        const val EXTRA_EVENT = "event"
    }
}
