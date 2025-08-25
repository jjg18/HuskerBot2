package org.j3y.HuskerBot2.automation.pickem.nfl

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.restaction.MessageEditAction
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.commands.pickem.NflPickemShow
import org.j3y.HuskerBot2.model.NflGameEntity
import org.j3y.HuskerBot2.model.NflPick
import org.j3y.HuskerBot2.repository.NflGameRepo
import org.j3y.HuskerBot2.repository.NflPickRepo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.util.*

class NflPickemListenerTest {

    private lateinit var listener: NflPickemListener
    private lateinit var gameRepo: NflGameRepo
    private lateinit var pickRepo: NflPickRepo
    private lateinit var pickemShow: NflPickemShow

    @BeforeEach
    fun setup() {
        listener = NflPickemListener()
        gameRepo = Mockito.mock(NflGameRepo::class.java)
        pickRepo = Mockito.mock(NflPickRepo::class.java)
        pickemShow = Mockito.mock(NflPickemShow::class.java)
        listener.nflGameRepo = gameRepo
        listener.nflPickRepo = pickRepo
        listener.nflPickemShow = pickemShow
    }

    private data class MockEventBundle(
        val event: ButtonInteractionEvent,
        val replyAction: ReplyCallbackAction,
        val editAction: MessageEditAction,
        val deferEditAction: MessageEditCallbackAction,
        val message: Message,
        val user: User,
    )

    private fun mockEventWithId(componentId: String, userId: Long = 42L): MockEventBundle {
        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val editAction = Mockito.mock(MessageEditAction::class.java)
        val deferEditAction = Mockito.mock(MessageEditCallbackAction::class.java)
        val message = Mockito.mock(Message::class.java)
        val user = Mockito.mock(User::class.java)

        `when`(event.componentId).thenReturn(componentId)
        `when`(event.user).thenReturn(user)
        `when`(user.idLong).thenReturn(userId)

        // reply(String) -> ReplyCallbackAction (and allow setEphemeral(true))
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(replyAction)
        // message edit components
        `when`(event.message).thenReturn(message)
        `when`(message.editMessageComponents(Mockito.any(ActionRow::class.java))).thenReturn(editAction)
        // queue on edit and reply actions
        `when`(editAction.queue()).then { null }
        `when`(replyAction.queue()).then { null }
        // defer edit
        `when`(event.deferEdit()).thenReturn(deferEditAction)
        `when`(deferEditAction.queue()).then { null }

        return MockEventBundle(event, replyAction, editAction, deferEditAction, message, user)
    }

    private fun game(
        id: Long,
        home: String,
        homeId: Long,
        away: String,
        awayId: Long,
        ts: Instant = Instant.now().plusSeconds(60)
    ) = NflGameEntity(
        id = id,
        homeTeam = home,
        homeTeamId = homeId,
        awayTeam = away,
        awayTeamId = awayId,
        dateTime = ts,
        season = LocalDate.now().year,
        week = 3
    )

    @Test
    fun `ignores non-pickem buttons`() {
        val b = mockEventWithId("unrelated|foo|bar")

        listener.onButtonInteraction(b.event)

        Mockito.verifyNoInteractions(gameRepo)
        Mockito.verifyNoInteractions(pickRepo)
        Mockito.verify(b.replyAction, Mockito.never()).queue()
    }


    @Test
    fun `ignores malformed ids with too few parts`() {
        val b = mockEventWithId("nflpickem|onlyonepart")

        listener.onButtonInteraction(b.event)

        Mockito.verifyNoInteractions(gameRepo)
        Mockito.verifyNoInteractions(pickRepo)
    }

