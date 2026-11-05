package org.j3y.HuskerBot2.commands.gameday

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.model.ForecastGame
import org.j3y.HuskerBot2.model.GameSite
import org.j3y.HuskerBot2.model.GeocodingResult
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.model.WeatherForecast
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.service.WeatherService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class GamedayWeatherTest {

    private val forecast = WeatherForecast(
        temperature = 48,
        shortForecast = "Partly Cloudy",
        detailedForecast = "Temperature around 48°F.",
        windSpeed = "12 mph",
        windDirection = "NW",
        humidity = null,
        snarkyDescription = "hot take"
    )

    // Kotlin-safe Mockito matchers for non-null parameters: register the matcher, return a placeholder
    private fun anyLocalDateTime(): LocalDateTime {
        Mockito.any(LocalDateTime::class.java)
        return LocalDateTime.MIN
    }

    private fun anyForecastGame(): ForecastGame {
        Mockito.any(ForecastGame::class.java)
        return ForecastGame("", GameSite.HOME, "", null)
    }

    /** Runs the command with [game] as the next game; returns the embed sent and the game the forecast was requested for. */
    private fun execute(game: ScheduleEntity): Pair<MessageEmbed, ForecastGame?> {
        val scheduleRepo = Mockito.mock(ScheduleRepo::class.java)
        val weatherService = Mockito.mock(WeatherService::class.java)
        val cmd = GamedayWeather()
        cmd.scheduleRepo = scheduleRepo
        cmd.weatherService = weatherService

        `when`(scheduleRepo.findAllBySeasonOrderByDateTimeAsc(Mockito.anyInt())).thenReturn(listOf(game))
        `when`(weatherService.getCoordinates(Mockito.anyString())).thenReturn(GeocodingResult(40.1, -88.2))
        var requested: ForecastGame? = null
        `when`(weatherService.getWeatherForecast(Mockito.anyDouble(), Mockito.anyDouble(), anyLocalDateTime(), anyForecastGame()))
            .thenAnswer {
                requested = it.getArgument(3)
                forecast
            }

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(event.deferReply()).thenReturn(Mockito.mock(ReplyCallbackAction::class.java))
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        cmd.execute(event)

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        return embedCaptor.value to requested
    }

    private fun MessageEmbed.field(name: String): String? = fields.first { it.name == name }.value

    @Test
    fun `away game is forecast for the host city and labeled Away`() {
        val game = ScheduleEntity(
            id = 21839L,
            opponent = "Illinois",
            location = "Champaign, Ill.",
            venueType = "away",
            venue = "",
            dateTime = Instant.now().plus(2, ChronoUnit.DAYS)
        )

        val (embed, requested) = execute(game)

        assertEquals(ForecastGame("Illinois", GameSite.AWAY, "Champaign, Ill.", null), requested)
        assertEquals("Champaign, Ill.", embed.field("📍 Location"))
        assertEquals("Away", embed.field("🏠 Home/Away"))
    }

    @Test
    fun `neutral-site game is labeled Neutral, not Away`() {
        val game = ScheduleEntity(
            id = 4244L,
            opponent = "Cincinnati",
            location = "Kansas City, Mo.",
            venueType = "neutral",
            venue = "Arrowhead Stadium",
            dateTime = Instant.now().plus(2, ChronoUnit.DAYS)
        )

        val (embed, requested) = execute(game)

        assertEquals(ForecastGame("Cincinnati", GameSite.NEUTRAL, "Kansas City, Mo.", "Arrowhead Stadium"), requested)
        assertEquals("Neutral", embed.field("🏠 Home/Away"))
    }
}
