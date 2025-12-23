package org.j3y.HuskerBot2.commands.schedules

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.internal.entities.GuildImpl
import net.dv8tion.jda.internal.entities.MemberImpl
import net.dv8tion.jda.internal.entities.UserImpl
import org.j3y.HuskerBot2.model.ScheduleEntity
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Instant

class CountdownTest {

    private fun setupEvent(): Triple<SlashCommandInteractionEvent, ReplyCallbackAction, InteractionHook> {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        val member = Mockito.mock(Member::class.java)
        val user = Mockito.mock(UserImpl::class.java)
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.member).thenReturn(member)
        `when`(event.user).thenReturn(user)
        return Triple(event, replyAction, hook)
    }

    // Kotlin-safe Mockito any() helper for non-null Instant parameters
    private fun anyInstant(): Instant {
        Mockito.any(Instant::class.java)
        return Instant.now()
    }

    @Test
    fun `metadata is correct`() {
        val cmd = Countdown()
        assertEquals("countdown", cmd.getCommandKey())
        assertEquals("Get the countdown until the next husker game", cmd.getDescription())
        assertTrue(cmd.getOptions().isEmpty())
        assertFalse(cmd.isSubcommand())
    }

    @Test
    fun `execute builds embed and sends countdown`() {
        val cmd = Countdown()
        val scheduleRepo = Mockito.mock(ScheduleRepo::class.java)
        cmd.scheduleRepo = scheduleRepo

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        // Capture embeds being sent
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(hook.sendMessageEmbeds(embedCaptor.capture())).thenReturn(msgAction)

        // Prepare a future instant and schedule entity
        val futureInstant = Instant.now().plusSeconds(24 * 60 * 60) // +1 day
        val sched = ScheduleEntity(
            id = 1L,
            opponent = "Opponent",
            dateTime = futureInstant
        )
        `when`(scheduleRepo.findFirstByDateTimeAfterOrderByDateTimeAsc(anyInstant())).thenReturn(sched)

        // Execute
        cmd.execute(event)

        // Verify interactions
        Mockito.verify(replyAction).queue()
        Mockito.verify(hook).sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))
        Mockito.verify(msgAction).queue()

        // Verify embed content
        val embed = embedCaptor.value
        assertNotNull(embed)
        assertEquals("Countdown to Nebraska vs. Opponent", embed.title)
        val desc = embed.description
        assertNotNull(desc)
        // Expect description to include countdown line and a kickoff formatted date-time line.
        val firstLineRegex = Regex("There are \\d+ days, \\d+ hours, \\d+ minutes and \\d+ seconds until game time!")
        val parts = desc!!.split("\n")
        assertTrue(parts.isNotEmpty(), "Description should not be empty")
        assertTrue(firstLineRegex.matches(parts[0]), "First line did not match expected countdown format: '${parts[0]}'")
    }
}