    @Test
    fun `replies when game not found`() {
        val b = mockEventWithId("nflpickem|123|456", userId = 99L)
        `when`(gameRepo.findById(123L)).thenReturn(Optional.empty())

        listener.onButtonInteraction(b.event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(b.replyAction).setEphemeral(true)
        Mockito.verify(b.event).reply(captor.capture())
        Mockito.verify(b.replyAction).queue()
        org.junit.jupiter.api.Assertions.assertEquals("Sorry, we don't have any data for game 123.", captor.value)
    }

    @Test
    fun `replies when game already started`() {
        val b = mockEventWithId("nflpickem|5|10", userId = 7L)
        val pastGame = game(5L, home = "H", homeId = 10L, away = "A", awayId = 11L, ts = Instant.now().minusSeconds(60))
        `when`(gameRepo.findById(5L)).thenReturn(Optional.of(pastGame))

        listener.onButtonInteraction(b.event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(b.replyAction).setEphemeral(true)
        Mockito.verify(b.event).reply(captor.capture())
        org.junit.jupiter.api.Assertions.assertEquals("You cannot make a pick after the game has already started!", captor.value)
        Mockito.verify(b.replyAction).queue()
    }

    @Test
    fun `successful pick saves and updates message components with counts`() {
        val b = mockEventWithId("nflpickem|200|300", userId = 55L)
        val g = game(200L, home = "HomeX", homeId = 400L, away = "AwayX", awayId = 300L, ts = Instant.now().plusSeconds(3600))
        `when`(gameRepo.findById(200L)).thenReturn(Optional.of(g))

        // Existing pick for another user: away team chosen
        val existing = NflPick(gameId = 200L, userId = 99L, season = g.season, week = g.week, winningTeamId = 300L)
        `when`(pickRepo.findByGameId(200L)).thenReturn(listOf(existing))
        // No existing pick for current user
        `when`(pickRepo.findByGameIdAndUserId(200L, 55L)).thenReturn(null)

        listener.onButtonInteraction(b.event)

        // Verify save was called with expected fields
        val pickCaptor = ArgumentCaptor.forClass(NflPick::class.java)
        Mockito.verify(pickRepo).save(pickCaptor.capture())
        val saved = pickCaptor.value
        org.junit.jupiter.api.Assertions.assertEquals(200L, saved.gameId)
        org.junit.jupiter.api.Assertions.assertEquals(55L, saved.userId)
        org.junit.jupiter.api.Assertions.assertEquals(g.season, saved.season)
        org.junit.jupiter.api.Assertions.assertEquals(g.week, saved.week)
        org.junit.jupiter.api.Assertions.assertEquals(300L, saved.winningTeamId) // selected team from componentId

        // Verify message components updated with counts: away 2 (existing + new), home 0
        val rowCaptor = ArgumentCaptor.forClass(ActionRow::class.java)
        Mockito.verify(b.message).editMessageComponents(rowCaptor.capture())
        val row = rowCaptor.value
        val buttons = row.components.filterIsInstance<Button>()
        org.junit.jupiter.api.Assertions.assertEquals(2, buttons.size)
        // Labels should contain team names and counts in parentheses
        org.junit.jupiter.api.Assertions.assertTrue(buttons[0].label?.contains("AwayX") == true)
        org.junit.jupiter.api.Assertions.assertTrue(buttons[1].label?.contains("HomeX") == true)
        org.junit.jupiter.api.Assertions.assertTrue(buttons[0].label?.contains("(") == true && buttons[0].label?.contains(")") == true)
        org.junit.jupiter.api.Assertions.assertTrue(buttons[1].label?.contains("(") == true && buttons[1].label?.contains(")") == true)
        // Ensure both buttons created with correct ids
        org.junit.jupiter.api.Assertions.assertEquals("nflpickem|200|300", buttons[0].id)
        org.junit.jupiter.api.Assertions.assertEquals("nflpickem|200|400", buttons[1].id)

        // Verify defer edit called
        Mockito.verify(b.deferEditAction).queue()
    }

    @Test
    fun `uses existing pick entity when present and updates winning team`() {
        val b = mockEventWithId("nflpickem|77|888", userId = 7L)
        val g = game(77L, home = "H", homeId = 999L, away = "A", awayId = 888L)
        `when`(gameRepo.findById(77L)).thenReturn(Optional.of(g))

        val existingForUser = NflPick(gameId = 77L, userId = 7L, season = g.season, week = g.week, winningTeamId = 999L)
        `when`(pickRepo.findByGameIdAndUserId(77L, 7L)).thenReturn(existingForUser)
        `when`(pickRepo.findByGameId(77L)).thenReturn(listOf(existingForUser))

        listener.onButtonInteraction(b.event)

        // Should update winningTeamId to 888
        val captor = ArgumentCaptor.forClass(NflPick::class.java)
        Mockito.verify(pickRepo).save(captor.capture())
        org.junit.jupiter.api.Assertions.assertEquals(888L, captor.value.winningTeamId)
    }

    @Test
    fun `handles unexpected exception with generic error reply`() {
        val b = mockEventWithId("nflpickem|1|2")
        `when`(gameRepo.findById(1L)).thenThrow(RuntimeException("boom"))

        listener.onButtonInteraction(b.event)

        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(b.event).reply(captor.capture())
        org.junit.jupiter.api.Assertions.assertEquals("An error occurred processing your pick.", captor.value)
        Mockito.verify(b.replyAction).setEphemeral(true)
        Mockito.verify(b.replyAction).queue()
    }
}
