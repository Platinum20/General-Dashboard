package com.platinum20.dashboard.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * C'è la rete? (revisione 1.402: senza rete nessuna pagina lo diceva e le liste restavano a caricare.)
 * Una sola richiesta al sistema per tutta l'app; le pagine leggono [online] e la riga sotto il titolo
 * sparisce da sola quando la rete torna.
 */
object Online {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()
    private var started = false

    fun init(context: Context) {
        if (started) return
        started = true
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java) ?: return
        // Senza una rete attiva siamo fuori; con una rete di cui il sistema non dice niente, meglio credere di sì.
        _online.value = manager.activeNetwork != null
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.hasInternet()
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }
            })
        }
    }

    private fun NetworkCapabilities?.hasInternet(): Boolean = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** La riga sopra il titolo: "Senza rete", poi quello che diceva già la pagina. */
    fun subtitle(online: Boolean, subtitle: String?): String? =
        if (online) subtitle else listOfNotNull("Senza rete", subtitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
}
