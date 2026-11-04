package org.j3y.HuskerBot2.service

import org.j3y.HuskerBot2.model.ForecastGame
import org.j3y.HuskerBot2.model.GameSite
import org.j3y.HuskerBot2.model.OpenMeteoDailyData
import org.j3y.HuskerBot2.model.OpenMeteoHistoricalResponse
import org.j3y.HuskerBot2.model.TomorrowTimelineEntry
import org.j3y.HuskerBot2.model.TomorrowTimelines
import org.j3y.HuskerBot2.model.TomorrowWeatherResponse
import org.j3y.HuskerBot2.model.TomorrowWeatherValues
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.time.LocalDateTime
import java.time.ZoneId

class WeatherServiceTest {

    private val restTemplate = Mockito.mock(RestTemplate::class.java)
    private val gemini = Mockito.mock(GoogleGeminiService::class.java)
    private val service = WeatherService(
        geocodeMapsBaseUrl = "https://geocode.example",
        geocodeMapsApiKey = "",
        nominatimBaseUrl = "https://nominatim.example",
        rateLimitDelayMs = 0,
        nwsBaseUrl = "https://nws.example",
        userAgent = "test-agent",
        tomorrowBaseUrl = "https://tomorrow.example",
        tomorrowApiKey = "key",
        openMeteoHistoricalBaseUrl = "https://open-meteo.example",
        yearsForAverage = 30
    )
    private val illinoisAway = ForecastGame("Illinois", GameSite.AWAY, "Champaign, Ill.", null)
    private var prompt: String? = null

    @BeforeEach
    fun setUp() {
        inject("restTemplate", restTemplate)
        inject("googleGeminiService", gemini)
        `when`(gemini.generateText(Mockito.anyString(), Mockito.isNull(), Mockito.anyBoolean())).thenAnswer {
            prompt = it.getArgument(0)
            "hot take"
        }
    }

    @Test
    fun `short-range commentary prompt places an away game in the host city`() {
        val kickoff = LocalDateTime.now().plusDays(2)
        val hour = TomorrowTimelineEntry(
            time = kickoff.atZone(ZoneId.of("America/Chicago")).toInstant().toString(),
            values = TomorrowWeatherValues(temperature = 48.0, windSpeed = 12.0, windDirection = 315.0, weatherCode = 1101)
        )
        stubGet(TomorrowWeatherResponse::class.java, TomorrowWeatherResponse(TomorrowTimelines(hourly = listOf(hour))))

        val forecast = service.getWeatherForecast(40.0992, -88.2360, kickoff, illinoisAway)

        assertEquals("hot take", forecast?.snarkyDescription)
        assertPlacedInChampaign()
    }

    @Test
    fun `historical-average commentary prompt places an away game in the host city`() {
        // The 15th of next month is always more than a week out (historical path) and never Feb 29
        val kickoff = LocalDateTime.now().plusMonths(1).withDayOfMonth(15)
        val sameDayLastYear = kickoff.toLocalDate().minusYears(1).toString()
        stubGet(
            OpenMeteoHistoricalResponse::class.java,
            OpenMeteoHistoricalResponse(
                latitude = 40.0992,
                longitude = -88.2360,
                daily = OpenMeteoDailyData(
                    time = listOf(sameDayLastYear),
                    temperature_2m_max = listOf(55.0),
                    temperature_2m_min = listOf(37.0)
                )
            )
        )

        val forecast = service.getWeatherForecast(40.0992, -88.2360, kickoff, illinoisAway)

        assertEquals("hot take", forecast?.snarkyDescription)
        assertPlacedInChampaign()
    }

    private fun <T : Any> stubGet(type: Class<T>, body: T) {
        `when`(restTemplate.exchange(Mockito.anyString(), Mockito.eq(HttpMethod.GET), Mockito.any(HttpEntity::class.java), Mockito.eq(type)))
            .thenReturn(ResponseEntity.ok(body))
    }

    private fun assertPlacedInChampaign() {
        val sent = prompt
        assertNotNull(sent, "Gemini was never asked for commentary")
        assertTrue(sent!!.contains("Champaign, Ill."), sent)
        assertTrue(sent.contains("Illinois"), sent)
        assertTrue(sent.contains("away", ignoreCase = true), sent)
    }

    private fun inject(field: String, value: Any) {
        WeatherService::class.java.getDeclaredField(field).apply { isAccessible = true }.set(service, value)
    }
}
