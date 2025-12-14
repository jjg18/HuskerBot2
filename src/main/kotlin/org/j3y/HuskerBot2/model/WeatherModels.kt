package org.j3y.HuskerBot2.model

data class WeatherForecast(
    val temperature: Int,
    val shortForecast: String,
    val detailedForecast: String,
    val windSpeed: String,
    val windDirection: String,
    val humidity: String?,
    val precipitationProbability: Int? = null,
    val snarkyDescription: String? = null,
    val micksTemp: String? = null
)

data class GeocodingResult(
    val latitude: Double,
    val longitude: Double
)

data class TomorrowWeatherResponse(
    val timelines: TomorrowTimelines
)

data class TomorrowTimelines(
    val minutely: List<TomorrowTimelineEntry>? = null,
    val hourly: List<TomorrowTimelineEntry>? = null,
    val daily: List<TomorrowTimelineEntry>? = null
)

data class TomorrowTimelineEntry(
    val time: String,
    val values: TomorrowWeatherValues
)

data class TomorrowWeatherValues(
    val temperature: Double? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val humidity: Double? = null,
    val precipitationProbability: Double? = null,
    val weatherCode: Int? = null
)

// Open-Meteo Historical API response models
data class OpenMeteoHistoricalResponse(
    val latitude: Double,
    val longitude: Double,
    val daily: OpenMeteoDailyData? = null
)

data class OpenMeteoDailyData(
    val time: List<String>? = null,
    val temperature_2m_max: List<Double?>? = null,
    val temperature_2m_min: List<Double?>? = null,
    val precipitation_sum: List<Double?>? = null,
    val windspeed_10m_max: List<Double?>? = null
)

// Historical average result
data class HistoricalWeatherAverage(
    val averageHigh: Int,
    val averageLow: Int,
    val averagePrecipitation: Double?,
    val averageWindSpeed: Double?,
    val yearsOfData: Int
)