package com.platinum20.dashboard.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.platinum20.dashboard.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

/** Posizione del telefono senza Google Play Services: basta il LocationManager di sistema. */
object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Ultima posizione nota se recente, altrimenti ne chiede una nuova (al massimo 20 secondi).
     * In background ([fresh] = false) ci si accontenta dell'ultima nota: Android non dà la posizione alle app chiuse.
     */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context, fresh: Boolean = true): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        val last = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (!fresh || (last != null && System.currentTimeMillis() - last.time < FRESH_MS)) return last

        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return last
        val fresh = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    // Su Android 8-10 questi metodi non hanno un'implementazione predefinita.
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Richiesto solo dalle versioni vecchie di Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }
                runCatching {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                }.onFailure { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
        return fresh ?: last
    }

    /** Nome della località: prima il geocoder del telefono, poi un servizio gratuito senza chiave. */
    suspend fun placeName(context: Context, lat: Double, lon: Double): String? {
        val fromGeocoder = withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                @Suppress("DEPRECATION")
                val address = Geocoder(context, Locale.ITALIAN).getFromLocation(lat, lon, 1)?.firstOrNull()
                address?.locality ?: address?.subAdminArea ?: address?.adminArea
            }.getOrNull()
        }
        if (!fromGeocoder.isNullOrBlank()) return fromGeocoder

        return runCatching {
            val json = JSONObject(
                Http.getText(
                    "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                        "?latitude=$lat&longitude=$lon&localityLanguage=it"
                )
            )
            json.optString("city").ifBlank { json.optString("locality") }.ifBlank { null }
        }.getOrNull()
    }

    private const val FRESH_MS = 30 * 60_000L
}
