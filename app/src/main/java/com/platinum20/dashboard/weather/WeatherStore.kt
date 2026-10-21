package com.platinum20.dashboard.weather

import android.content.Context
import com.platinum20.dashboard.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.abs

data class WeatherState(
    val forecast: Forecast? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Meteo condiviso tra Dashboard, dettaglio, widget e notifica.
 * L'ultima previsione resta salvata, così all'avvio si vede subito qualcosa anche offline.
 */
object WeatherStore {
    private const val PREFS = "weather"
    private const val KEY_JSON = "json"
    private const val KEY_PLACE = "place"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_TIME = "time"
    private const val MAX_AGE_MS = 30 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private var job: Job? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        _state.value = WeatherState(forecast = cached(context))
    }

    /** Aggiorna in background se la previsione è vecchia (o sempre, con [force]). */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        init(app)
        if (job?.isActive == true) return
        val current = _state.value.forecast
        if (!force && current != null && System.currentTimeMillis() - current.fetchedAt < MAX_AGE_MS) return
        job = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { fetch(app) }
                .onSuccess { f -> _state.value = WeatherState(forecast = f) }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Meteo non disponibile.") }
                }
        }
    }

    /** Scarica la previsione per la posizione attuale (o l'ultima nota) e la salva. */
    suspend fun fetch(context: Context): Forecast {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val location = LocationProvider.current(app)
        val lat: Double
        val lon: Double
        if (location != null) {
            lat = location.latitude
            lon = location.longitude
        } else if (prefs.contains(KEY_LAT)) {
            lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
            lon = prefs.getFloat(KEY_LON, 0f).toDouble()
        } else if (!LocationProvider.hasPermission(app)) {
            throw IOException("Serve il permesso di posizione per il meteo.")
        } else {
            throw IOException("Posizione non disponibile: attiva la localizzazione.")
        }

        val raw = runCatching { Http.getText(ForecastParser.url(lat, lon)) }
            .getOrElse { throw IOException("Meteo non raggiungibile. Controlla la connessione.") }

        // Il nome del posto si cerca solo se ci si è spostati, per non chiederlo ogni volta.
        val oldPlace = prefs.getString(KEY_PLACE, null)
        val moved = abs(prefs.getFloat(KEY_LAT, 0f) - lat) > 0.02 || abs(prefs.getFloat(KEY_LON, 0f) - lon) > 0.02
        val place = if (!moved && oldPlace != null) oldPlace
        else LocationProvider.placeName(app, lat, lon) ?: oldPlace.takeIf { !moved } ?: "La tua posizione"

        val now = System.currentTimeMillis()
        val forecast = ForecastParser.parse(raw, place, lat, lon, now)
        prefs.edit()
            .putString(KEY_JSON, raw)
            .putString(KEY_PLACE, place)
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LON, lon.toFloat())
            .putLong(KEY_TIME, now)
            .apply()
        _state.value = WeatherState(forecast = forecast)
        return forecast
    }

    fun cached(context: Context): Forecast? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching {
            ForecastParser.parse(
                raw,
                prefs.getString(KEY_PLACE, null) ?: "La tua posizione",
                prefs.getFloat(KEY_LAT, 0f).toDouble(),
                prefs.getFloat(KEY_LON, 0f).toDouble(),
                prefs.getLong(KEY_TIME, 0L),
            )
        }.getOrNull()
    }
}
