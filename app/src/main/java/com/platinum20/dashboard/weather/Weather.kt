package com.platinum20.dashboard.weather

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparent: Double,
    val humidity: Int,
    val code: Int,
    val isDay: Boolean,
    val windSpeed: Double,
    val windDirection: Int,
    val precipitation: Double,
    val pressure: Double,
)

data class HourlyPoint(
    val time: LocalDateTime,
    val temperature: Double,
    val code: Int,
    val precipitationProbability: Int,
    val isDay: Boolean,
)

data class DailyPoint(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val precipitationProbability: Int,
    val precipitationSum: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val windMax: Double,
    val uvMax: Double,
)

data class AirQuality(
    /** Indice europeo (EAQI): 0 ottima, oltre 100 pessima. */
    val europeanAqi: Int,
    val pm25: Double,
    val pm10: Double,
    val ozone: Double,
) {
    val label: String get() = when {
        europeanAqi < 20 -> "Buona"
        europeanAqi < 40 -> "Discreta"
        europeanAqi < 60 -> "Moderata"
        europeanAqi < 80 -> "Scadente"
        europeanAqi < 100 -> "Molto scadente"
        else -> "Pessima"
    }
}

data class Forecast(
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: Long,
    val current: CurrentWeather,
    /** Le prossime 24 ore a partire da quella attuale. */
    val hourly: List<HourlyPoint>,
    val daily: List<DailyPoint>,
    /** Pioggia prevista ogni 15 minuti nelle prossime ore (mm). */
    val nowcast: List<Pair<LocalDateTime, Double>> = emptyList(),
    val air: AirQuality? = null,
) {
    val today: DailyPoint? get() = daily.firstOrNull()

    /** Avviso breve sulla pioggia delle prossime ore, se c'è qualcosa da dire. */
    val rainAlert: String?
        get() {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            val upcoming = nowcast.filter { !it.first.isBefore(current.time.minusMinutes(15)) }
            val rainingNow = current.precipitation >= 0.1 || (upcoming.firstOrNull()?.second ?: 0.0) >= 0.1
            if (upcoming.isNotEmpty()) {
                if (rainingNow) {
                    val stop = upcoming.firstOrNull { it.second < 0.1 }
                    return if (stop != null) "Pioggia fino alle ${stop.first.format(fmt)} circa" else "Pioggia nelle prossime ore"
                }
                val start = upcoming.firstOrNull { it.second >= 0.1 }
                if (start != null) return "Pioggia in arrivo verso le ${start.first.format(fmt)}"
            }
            // Oltre l'orizzonte dei 15 minuti: le probabilità orarie delle prossime 6 ore.
            val likely = hourly.drop(1).take(6).firstOrNull { it.precipitationProbability >= 60 }
            return likely?.let { "Probabile pioggia verso le ${it.time.format(fmt)}" }
        }
}

/** Legge la risposta di Open-Meteo (orari già nel fuso orario del posto, `timezone=auto`). */
object ForecastParser {

    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code," +
            "wind_speed_10m,wind_direction_10m,precipitation,pressure_msl" +
            "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max," +
            "precipitation_sum,sunrise,sunset,wind_speed_10m_max,uv_index_max" +
            "&minutely_15=precipitation&forecast_minutely_15=16" +
            "&timezone=auto&forecast_days=7"

    fun airUrl(lat: Double, lon: Double): String =
        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
            "&current=european_aqi,pm2_5,pm10,ozone&timezone=auto"

    fun parseAir(raw: String?): AirQuality? = raw?.let {
        runCatching {
            val c = JSONObject(it).getJSONObject("current")
            if (c.isNull("european_aqi")) return@runCatching null
            AirQuality(
                europeanAqi = c.optInt("european_aqi"),
                pm25 = c.optDouble("pm2_5"),
                pm10 = c.optDouble("pm10"),
                ozone = c.optDouble("ozone"),
            )
        }.getOrNull()
    }

