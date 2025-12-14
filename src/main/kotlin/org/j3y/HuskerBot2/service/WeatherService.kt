package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.j3y.HuskerBot2.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Service
class WeatherService(
    @Value("\${weather.geocode-maps.base-url}") private val geocodeMapsBaseUrl: String,
    @Value("\${weather.geocode-maps.api-key}") private val geocodeMapsApiKey: String,
    @Value("\${weather.nominatim.base-url}") private val nominatimBaseUrl: String,
    @Value("\${weather.nominatim.rate-limit-delay}") private val rateLimitDelayMs: Long,
    @Value("\${weather.nws.base-url}") private val nwsBaseUrl: String,
    @Value("\${weather.nws.user-agent}") private val userAgent: String,
    @Value("\${weather.tomorrow.base-url}") private val tomorrowBaseUrl: String,
    @Value("\${weather.tomorrow.api-key}") private val tomorrowApiKey: String,
    @Value("\${weather.open-meteo.historical-base-url}") private val openMeteoHistoricalBaseUrl: String,
    @Value("\${weather.open-meteo.years-for-average}") private val yearsForAverage: Int
) {
    
    @Autowired
    private lateinit var googleGeminiService: GoogleGeminiService
    
    private final val log = LoggerFactory.getLogger(WeatherService::class.java)
    private final val restTemplate = RestTemplate()
    private final val objectMapper = ObjectMapper()
    private final val rateLimitDelay: Duration = Duration.ofMillis(rateLimitDelayMs)
    
    @Cacheable("coordinates", unless = "#result == null")
    fun getCoordinates(location: String): GeocodingResult? {
        // Check for hardcoded coordinates first to avoid API calls for common locations
        val hardcoded = getHardcodedCoordinates(location)
        if (hardcoded != null) {
            log.info("Using hardcoded coordinates for $location")
            return hardcoded
        }
        
        // Try geocode.maps.co first (primary provider - 25k requests/day free)
        val geocodeMapsResult = searchGeocodeMaps(location)
        if (geocodeMapsResult != null) {
            return geocodeMapsResult
        }
        
        // Fallback to Nominatim if geocode.maps.co fails
        log.info("Falling back to Nominatim for geocoding: $location")
        return searchNominatim(location)
    }
    
    private fun getHardcodedCoordinates(location: String): GeocodingResult? {
        // Normalize location string for comparison
        val normalized = location.trim().lowercase().replace(Regex("\\s+"), " ")
        
        return when {
            // Nebraska - Memorial Stadium
            normalized.contains("lincoln") && (normalized.contains("ne") || normalized.contains("nebraska")) -> 
                GeocodingResult(40.8206, -96.7056)
            
            // Big Ten Stadiums - for away games
            // Ohio State - Ohio Stadium
            normalized.contains("columbus") && (normalized.contains("oh") || normalized.contains("ohio")) -> 
                GeocodingResult(40.0017, -83.0197)
            
            // Michigan - Michigan Stadium (The Big House)
            normalized.contains("ann arbor") && (normalized.contains("mi") || normalized.contains("michigan")) -> 
                GeocodingResult(42.2658, -83.7486)
            
            // Penn State - Beaver Stadium
            normalized.contains("state college") || normalized.contains("university park") -> 
                GeocodingResult(40.8122, -77.8561)
            
            // Wisconsin - Camp Randall Stadium
            normalized.contains("madison") && (normalized.contains("wi") || normalized.contains("wisconsin")) -> 
                GeocodingResult(43.0700, -89.4128)
            
            // Iowa - Kinnick Stadium
            normalized.contains("iowa city") -> 
                GeocodingResult(41.6589, -91.5508)
            
            // Minnesota - Huntington Bank Stadium
            normalized.contains("minneapolis") && (normalized.contains("mn") || normalized.contains("minnesota")) -> 
                GeocodingResult(44.9765, -93.2248)
            
            // Illinois - Memorial Stadium
            normalized.contains("champaign") || normalized.contains("urbana") -> 
                GeocodingResult(40.0992, -88.2360)
            
            // Northwestern - Ryan Field
            normalized.contains("evanston") -> 
                GeocodingResult(42.0659, -87.6910)
            
            // Purdue - Ross-Ade Stadium
            normalized.contains("west lafayette") -> 
                GeocodingResult(40.4419, -86.9189)
            
            // Indiana - Memorial Stadium
            normalized.contains("bloomington") && (normalized.contains("in") || normalized.contains("indiana")) -> 
                GeocodingResult(39.1807, -86.5258)
            
            // Maryland - SECU Stadium
            normalized.contains("college park") -> 
                GeocodingResult(38.9907, -76.9488)
            
            // Rutgers - SHI Stadium
            normalized.contains("piscataway") || (normalized.contains("new brunswick") && normalized.contains("nj")) -> 
                GeocodingResult(40.5138, -74.4653)
            
            // Michigan State - Spartan Stadium
            normalized.contains("east lansing") -> 
                GeocodingResult(42.7284, -84.4822)
            
            // UCLA - Rose Bowl (temporary) / new stadium
            normalized.contains("pasadena") || (normalized.contains("los angeles") && normalized.contains("ucla")) -> 
                GeocodingResult(34.1614, -118.1676)
            
            // USC - LA Memorial Coliseum
            normalized.contains("los angeles") && (normalized.contains("ca") || normalized.contains("usc") || normalized.contains("coliseum")) -> 
                GeocodingResult(34.0141, -118.2879)
            
            // Oregon - Autzen Stadium
            normalized.contains("eugene") -> 
                GeocodingResult(44.0582, -123.0687)
            
            // Washington - Husky Stadium
            normalized.contains("seattle") -> 
                GeocodingResult(47.6505, -122.3017)
            
            // Other common Nebraska locations
            normalized.contains("omaha") && (normalized.contains("ne") || normalized.contains("nebraska")) -> 
                GeocodingResult(41.2565, -95.9345)
            
            else -> null
        }
    }
    
    @Cacheable("weather-forecast", unless = "#result == null") 
    fun getWeatherForecast(latitude: Double, longitude: Double, targetDate: LocalDateTime): WeatherForecast? {
        try {
            val now = LocalDateTime.now()
            val hoursUntilGame = ChronoUnit.HOURS.between(now, targetDate)
            val daysUntilGame = ChronoUnit.DAYS.between(now, targetDate)
            
            // Use Tomorrow Weather API if within 120 hours, otherwise fallback to NWS
            return if (hoursUntilGame <= 120) {
                log.info("Using Tomorrow Weather API for forecast $hoursUntilGame hours out")
                getTomorrowWeatherForecast(latitude, longitude, targetDate, hoursUntilGame)
            } else if (daysUntilGame <= 7) {
                log.info("Using NWS API for forecast $daysUntilGame days out (beyond Tomorrow Weather 120-hour limit)")
                getNWSWeatherForecast(latitude, longitude, targetDate)
            } else {
                // Beyond 7 days - use historical averages from Open-Meteo
                log.info("Game is $daysUntilGame days out - using historical averages from Open-Meteo")
                getHistoricalAverageForecast(latitude, longitude, targetDate)
            }
        } catch (e: Exception) {
            log.error("Error getting weather forecast for $latitude, $longitude", e)
            return null
        }
    }
    
    private fun getTomorrowWeatherForecast(latitude: Double, longitude: Double, targetDate: LocalDateTime, hoursUntilGame: Long): WeatherForecast? {
        try {
            val headers = HttpHeaders()
            headers.set("Content-Type", "application/json")
            val entity = HttpEntity<String>(headers)
            
            // Determine which timeline to use based on time until game
            val timeline = if (hoursUntilGame <= 120) "hourly" else "daily"
            val fields = "temperature,windSpeed,windDirection,humidity,precipitationProbability,weatherCode"
            
            val url = "$tomorrowBaseUrl/weather/forecast?location=$latitude,$longitude&apikey=$tomorrowApiKey&timesteps=${timeline}&fields=$fields&units=imperial"
            
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                TomorrowWeatherResponse::class.java
            )
            
            val weatherData = response.body
            if (weatherData != null) {
                val forecast = findTomorrowForecastForDate(weatherData, targetDate, timeline == "hourly")
                return if (forecast != null) {
                    addSnarkyDescription(forecast)
                } else {
                    log.warn("No suitable Tomorrow Weather forecast found for $targetDate")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Error getting Tomorrow Weather forecast, falling back to NWS", e)
            return getNWSWeatherForecast(latitude, longitude, targetDate)
        }
        return null
    }
    
    private fun getNWSWeatherForecast(latitude: Double, longitude: Double, targetDate: LocalDateTime): WeatherForecast? {
        try {
            val forecastData = getNWSForecast(latitude, longitude)
            if (forecastData != null) {
                val forecast = findNWSForecastForDate(forecastData, targetDate)
                return if (forecast != null) {
                    addSnarkyDescription(forecast)
                } else {
                    log.warn("No suitable NWS forecast found for $targetDate")
                    null
                }
            }
        } catch (e: Exception) {
            log.error("Error getting NWS forecast for $latitude, $longitude", e)
        }
        return null
    }
    
    private fun getHistoricalAverageForecast(latitude: Double, longitude: Double, targetDate: LocalDateTime): WeatherForecast? {
        try {
            val historicalAverage = getHistoricalAverages(latitude, longitude, targetDate.toLocalDate())
            if (historicalAverage != null) {
                val avgTemp = (historicalAverage.averageHigh + historicalAverage.averageLow) / 2
                val tempK = ((avgTemp - 32) * 5.0 / 9.0 + 273.15).roundToInt()
                
                val forecast = WeatherForecast(
                    temperature = avgTemp,
                    shortForecast = "Historical Average",
                    detailedForecast = buildHistoricalDetailedForecast(historicalAverage, targetDate),
                    windSpeed = historicalAverage.averageWindSpeed?.let { "${it.roundToInt()} mph" } ?: "Unknown",
                    windDirection = "Variable",
                    humidity = historicalAverage.averagePrecipitation?.let { 
                        if (it > 0) "Avg ${String.format("%.1f", it)} in precipitation" else null 
                    },
                    precipitationProbability = null,
                    micksTemp = "${tempK}K"
                )
                return addSnarkyDescriptionForHistorical(forecast, historicalAverage)
            }
        } catch (e: Exception) {
            log.error("Error getting historical average forecast for $latitude, $longitude on $targetDate", e)
        }
        return null
    }
    
    @Cacheable("historical-averages", unless = "#result == null")
    fun getHistoricalAverages(latitude: Double, longitude: Double, targetDate: LocalDate): HistoricalWeatherAverage? {
        try {
            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            val entity = HttpEntity<String>(headers)
            
            // Query the same calendar date for the past N years
            val currentYear = LocalDate.now().year
            val startYear = currentYear - yearsForAverage
            
            // Build date list for the same month/day across years
            val dates = (startYear until currentYear).mapNotNull { year ->
                try {
                    LocalDate.of(year, targetDate.month, targetDate.dayOfMonth)
                } catch (e: Exception) {
                    // Handle Feb 29 on non-leap years
                    null
                }
            }
            
            if (dates.isEmpty()) {
                log.warn("No valid historical dates found for ${targetDate.month}/${targetDate.dayOfMonth}")
                return null
            }
            
            // Query Open-Meteo for all historical data at once (more efficient)
            val startDate = dates.first()
            val endDate = dates.last()
            
            val url = "$openMeteoHistoricalBaseUrl?latitude=$latitude&longitude=$longitude" +
                    "&start_date=$startDate&end_date=$endDate" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,windspeed_10m_max" +
                    "&temperature_unit=fahrenheit&windspeed_unit=mph&timezone=auto"
            
            log.info("Fetching historical weather data from Open-Meteo for ${dates.size} years")
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                OpenMeteoHistoricalResponse::class.java
            )
            
            val data = response.body?.daily
            if (data == null || data.time.isNullOrEmpty()) {
                log.warn("No historical data returned from Open-Meteo")
                return null
            }
            
            // Filter to only the specific calendar date across years
            val targetMonth = targetDate.monthValue
            val targetDay = targetDate.dayOfMonth
            
            val matchingIndices = data.time.mapIndexedNotNull { index, dateStr ->
                val date = LocalDate.parse(dateStr)
                if (date.monthValue == targetMonth && date.dayOfMonth == targetDay) index else null
            }
            
            if (matchingIndices.isEmpty()) {
                log.warn("No matching dates found in historical data for ${targetDate.month}/${targetDate.dayOfMonth}")
                return null
            }
            
            // Calculate averages from matching dates
            val highTemps = matchingIndices.mapNotNull { data.temperature_2m_max?.getOrNull(it) }
            val lowTemps = matchingIndices.mapNotNull { data.temperature_2m_min?.getOrNull(it) }
            val precipitation = matchingIndices.mapNotNull { data.precipitation_sum?.getOrNull(it) }
            val windSpeeds = matchingIndices.mapNotNull { data.windspeed_10m_max?.getOrNull(it) }
            
            if (highTemps.isEmpty() || lowTemps.isEmpty()) {
                log.warn("Insufficient temperature data for historical average calculation")
                return null
            }
            
            val avgHigh = highTemps.average().roundToInt()
            val avgLow = lowTemps.average().roundToInt()
            val avgPrecip = if (precipitation.isNotEmpty()) precipitation.average() else null
            val avgWind = if (windSpeeds.isNotEmpty()) windSpeeds.average() else null
            
            log.info("Calculated ${matchingIndices.size}-year average for ${targetDate.month} ${targetDate.dayOfMonth}: High=$avgHigh°F, Low=$avgLow°F")
            
            return HistoricalWeatherAverage(
                averageHigh = avgHigh,
                averageLow = avgLow,
                averagePrecipitation = avgPrecip,
                averageWindSpeed = avgWind,
                yearsOfData = matchingIndices.size
            )
        } catch (e: Exception) {
            log.error("Error fetching historical averages from Open-Meteo: ${e.message}", e)
            return null
        }
    }
    
    private fun buildHistoricalDetailedForecast(avg: HistoricalWeatherAverage, targetDate: LocalDateTime): String {
        val parts = mutableListOf<String>()
        parts.add("Based on ${avg.yearsOfData}-year historical average for ${targetDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${targetDate.dayOfMonth}")
        parts.add("Average high: ${avg.averageHigh}°F, Average low: ${avg.averageLow}°F")
        avg.averageWindSpeed?.let { parts.add("Average wind: ${it.roundToInt()} mph") }
        avg.averagePrecipitation?.let { 
            if (it > 0.01) parts.add("Average precipitation: ${String.format("%.2f", it)} inches") 
        }
        return parts.joinToString(". ") + "."
    }
    
    private fun addSnarkyDescriptionForHistorical(forecast: WeatherForecast, avg: HistoricalWeatherAverage): WeatherForecast {
        return try {
            val weatherPrompt = createHistoricalWeatherPrompt(avg)
            val snarkyDescription = googleGeminiService.generateText(weatherPrompt)
            forecast.copy(snarkyDescription = snarkyDescription)
        } catch (e: Exception) {
            log.error("Error generating snarky weather description for historical forecast", e)
            forecast
        }
    }
    
    private fun createHistoricalWeatherPrompt(avg: HistoricalWeatherAverage): String {
        return """
            Create a hilariously snarky and unhinged weather forecast description for a Nebraska Huskers football game. 
            This is a HISTORICAL AVERAGE forecast (not a real-time prediction) because the game is more than a week away.
            Keep it under 100 words and make it brutally honest but entertaining. Include references to how this weather 
            will affect the game, the fans, and general Nebraska football culture. Feel free to joke about how we're 
            basically guessing based on history.
            
            Historical weather details (${avg.yearsOfData}-year average):
            - Average High: ${avg.averageHigh}°F
            - Average Low: ${avg.averageLow}°F
            - Average Wind: ${avg.averageWindSpeed?.roundToInt() ?: "Unknown"} mph
            ${avg.averagePrecipitation?.let { "- Average Precipitation: ${String.format("%.2f", it)} inches" } ?: ""}
            
            Make it spicy but keep it family-friendly for Discord. Remind everyone this is just historical data, not a crystal ball.
        """.trimIndent()
    }
    
    private fun findTomorrowForecastForDate(weatherData: TomorrowWeatherResponse, targetDate: LocalDateTime, useHourly: Boolean): WeatherForecast? {
        val timeline = if (useHourly) weatherData.timelines.hourly else weatherData.timelines.daily
        
        if (timeline == null || timeline.isEmpty()) {
            log.warn("No timeline data available")
            return null
        }
        
        val targetZoned = targetDate.atZone(ZoneId.of("America/Chicago"))
        
        // Find the closest forecast entry to the target time
        val closestEntry = if (useHourly) {
            timeline.minByOrNull { entry ->
                val entryTime = java.time.Instant.parse(entry.time).atZone(ZoneId.of("America/Chicago"))
                kotlin.math.abs(ChronoUnit.HOURS.between(targetZoned, entryTime))
            }
        } else {
            timeline.find { entry ->
                val entryTime = java.time.Instant.parse(entry.time).atZone(ZoneId.of("America/Chicago"))
                entryTime.toLocalDate() == targetZoned.toLocalDate()
            } ?: timeline.firstOrNull()
        }
        
        return closestEntry?.let { entry ->
            val values = entry.values
            val tempF = values.temperature?.roundToInt() ?: 0
            val tempK = ((tempF - 32) * 5.0 / 9.0 + 273.15).roundToInt()
            WeatherForecast(
                temperature = tempF,
                shortForecast = getWeatherCodeDescription(values.weatherCode ?: 0),
                detailedForecast = createDetailedForecast(values),
                windSpeed = "${values.windSpeed?.roundToInt() ?: 0} mph",
                windDirection = getWindDirection(values.windDirection ?: 0.0),
                humidity = values.humidity?.let { "${it.roundToInt()}% humidity" },
                precipitationProbability = values.precipitationProbability?.roundToInt(),
                micksTemp = "${tempK}K"
            )
        }
    }
    
    private fun findNWSForecastForDate(forecastData: JsonNode, targetDate: LocalDateTime): WeatherForecast? {
        return try {
            val periods = forecastData.get("properties")?.get("periods")
            if (periods != null && periods.isArray) {
                val targetZoned = targetDate.atZone(ZoneId.of("America/Chicago"))
                
                for (period in periods) {
                    val startTime = period.get("startTime")?.asText()
                    val endTime = period.get("endTime")?.asText()
                    
                    if (startTime != null && endTime != null) {
                        val periodStart = java.time.Instant.parse(startTime).atZone(ZoneId.of("America/Chicago"))
                        val periodEnd = java.time.Instant.parse(endTime).atZone(ZoneId.of("America/Chicago"))
                        
                        if (targetZoned.isAfter(periodStart) && targetZoned.isBefore(periodEnd)) {
                            val tempF = period.get("temperature")?.asInt() ?: 0
                            val tempK = ((tempF - 32) * 5.0 / 9.0 + 273.15).roundToInt()
                            return WeatherForecast(
                                temperature = tempF,
                                shortForecast = period.get("shortForecast")?.asText() ?: "Unknown",
                                detailedForecast = period.get("detailedForecast")?.asText() ?: "No details available",
                                windSpeed = period.get("windSpeed")?.asText() ?: "Unknown",
                                windDirection = period.get("windDirection")?.asText() ?: "Unknown",
                                humidity = period.get("probabilityOfPrecipitation")?.get("value")?.asText()?.let { "${it}% chance of precipitation" },
                                micksTemp = "${tempK}K"
                            )
                        }
                    }
                }
                
                // Fallback to first period
                if (periods.size() > 0) {
                    val firstPeriod = periods.get(0)
                    val tempF = firstPeriod.get("temperature")?.asInt() ?: 0
                    val tempK = ((tempF - 32) * 5.0 / 9.0 + 273.15).roundToInt()
                    log.info("Using first available NWS forecast period as fallback")
                    return WeatherForecast(
                        temperature = tempF,
                        shortForecast = firstPeriod.get("shortForecast")?.asText() ?: "Unknown",
                        detailedForecast = firstPeriod.get("detailedForecast")?.asText() ?: "No details available",
                        windSpeed = firstPeriod.get("windSpeed")?.asText() ?: "Unknown",
                        windDirection = firstPeriod.get("windDirection")?.asText() ?: "Unknown",
                        humidity = firstPeriod.get("probabilityOfPrecipitation")?.get("value")?.asText()?.let { "${it}% chance of precipitation" },
                        micksTemp = "${tempK}K"
                    )
                }
            }
            null
        } catch (e: Exception) {
            log.error("Error parsing NWS forecast data for target date: $targetDate", e)
            null
        }
    }
    
    private fun addSnarkyDescription(forecast: WeatherForecast): WeatherForecast {
        return try {
            val weatherPrompt = createWeatherPrompt(forecast)
            val snarkyDescription = googleGeminiService.generateText(weatherPrompt)
            forecast.copy(snarkyDescription = snarkyDescription)
        } catch (e: Exception) {
            log.error("Error generating snarky weather description", e)
            forecast
        }
    }
    
    private fun createWeatherPrompt(forecast: WeatherForecast): String {
        return """
            Create a hilariously snarky and unhinged weather forecast description for a Nebraska Huskers football game. 
            Keep it under 100 words and make it brutally honest but entertaining. Include references to how this weather 
            will affect the game, the fans, and general Nebraska football culture.
            
            Weather details:
            - Temperature: ${forecast.temperature}°F
            - Conditions: ${forecast.shortForecast}
            - Wind: ${forecast.windSpeed} ${forecast.windDirection}
            - Humidity: ${forecast.humidity ?: "Unknown"}
            ${forecast.precipitationProbability?.let { "- Precipitation chance: $it%" } ?: ""}
            
            Make it spicy but keep it family-friendly for Discord.
        """.trimIndent()
    }
    
    private fun getWeatherCodeDescription(code: Int): String {
        return when (code) {
            0 -> "Unknown"
            1000 -> "Clear"
            1001 -> "Cloudy"
            1100 -> "Mostly Clear"
            1101 -> "Partly Cloudy"
            1102 -> "Mostly Cloudy"
            2000 -> "Fog"
            2100 -> "Light Fog"
            3000 -> "Light Wind"
            3001 -> "Wind"
            3002 -> "Strong Wind"
            4000 -> "Drizzle"
            4001 -> "Rain"
            4200 -> "Light Rain"
            4201 -> "Heavy Rain"
            5000 -> "Snow"
            5001 -> "Flurries"
            5100 -> "Light Snow"
            5101 -> "Heavy Snow"
            6000 -> "Freezing Drizzle"
            6001 -> "Freezing Rain"
            6200 -> "Light Freezing Rain"
            6201 -> "Heavy Freezing Rain"
            7000 -> "Ice Pellets"
            7101 -> "Heavy Ice Pellets"
            7102 -> "Light Ice Pellets"
            8000 -> "Thunderstorm"
            else -> "Unknown Conditions"
        }
    }
    
    private fun createDetailedForecast(values: TomorrowWeatherValues): String {
        val parts = mutableListOf<String>()
        
        values.temperature?.let { parts.add("Temperature around ${it.roundToInt()}°F") }
        values.windSpeed?.let { parts.add("winds at ${it.roundToInt()} mph") }
        values.humidity?.let { parts.add("${it.roundToInt()}% humidity") }
        values.precipitationProbability?.let { 
            if (it > 0) parts.add("${it.roundToInt()}% chance of precipitation") 
        }
        
        return if (parts.isNotEmpty()) {
            parts.joinToString(", ") + "."
        } else {
            "Weather details unavailable."
        }
    }
    
    private fun getWindDirection(degrees: Double): String {
        return when (degrees.roundToInt()) {
            in 0..22, in 338..360 -> "N"
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SW"
            in 248..292 -> "W"
            in 293..337 -> "NW"
            else -> "Variable"
        }
    }
    
    private fun searchGeocodeMaps(location: String): GeocodingResult? {
        // Skip if no API key configured
        if (geocodeMapsApiKey.isBlank()) {
            log.warn("geocode.maps.co API key not configured, skipping")
            return null
        }
        
        return try {
            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            val entity = HttpEntity<String>(headers)
            
            val encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8)
            val url = "$geocodeMapsBaseUrl/search?q=$encodedLocation&api_key=$geocodeMapsApiKey"
            
            log.info("Geocoding location via geocode.maps.co: $location")
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Array<JsonNode>::class.java
            )
            
            val results = response.body
            if (results != null && results.isNotEmpty()) {
                val result = results[0]
                val lat = result.get("lat").asText().toDoubleOrNull()
                val lon = result.get("lon").asText().toDoubleOrNull()
                if (lat != null && lon != null) {
                    log.info("Found coordinates via geocode.maps.co for $location: $lat, $lon")
                    GeocodingResult(lat, lon)
                } else {
                    log.warn("Invalid coordinates returned from geocode.maps.co for: $location")
                    null
                }
            } else {
                log.warn("No coordinates found via geocode.maps.co for: $location")
                null
            }
        } catch (e: Exception) {
            log.error("Error geocoding location via geocode.maps.co: $location - ${e.message}", e)
            null
        }
    }
    
    private fun searchNominatim(location: String): GeocodingResult? {
        return try {
            Thread.sleep(rateLimitDelay.toMillis())
            
            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            val entity = HttpEntity<String>(headers)
            
            val encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8)
            val url = "$nominatimBaseUrl/search?q=$encodedLocation&format=json&limit=1"
            
            log.info("Geocoding location via Nominatim: $location")
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                Array<JsonNode>::class.java
            )
            
            val results = response.body
            if (results != null && results.isNotEmpty()) {
                val result = results[0]
                val lat = result.get("lat").asDouble()
                val lon = result.get("lon").asDouble()
                log.info("Found coordinates for $location: $lat, $lon")
                GeocodingResult(lat, lon)
            } else {
                log.warn("No coordinates found for location: $location")
                null
            }
        } catch (e: Exception) {
            log.error("Error geocoding location via Nominatim: $location - ${e.message}", e)
            null
        }
    }
    
    private fun getNWSForecast(latitude: Double, longitude: Double): JsonNode? {
        return try {
            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            val entity = HttpEntity<String>(headers)
            
            val pointsUrl = "$nwsBaseUrl/points/$latitude,$longitude"
            val pointsResponse = restTemplate.exchange(
                pointsUrl,
                HttpMethod.GET,
                entity,
                JsonNode::class.java
            )
            
            val forecastUrl = pointsResponse.body?.get("properties")?.get("forecast")?.asText()
            if (forecastUrl != null) {
                val forecastResponse = restTemplate.exchange(
                    forecastUrl,
                    HttpMethod.GET,
                    entity,
                    JsonNode::class.java
                )
                forecastResponse.body
            } else {
                log.warn("No forecast URL found for coordinates: $latitude, $longitude")
                null
            }
        } catch (e: Exception) {
            log.error("Error getting NWS forecast for $latitude, $longitude", e)
            null
        }
    }

    fun getSunriseSunsetTimes(latitude: Double, longitude: Double, date: LocalDate, zone: ZoneId = ZoneId.of("America/Chicago")): Pair<ZonedDateTime, ZonedDateTime>? {
        return try {
            // Use sunrisesunset.io API which returns local times and timezone metadata
            val headers = HttpHeaders()
            headers.set("User-Agent", userAgent)
            val entity = HttpEntity<String>(headers)

            val url = "https://api.sunrisesunset.io/json?lat=${latitude}&lng=${longitude}&date=${date}"
            val response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                JsonNode::class.java
            )

            val body = response.body ?: return null
            val results = body.get("results") ?: return null

            val sunriseStr = results.get("sunrise")?.asText()
            val sunsetStr = results.get("sunset")?.asText()
            if (sunriseStr.isNullOrBlank() || sunsetStr.isNullOrBlank()) return null

            // Prefer API-provided timezone if available, otherwise fallback to provided zone
            val tzId = results.get("timezone")?.asText()
            val useZone = try {
                if (!tzId.isNullOrBlank()) ZoneId.of(tzId) else zone
            } catch (e: Exception) { zone }

            fun parseLocalTimeFlexible(text: String): java.time.LocalTime? {
                val patterns = listOf("h:mm:ss a", "h:mm a")
                for (p in patterns) {
                    try {
                        val fmt = java.time.format.DateTimeFormatter.ofPattern(p)
                        return java.time.LocalTime.parse(text, fmt)
                    } catch (_: Exception) { }
                }
                return null
            }

            val sunriseLocal = parseLocalTimeFlexible(sunriseStr) ?: return null
            val sunsetLocal = parseLocalTimeFlexible(sunsetStr) ?: return null

            val sunriseZdt = ZonedDateTime.of(date, sunriseLocal, useZone)
            val sunsetZdt = ZonedDateTime.of(date, sunsetLocal, useZone)

            Pair(sunriseZdt, sunsetZdt)
        } catch (e: Exception) {
            log.error("Error computing sunrise/sunset times via sunrisesunset.io for $latitude,$longitude on $date", e)
            null
        }
    }
}