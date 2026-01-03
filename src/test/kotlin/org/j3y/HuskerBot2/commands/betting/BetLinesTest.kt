package org.j3y.HuskerBot2.commands.betting

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.interactions.InteractionHook
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.service.CfbBettingLinesService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate

class BetLinesTest {

    private lateinit var betLines: BetLines
    private lateinit var scheduleRepo: ScheduleRepo
    private lateinit var cfbBettingLinesService: CfbBettingLinesService

    private val mapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        betLines = BetLines()
        scheduleRepo = Mockito.mock(ScheduleRepo::class.java)
        cfbBettingLinesService = Mockito.mock(CfbBettingLinesService::class.java)
        betLines.scheduleRepo = scheduleRepo
        betLines.cfbBettingLinesService = cfbBettingLinesService
    }

    @Test
    fun `getOptions builds expected week choices`() {
        val season = SeasonResolver.currentCfbSeason()
        val games = listOf(
            ScheduleEntity(id = 1, opponent = "Iowa", season = season, week = 3),
            ScheduleEntity(id = 2, opponent = "Minnesota", season = season, week = 5)
        )
        `when`(scheduleRepo.findAllBySeasonOrderByDateTimeAsc(season)).thenReturn(games)

        val opts: List<OptionData> = betLines.getOptions()

        assertEquals(1, opts.size)
        assertEquals("week", opts[0].name)
        assertEquals(OptionType.INTEGER, opts[0].type)
        val weekChoices: List<Command.Choice> = opts[0].choices
        assertEquals(2, weekChoices.size)
        assertEquals("Iowa - Week 3", weekChoices[0].name)
        assertEquals(3L, weekChoices[0].asLong)
        assertEquals("Minnesota - Week 5", weekChoices[1].name)
        assertEquals(5L, weekChoices[1].asLong)
    }

    @Test
    fun `execute sends embed with odds when Nebraska game found`() {
        val season = SeasonResolver.currentCfbSeason()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        val messageCreateAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // defer reply and hook setup
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageCreateAction)

        // Default week = 1 (no option supplied)
        val sched = ScheduleEntity(id = 10, opponent = "Iowa", season = season, week = 1, dateTime = Instant.now())
        `when`(scheduleRepo.findBySeasonAndWeek(season, 1)).thenReturn(sched)

        // CFBD lines JSON with Nebraska game and odds
        val json = """
            [
              {
                "homeTeam": "Nebraska",
                "awayTeam": "Iowa",
                "lines": [
                  {"provider": "consensus", "formattedSpread": "Nebraska -3.5", "spread": -3.5, "overUnder": 45.0}
                ]
              }
            ]
        """.trimIndent()
        val node: JsonNode = mapper.readTree(json)
        `when`(cfbBettingLinesService.getLines(season, 1, "nebraska")).thenReturn(node)

        betLines.execute(event)

        // Verify deferred reply queued
        Mockito.verify(replyAction).queue()

        // Capture embed sent via hook
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        Mockito.verify(messageCreateAction).queue()

        val embed = embedCaptor.value
        assertEquals("Opponent Betting Lines", embed.title)
        val fields = embed.fields.associate { it.name to it.value }
        assertEquals("Iowa", fields["Opponent"]) // from schedule
        assertEquals("$season", fields["Year"])
        assertEquals("1", fields["Week"])
        assertEquals("-3.5", fields["Spread"])
        assertEquals("45.0", fields["Over/Under"])
        assertEquals("Nebraska -3.5", fields["Details"]) // from CFBD odds
        // Color and other visual properties are not asserted here
    }

    @Test
    fun `execute replies with message when Nebraska game not found in ESPN data`() {
        val season = SeasonResolver.currentCfbSeason()
        val week = 2
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // Setup defer and hook
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Provide an option for week via default behavior (missing -> 1). To test explicit week, we
        // can stub getOption("week") to return null and simply set the scoreboard fetch to week 1 or
        // we can simulate week=2 by making the method expect 2. We'll stick with default = 1 for simplicity.
        // But ensure our message uses that default. We'll still validate the message for week=1.

        // Simulate no data returned from CFBD service
        `when`(cfbBettingLinesService.getLines(season, 1, "nebraska")).thenReturn(null)

        betLines.execute(event)

        // Verify deferred reply queued
        Mockito.verify(replyAction).queue()

        // Should send a plain message via hook
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        Mockito.verify(messageAction).queue()
        assertEquals("Unable to find Nebraska Cornhuskers game for week 1.", msgCaptor.value)
    }
}