    fun parse(
        raw: String,
        place: String,
        latitude: Double,
        longitude: Double,
        fetchedAt: Long,
        airRaw: String? = null,
    ): Forecast {
        val json = JSONObject(raw)
        val c = json.getJSONObject("current")
        val current = CurrentWeather(
            time = LocalDateTime.parse(c.getString("time")),
            temperature = c.optDouble("temperature_2m"),
            apparent = c.optDouble("apparent_temperature"),
            humidity = c.optInt("relative_humidity_2m"),
            code = c.optInt("weather_code"),
            isDay = c.optInt("is_day", 1) == 1,
            windSpeed = c.optDouble("wind_speed_10m"),
            windDirection = c.optInt("wind_direction_10m"),
            precipitation = c.optDouble("precipitation", 0.0),
            pressure = c.optDouble("pressure_msl"),
        )

        val h = json.getJSONObject("hourly")
        val hTimes = h.getJSONArray("time")
        val currentHour = current.time.withMinute(0)
        val hourly = (0 until hTimes.length())
            .map { i ->
                HourlyPoint(
                    time = LocalDateTime.parse(hTimes.getString(i)),
                    temperature = h.getJSONArray("temperature_2m").optDouble(i),
                    code = h.getJSONArray("weather_code").optInt(i),
                    precipitationProbability = h.getJSONArray("precipitation_probability").optInt(i),
                    isDay = h.getJSONArray("is_day").optInt(i, 1) == 1,
                )
            }
            .filter { !it.time.isBefore(currentHour) }
            .take(24)

        val d = json.getJSONObject("daily")
        val dTimes = d.getJSONArray("time")
        val daily = (0 until dTimes.length()).map { i ->
            DailyPoint(
                date = LocalDate.parse(dTimes.getString(i)),
                code = d.getJSONArray("weather_code").optInt(i),
                max = d.getJSONArray("temperature_2m_max").optDouble(i),
                min = d.getJSONArray("temperature_2m_min").optDouble(i),
                precipitationProbability = d.getJSONArray("precipitation_probability_max").optInt(i),
                precipitationSum = d.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                sunrise = d.getJSONArray("sunrise").dateTimeAt(i),
                sunset = d.getJSONArray("sunset").dateTimeAt(i),
                windMax = d.getJSONArray("wind_speed_10m_max").optDouble(i),
                uvMax = d.getJSONArray("uv_index_max").optDouble(i),
            )
        }
        val nowcast = json.optJSONObject("minutely_15")?.let { m ->
            val times = m.optJSONArray("time") ?: return@let emptyList<Pair<LocalDateTime, Double>>()
            val values = m.optJSONArray("precipitation") ?: return@let emptyList<Pair<LocalDateTime, Double>>()
            (0 until times.length()).mapNotNull { i ->
                val t = runCatching { LocalDateTime.parse(times.getString(i)) }.getOrNull() ?: return@mapNotNull null
                t to values.optDouble(i, 0.0).let { v -> if (v.isNaN()) 0.0 else v }
            }
        } ?: emptyList()
        return Forecast(place, latitude, longitude, fetchedAt, current, hourly, daily, nowcast, parseAir(airRaw))
    }

    private fun JSONArray.dateTimeAt(i: Int): LocalDateTime? =
        optString(i).takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
}

/** Codici meteo WMO usati da Open-Meteo. */
object WeatherCodes {
    fun description(code: Int): String = when (code) {
        0 -> "Sereno"
        1 -> "Prevalentemente sereno"
        2 -> "Parzialmente nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51, 53, 55 -> "Pioviggine"
        56, 57 -> "Pioviggine gelata"
        61 -> "Pioggia debole"
        63 -> "Pioggia"
        65 -> "Pioggia forte"
        66, 67 -> "Pioggia gelata"
        71 -> "Neve debole"
        73 -> "Neve"
        75 -> "Neve forte"
        77 -> "Nevischio"
        80, 81 -> "Rovesci"
        82 -> "Rovesci violenti"
        85, 86 -> "Rovesci di neve"
        95 -> "Temporale"
        96, 99 -> "Temporale con grandine"
        else -> "Condizioni sconosciute"
    }

    enum class Kind { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    fun kind(code: Int): Kind = when (code) {
        0 -> Kind.CLEAR
        1, 2 -> Kind.PARTLY
        3 -> Kind.CLOUDY
        45, 48 -> Kind.FOG
        51, 53, 55, 56, 57 -> Kind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Kind.RAIN
        71, 73, 75, 77, 85, 86 -> Kind.SNOW
        95, 96, 99 -> Kind.STORM
        else -> Kind.CLOUDY
    }

    /** Per widget e notifiche, dove non ci sono le icone di Compose. */
    fun emoji(code: Int, isDay: Boolean = true): String = when (kind(code)) {
        Kind.CLEAR -> if (isDay) "☀️" else "🌙"
        Kind.PARTLY -> if (isDay) "⛅" else "☁️"
        Kind.CLOUDY -> "☁️"
        Kind.FOG -> "🌫️"
        Kind.DRIZZLE -> "🌦️"
        Kind.RAIN -> "🌧️"
        Kind.SNOW -> "❄️"
        Kind.STORM -> "⛈️"
    }

    fun windDirection(degrees: Int): String {
        val names = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        return names[((degrees % 360 + 360) % 360 + 22) / 45 % 8]
    }
}

fun Double.asTemp(): String = if (isNaN()) "–" else "${Math.round(this)}°"
