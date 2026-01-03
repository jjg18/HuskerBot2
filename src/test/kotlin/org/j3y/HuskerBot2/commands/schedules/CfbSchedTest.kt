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
import org.j3y.HuskerBot2.util.SeasonResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class CfbSchedTest {

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
        val cmd = CfbSched()
        assertEquals("cfb", cmd.getCommandKey())
        assertEquals("Get the CFB schedules for a given week and/or league", cmd.getDescription())
        assertTrue(cmd.isSubcommand())

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(2, opts.size)
        val leagueOpt = opts.first { it.name == "league" }
        val weekOpt = opts.first { it.name == "week" }

        assertEquals(OptionType.STRING, leagueOpt.type)
        assertFalse(leagueOpt.isRequired)
        // Choices should include known leagues
        val choiceValues = leagueOpt.choices.map { it.asString }
        assertTrue(choiceValues.containsAll(listOf("top25", "acc", "american", "big12", "big10", "sec", "pac12", "mac", "independent")))

        assertEquals(OptionType.INTEGER, weekOpt.type)
        assertFalse(weekOpt.isRequired)
    }

    @Test
    fun `invalid league sends error message`() {
        val cmd = CfbSched()
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)

        val leagueOpt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(leagueOpt.asString).thenReturn("unknown")
        `when`(event.getOption("league")).thenReturn(leagueOpt)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("That league was not recognized.", captor.value)
        Mockito.verify(msgAction).queue()
        Mockito.verifyNoInteractions(espn)
    }

    @Test
    fun `invalid week below range defaults to bowls`() {
        val cmd = CfbSched()
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        val weekOpt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(weekOpt.asInt).thenReturn(0) // invalid
        `when`(event.getOption("week")).thenReturn(weekOpt)
        // no league provided -> default top25 = valid

        val apiJson = Mockito.mock(JsonNode::class.java)
        `when`(espn.getCfbScoreboard(0, -1)).thenReturn(apiJson)
        `when`(espn.buildEventEmbed(apiJson)).thenReturn(listOf(Mockito.mock(MessageEmbed::class.java)))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        Mockito.verify(espn).getCfbScoreboard(0, -1)
    }

    @Test
    fun `invalid week above range defaults to bowls`() {
        val cmd = CfbSched()
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        val weekOpt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(weekOpt.asInt).thenReturn(99) // invalid
        `when`(event.getOption("week")).thenReturn(weekOpt)

        val apiJson = Mockito.mock(JsonNode::class.java)
        `when`(espn.getCfbScoreboard(0, -1)).thenReturn(apiJson)
        `when`(espn.buildEventEmbed(apiJson)).thenReturn(listOf(Mockito.mock(MessageEmbed::class.java)))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        Mockito.verify(espn).getCfbScoreboard(0, -1)
    }

    @Test
    fun `successful execute with explicit league and week builds embeds and heading`() {
        val cmd = CfbSched()
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        val leagueOpt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(leagueOpt.asString).thenReturn("big10")
        `when`(event.getOption("league")).thenReturn(leagueOpt)

        val weekOpt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(weekOpt.asInt).thenReturn(3)
        `when`(event.getOption("week")).thenReturn(weekOpt)

        val apiJson = Mockito.mock(JsonNode::class.java)
        val embeds = listOf(Mockito.mock(MessageEmbed::class.java))
        `when`(espn.getCfbScoreboard(5, 3)).thenReturn(apiJson)
        `when`(espn.buildEventEmbed(apiJson)).thenReturn(embeds)

        cmd.execute(event)

        // verify deferReply
        Mockito.verify(replyAction).queue()
        // verify espn calls
        Mockito.verify(espn).getCfbScoreboard(5, 3)
        Mockito.verify(espn).buildEventEmbed(apiJson)

        // verify heading and embeds
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        val heading = msgCaptor.value
        assertTrue(heading.contains("CFB Schedule  for Big 10 in Week 3"))
        Mockito.verify(msgAction).addEmbeds(embeds)
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `missing week uses resolved current week within valid range`() {
        val cmd = CfbSched()
        val espn = Mockito.mock(EspnService::class.java)
        cmd.espnService = espn

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // no week option provided; default league also not provided -> top25 -> 0
        val apiJson = Mockito.mock(JsonNode::class.java)
        val embeds = listOf(Mockito.mock(MessageEmbed::class.java))

        val leagueCaptor = ArgumentCaptor.forClass(Int::class.java)
        val weekCaptor = ArgumentCaptor.forClass(Int::class.java)

        `when`(espn.getCfbScoreboard(Mockito.anyInt(), Mockito.anyInt())).thenReturn(apiJson)
        `when`(espn.buildEventEmbed(apiJson)).thenReturn(embeds)

        Mockito.mockStatic(SeasonResolver::class.java).use { mockedResolver ->
            mockedResolver.`when`<Int> { SeasonResolver.currentCfbWeek() }.thenReturn(3)

            cmd.execute(event)

            Mockito.verify(replyAction).queue()
            Mockito.verify(espn).getCfbScoreboard(leagueCaptor.capture(), weekCaptor.capture())
            assertEquals(0, leagueCaptor.value) // top25 default
            assertEquals(3, weekCaptor.value)
            Mockito.verify(msgAction).addEmbeds(embeds)
            Mockito.verify(msgAction).queue()
        }
    }
}
