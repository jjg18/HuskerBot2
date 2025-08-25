package org.j3y.HuskerBot2.commands.pickem

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.model.NflGameEntity
import org.j3y.HuskerBot2.model.NflPick
import org.j3y.HuskerBot2.repository.NflGameRepo
import org.j3y.HuskerBot2.repository.NflPickRepo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.util.*

class NflPickemShowTest {

    private lateinit var cmd: NflPickemShow
    private lateinit var pickRepo: NflPickRepo
    private lateinit var gameRepo: NflGameRepo

    @BeforeEach
    fun setup() {
        cmd = NflPickemShow()
        pickRepo = Mockito.mock(NflPickRepo::class.java)
        gameRepo = Mockito.mock(NflGameRepo::class.java)
        cmd.nflPickRepo = pickRepo
        cmd.nflGameRepo = gameRepo
    }

    private fun mockEvent(userId: Long = 42L, mention: String = "<@42>"): Triple<SlashCommandInteractionEvent, ReplyCallbackAction, InteractionHook> {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        val user = Mockito.mock(User::class.java)
        `when`(event.user).thenReturn(user)
        `when`(user.idLong).thenReturn(userId)
        `when`(user.asMention).thenReturn(mention)
        `when`(event.deferReply(Mockito.anyBoolean())).thenReturn(replyAction)
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        return Triple(event, replyAction, hook)
    }

    private fun pick(gameId: Long, userId: Long, week: Int, processed: Boolean, correct: Boolean, winningTeamId: Long) =
        NflPick(
            gameId = gameId,
            userId = userId,
            season = LocalDate.now().year,
            week = week,
            winningTeamId = winningTeamId,
            processed = processed,
            correctPick = correct
        )

    private fun game(
        id: Long,
        home: String,
        homeId: Long,
        away: String,
        awayId: Long,
        ts: Instant = Instant.now().plusSeconds(id) // unique, predictable ordering by id
    ) = NflGameEntity(
        id = id,
        homeTeam = home,
        homeTeamId = homeId,
        awayTeam = away,
        awayTeamId = awayId,
        dateTime = ts,
        season = LocalDate.now().year,
        week = 1
    )

