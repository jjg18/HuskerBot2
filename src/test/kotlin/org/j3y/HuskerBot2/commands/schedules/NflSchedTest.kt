package org.j3y.HuskerBot2.commands.schedules

import com.fasterxml.jackson.databind.JsonNode
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.service.EspnService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class NflSchedTest {

    private fun setupEvent(): Triple<SlashCommandInteractionEvent, ReplyCallbackAction, InteractionHook> {
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        return Triple(event, replyAction, hook)
    }

    @Test
    fun `metadata and options are correct`() {
        val cmd = NflSched()
        assertEquals("nfl", cmd.getCommandKey())
        assertEquals("Get the NFL schedules (or scores) for a given week", cmd.getDescription())
        assertTrue(cmd.isSubcommand())

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(1, opts.size)
        val weekOpt = opts.first()
        assertEquals("week", weekOpt.name)
        assertEquals(OptionType.INTEGER, weekOpt.type)
        assertFalse(weekOpt.isRequired)
    }

    @Test
    fun `successful execute with explicit week builds embeds and heading`() {
        val cmd = NflSched()
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        val weekOpt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(weekOpt.asInt).thenReturn(5)
        `when`(event.getOption("week")).thenReturn(weekOpt)

        val apiJson = Mockito.mock(JsonNode::class.java)
        val embeds = listOf(Mockito.mock(MessageEmbed::class.java))
        `when`(espn.getNflScoreboard(5)).thenReturn(apiJson)
        `when`(espn.buildEventEmbed(apiJson)).thenReturn(embeds)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        Mockito.verify(espn).getNflScoreboard(5)
        Mockito.verify(espn).buildEventEmbed(apiJson)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        val heading = msgCaptor.value
        assertTrue(heading.contains("NFL Schedule for Week 5"))
        Mockito.verify(msgAction).addEmbeds(embeds)
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `missing week uses resolved current week and sends embeds`() {
        val cmd = NflSched()
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // No week option provided; capture the week passed to espn
        val apiJson = Mockito.mock(JsonNode::class.java)
        val embeds = listOf(Mockito.mock(MessageEmbed::class.java))
        val weekCaptor = ArgumentCaptor.forClass(Int::class.java)
        `when`(espn.getNflScoreboard(Mockito.anyInt())).thenReturn(apiJson)
        `when`(espn.buildEventEmbed(apiJson)).thenReturn(embeds)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        Mockito.verify(espn).getNflScoreboard(weekCaptor.capture())
        val resolvedWeek = weekCaptor.value
        // WeekResolver.currentNflWeek() returns between 1 and 24 inclusive based on configured weeks
        assertTrue(resolvedWeek in 1..24, "Resolved NFL week should be within 1..24 but was $resolvedWeek")

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        val heading = msgCaptor.value
        assertTrue(heading.contains("NFL Schedule for Week "))
        // Ensure the same week number appears in heading
        assertTrue(heading.contains("Week $resolvedWeek"))

        Mockito.verify(msgAction).addEmbeds(embeds)
        Mockito.verify(msgAction).queue()
    }
}
