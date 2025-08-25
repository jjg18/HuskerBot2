package org.j3y.HuskerBot2.commands.schedules

import com.fasterxml.jackson.databind.ObjectMapper
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.service.EspnService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class CountdownTest {

    private fun setupEvent(): Triple<SlashCommandInteractionEvent, ReplyCallbackAction, InteractionHook> {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        return Triple(event, replyAction, hook)
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
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        // Capture embeds being sent
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(hook.sendMessageEmbeds(embedCaptor.capture())).thenReturn(msgAction)

        // Prepare a future date so the duration is positive and deterministic-enough for format
        val futureDate = OffsetDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val json = """
            {
              "team": {
                "nextEvent": [
                  {
                    "name": "Nebraska vs. Opponent",
                    "date": "$futureDate"
                  }
                ]
              }
            }
        """.trimIndent()
        val apiJson = ObjectMapper().readTree(json)
        `when`(espn.getTeamData("nebraska")).thenReturn(apiJson)

        // Execute
        cmd.execute(event)

        // Verify interactions
        Mockito.verify(replyAction).queue()
        Mockito.verify(espn).getTeamData("nebraska")
        Mockito.verify(hook).sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))
        Mockito.verify(msgAction).queue()

        // Verify embed content
        val embed = embedCaptor.value
        assertNotNull(embed)
        assertEquals("Countdown to Nebraska vs. Opponent", embed.title)
        val desc = embed.description
        assertNotNull(desc)
        // Expect description like: There are X days, Y hours, Z minutes and W seconds til gameday!
        val regex = Regex("There are \\d+ days, \\d+ hours, \\d+ minutes and \\d+ seconds til gameday!")
        assertTrue(regex.matches(desc!!), "Description did not match expected countdown format: '$desc'")
    }
}