    @Test
    fun `no picks found sends message`() {
        val (event, replyAction, hook) = mockEvent(userId = 99L, mention = "<@99>")
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        val week = 3
        `when`(pickRepo.findByUserIdAndSeasonAndWeek(99L, LocalDate.now().year, week)).thenReturn(emptyList())
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.handleEvent(event, week)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("No picks found for <@99> in week $week.", captor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `unprocessed picks create embed without footer and pending note`() {
        val week = 1
        val (event, replyAction, hook) = mockEvent(userId = 7L, mention = "<@7>")
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        val p1 = pick(1L, 7L, week, processed = false, correct = false, winningTeamId = 100L)
        val p2 = pick(2L, 7L, week, processed = false, correct = false, winningTeamId = 200L)
        `when`(pickRepo.findByUserIdAndSeasonAndWeek(7L, LocalDate.now().year, week)).thenReturn(listOf(p2, p1))

        val g1 = game(1L, home = "HomeA", homeId = 101L, away = "AwayA", awayId = 100L)
        val g2 = game(2L, home = "HomeB", homeId = 200L, away = "AwayB", awayId = 201L)
        `when`(gameRepo.findById(1L)).thenReturn(Optional.of(g1))
        `when`(gameRepo.findById(2L)).thenReturn(Optional.of(g2))

        cmd.handleEvent(event, week)

        Mockito.verify(replyAction).queue()
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        Mockito.verify(messageAction).queue()

        val embed = embedCaptor.value
        assertEquals("NFL Pick'em — Week $week Picks", embed.title)
        assertTrue(embed.description?.contains("User: <@7>") == true)
        assertTrue(embed.description?.contains("Results pending") == true)
        // Should be sorted by game dateTime: g1(id1) then g2(id2)
        val picksField = embed.fields.firstOrNull { it.name == "Picks" }
        assertNotNull(picksField)
        val value = picksField?.value ?: ""
        val lines = value.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        // For g1: away (id 100) picked => away has check mark prefix, home has cross suffix
        assertTrue(lines[0].contains("☑️ AwayA @ HomeA ❌") || lines[0].contains("☑\uFE0F AwayA @ HomeA ❌"))
        // For g2: home (id 200) picked => away has cross prefix, home has check suffix
        assertTrue(lines[1].contains("❌ AwayB @ HomeB ☑️") || lines[1].contains("❌ AwayB @ HomeB ☑\uFE0F"))
        assertNull(embed.footer) // no footer when unprocessed
    }

    @Test
    fun `processed picks include correctness and footer score`() {
        val week = 2
        val (event, replyAction, hook) = mockEvent(userId = 5L, mention = "<@5>")
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        val p1 = pick(10L, 5L, week, processed = true, correct = true, winningTeamId = 300L)
        val p2 = pick(20L, 5L, week, processed = true, correct = false, winningTeamId = 400L)
        `when`(pickRepo.findByUserIdAndSeasonAndWeek(5L, LocalDate.now().year, week)).thenReturn(listOf(p1, p2))

        val g1 = game(10L, home = "H1", homeId = 301L, away = "A1", awayId = 300L)
        val g2 = game(20L, home = "H2", homeId = 400L, away = "A2", awayId = 401L)
        `when`(gameRepo.findById(10L)).thenReturn(Optional.of(g1))
        `when`(gameRepo.findById(20L)).thenReturn(Optional.of(g2))

        cmd.handleEvent(event, week)

        Mockito.verify(replyAction).queue()
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        Mockito.verify(messageAction).queue()

        val embed = embedCaptor.value
        assertTrue(embed.description?.contains("Results are final.") == true)
        val picksField = embed.fields.first { it.name == "Picks" }
        val value2 = picksField.value ?: ""
        val lines = value2.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("Correct?: ✅") || lines[0].contains("Correct?: ❌") )
        assertTrue(lines[1].contains("Correct?: ✅") || lines[1].contains("Correct?: ❌") )
        assertNotNull(embed.footer)
        assertTrue(embed.footer?.text?.contains("score: ") == true)
        // expected points = 10 for 1 correct out of 2
        assertTrue(embed.footer?.text?.contains("10 pts (1/2 correct)") == true)
    }

    @Test
    fun `exception during game lookup sends error message`() {
        val week = 4
        val (event, replyAction, hook) = mockEvent(userId = 1L, mention = "<@1>")
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val p1 = pick(100L, 1L, week, processed = false, correct = false, winningTeamId = 1L)
        `when`(pickRepo.findByUserIdAndSeasonAndWeek(1L, LocalDate.now().year, week)).thenReturn(listOf(p1))
        `when`(gameRepo.findById(100L)).thenThrow(RuntimeException("boom"))

        cmd.handleEvent(event, week)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("An error occurred fetching picks.", captor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute uses week option and builds correct title`() {
        val (event, replyAction, hook) = mockEvent(userId = 8L, mention = "<@8>")
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        val weekOpt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(weekOpt.asInt).thenReturn(7)
        `when`(event.getOption("week")).thenReturn(weekOpt)

        val p = pick(5L, 8L, week = 7, processed = false, correct = false, winningTeamId = 11L)
        `when`(pickRepo.findByUserIdAndSeasonAndWeek(8L, LocalDate.now().year, 7)).thenReturn(listOf(p))
        val g = game(5L, home = "Home", homeId = 10L, away = "Away", awayId = 11L)
        `when`(gameRepo.findById(5L)).thenReturn(Optional.of(g))

        // Reply action for deferReply() (not ephemeral in execute; the handleEvent uses deferReply(true), but we only need to satisfy mocks)
        val replyActionDefault = Mockito.mock(ReplyCallbackAction::class.java)
        `when`(event.deferReply()).thenReturn(replyActionDefault)

        cmd.execute(event)

        // handleEvent inside will call deferReply(true) on the same event; set up to return a replyAction
        Mockito.verify(replyAction).queue()
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        assertEquals("NFL Pick'em — Week 7 Picks", embedCaptor.value.title)
        Mockito.verify(messageAction).queue()
    }
}
