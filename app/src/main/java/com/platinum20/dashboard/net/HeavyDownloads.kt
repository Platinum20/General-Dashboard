package com.platinum20.dashboard.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.platinum20.dashboard.settings.AppSettings

/**
 * Le cose pesanti da scaricare: le voci di lettura e gli APK degli aggiornamenti.
 * Con l'impostazione accesa aspettano il Wi-Fi, così non si consumano i dati del telefono.
 */
object HeavyDownloads {
    const val BLOCKED = "In attesa del Wi-Fi: lo scarica appena ti colleghi"

    /** Vero se adesso un download pesante non deve partire. */
    fun blocked(context: Context): Boolean = AppSettings.wifiOnlyDownloads.value && metered(context)

    /** La rete di adesso fa consumare i dati (o non si sa che rete è). */
    private fun metered(context: Context): Boolean {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
