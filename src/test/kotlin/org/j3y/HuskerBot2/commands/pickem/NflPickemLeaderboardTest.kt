package org.j3y.HuskerBot2.commands.pickem

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.model.NflPick
import org.j3y.HuskerBot2.repository.NflPickRepo
import org.j3y.HuskerBot2.service.NflPickemLeaderboardService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.LocalDate

class NflPickemLeaderboardTest {

    private lateinit var cmd: NflPickemLeaderboard
    private lateinit var pickRepo: NflPickRepo

    @BeforeEach
    fun setup() {
        cmd = NflPickemLeaderboard()
        pickRepo = Mockito.mock(NflPickRepo::class.java)
        cmd.nflPickRepo = pickRepo
        cmd.leaderboardService = NflPickemLeaderboardService()
    }

    @Test
    fun `execute replies with no picks message when none found for current season`() {
        val season = SeasonResolver.currentNflSeason()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // Repo returns empty
        `when`(pickRepo.findAll()).thenReturn(emptyList())

        // Defer + hook
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("No picks recorded yet for $season.", captor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute builds and sends sorted leaderboard with ties resolved by userId`() {
        val season = SeasonResolver.currentNflSeason()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        // Picks: user 1 -> 5 correct (7 total), user 2 -> 3 correct (4 total), user 3 -> 5 correct (5 total)
        fun p(user: Long, correct: Boolean) = NflPick(
            gameId = System.nanoTime(), // unique enough for tests
            userId = user,
            season = season,
            week = 1,
            winningTeamId = 0,
            processed = true,
            correctPick = correct
        )
        val picks = buildList {
            repeat(5) { add(p(1L, true)) }
            repeat(2) { add(p(1L, false)) }
            repeat(3) { add(p(2L, true)) }
            repeat(1) { add(p(2L, false)) }
            repeat(5) { add(p(3L, true)) }
        }
        `when`(pickRepo.findAll()).thenReturn(picks)

        // Defer + hook
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        // The command now sends an embed
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        Mockito.verify(messageAction).queue()

        val embed = embedCaptor.value
        assertEquals("NFL Pick'em — Season Leaderboard ($season)", embed.title)
        assertEquals("Each correct pick is worth 10 points.", embed.description)
        assertTrue(embed.fields.isNotEmpty())
        val field = embed.fields[0]
        assertEquals("Leaderboard", field.name)

        val fieldValue = field.value ?: ""
        val lines = fieldValue.split("\n").filter { it.isNotBlank() }
        assertEquals(3, lines.size)
        // Check ordering and formatting with medals and totals
        // userId tie-breaker (1 < 3)
        assertEquals("🥇 <@1> — 50 pts (5/7 correct)", lines[0])
        assertEquals("🥇 <@3> — 50 pts (5/5 correct)", lines[1])
        assertEquals("🥉 <@2> — 30 pts (3/4 correct)", lines[2])
    }

    @Test
    fun `execute says no picks when only incorrect picks exist`() {
        val season = SeasonResolver.currentNflSeason()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val picks = listOf(
            NflPick(gameId = 1L, userId = 10L, season = season, week = 1, winningTeamId = 0, processed = true, correctPick = false),
            NflPick(gameId = 2L, userId = 11L, season = season, week = 1, winningTeamId = 0, processed = true, correctPick = false)
        )
        `when`(pickRepo.findAll()).thenReturn(picks)

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("No picks recorded yet for $season.", captor.value)
        Mockito.verify(messageAction).queue()
    }
}
