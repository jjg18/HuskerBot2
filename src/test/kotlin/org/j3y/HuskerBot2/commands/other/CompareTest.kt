package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.service.CfbMatchupService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class CompareTest {

    private fun matchup(team1: String = "Nebraska", team2: String = "Iowa", games: List<CfbMatchupService.GameResult> = emptyList(), t1Wins: Int = 0, t2Wins: Int = 0, ties: Int = 0): CfbMatchupService.TeamMatchupData {
        return CfbMatchupService.TeamMatchupData(team1, t1Wins, team2, t2Wins, ties, games)
    }

    private fun game(
        season: Int,
        seasonType: String = "regular",
        homeTeam: String,
        homeScore: Int? = null,
        awayTeam: String,
        awayScore: Int? = null,
        winner: String? = null,
        neutral: Boolean = false,
        venue: String? = null
    ): CfbMatchupService.GameResult = CfbMatchupService.GameResult(
        season = season,
        week = null,
        seasonType = seasonType,
        date = "",
        neutralSite = neutral,
        venue = venue,
        homeTeam = homeTeam,
        homeScore = homeScore,
        awayTeam = awayTeam,
        awayScore = awayScore,
        winner = winner
    )

    @Test
    fun `metadata and options are correct`() {
        val svc = Mockito.mock(CfbMatchupService::class.java)
        val cmd = Compare(svc)
        assertEquals("compare", cmd.getCommandKey())
        assertEquals("Compare two CFB teams head-to-head history", cmd.getDescription())
        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(2, opts.size)
        assertEquals("team1", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
        assertEquals("team2", opts[1].name)
        assertEquals(OptionType.STRING, opts[1].type)
        assertTrue(opts[1].isRequired)
    }

    @Test
    fun `execute replies when options missing`() {
        val svc = Mockito.mock(CfbMatchupService::class.java)
        val cmd = Compare(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // simulate missing team2
        val optTeam1 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(optTeam1.asString).thenReturn("Nebraska")
        `when`(event.getOption("team1")).thenReturn(optTeam1)
        `when`(event.getOption("team2")).thenReturn(null)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Please provide both team names.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute replies when service returns null`() {
        val svc = Mockito.mock(CfbMatchupService::class.java)
        val cmd = Compare(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt1 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val opt2 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt1.asString).thenReturn("Nebraska")
        `when`(opt2.asString).thenReturn("Iowa")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("team1")).thenReturn(opt1)
        `when`(event.getOption("team2")).thenReturn(opt2)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)
        `when`(svc.getTeamMatchup("Nebraska", "Iowa")).thenReturn(null)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("No matchup data found for Nebraska vs Iowa. Please check team names.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute builds embed for small list without buttons`() {
        val svc = Mockito.mock(CfbMatchupService::class.java)
        val cmd = Compare(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)

        `when`(event.user).thenReturn(user)
        `when`(user.id).thenReturn("u1")

        val g1 = game(2022, homeTeam = "Nebraska", homeScore = 21, awayTeam = "Iowa", awayScore = 17)
        val g2 = game(2021, seasonType = "postseason", homeTeam = "Iowa", homeScore = 28, awayTeam = "Nebraska", awayScore = 27, neutral = true, venue = "Lucas Oil")
        val data = matchup(t1Wins = 1, t2Wins = 1, ties = 0, games = listOf(g1, g2))
        `when`(svc.getTeamMatchup("Nebraska", "Iowa")).thenReturn(data)

        val opt1 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val opt2 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt1.asString).thenReturn("Nebraska")
        `when`(opt2.asString).thenReturn("Iowa")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("team1")).thenReturn(opt1)
        `when`(event.getOption("team2")).thenReturn(opt2)
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        Mockito.verify(messageAction).queue()

        val embed = embedCaptor.value
        assertEquals("🏈 Nebraska vs Iowa", embed.title)
        assertTrue(embed.description?.contains("Series Record:") == true)
        val fields = embed.fields
        assertTrue(fields.any { it.name?.startsWith("Recent Games") == true })
        assertTrue(fields.any { it.name == "Last Meeting" })

        // Ensure no buttons added (we can only ensure we did not call setActionRow)
        Mockito.verify(messageAction, Mockito.never()).setActionRow(Mockito.anyList<Any>() as MutableList<Button>)
    }

    @Test
    fun `execute with many games adds pagination buttons and disables start buttons on first page`() {
        val svc = Mockito.mock(CfbMatchupService::class.java)
        val cmd = Compare(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)

        `when`(event.user).thenReturn(user)
        `when`(user.id).thenReturn("u1")

        val games = (1..8).map { idx ->
            game(2015 + idx, homeTeam = "Nebraska", homeScore = 20 + idx, awayTeam = "Iowa", awayScore = 10 + idx)
        }
        val data = matchup(games = games)
        `when`(svc.getTeamMatchup("Nebraska", "Iowa")).thenReturn(data)

        val opt1 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val opt2 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt1.asString).thenReturn("Nebraska")
        `when`(opt2.asString).thenReturn("Iowa")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("team1")).thenReturn(opt1)
        `when`(event.getOption("team2")).thenReturn(opt2)
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        // Capture buttons from setActionRow
        val buttonsCaptor = ArgumentCaptor.forClass(MutableList::class.java as Class<MutableList<Button>>)
        `when`(messageAction.setActionRow(Mockito.anyList<Button>())).thenAnswer { inv ->
            // pass-through mock behavior returning same action
            messageAction
        }

        cmd.execute(event)

        // Ensure setActionRow called and inspect buttons argument
        Mockito.verify(messageAction).setActionRow(buttonsCaptor.capture())
        Mockito.verify(messageAction).queue()

        val buttons: List<Button> = buttonsCaptor.value
        assertEquals(4, buttons.size)
        // first two should be disabled on first page
        assertTrue(buttons[0].isDisabled)
        assertTrue(buttons[1].isDisabled)
        assertFalse(buttons[2].isDisabled)
        assertFalse(buttons[3].isDisabled)

        // Verify component ids carry state including page 0 and user id
        assertTrue(buttons[0].id!!.startsWith("compare|first|Nebraska|Iowa|0|u1"))
        assertTrue(buttons[2].id!!.startsWith("compare|next|Nebraska|Iowa|0|u1"))
    }

    @Test
    fun `buttonEvent next and last update page and buttons`() {
        val svc = Mockito.mock(CfbMatchupService::class.java)
        val cmd = Compare(svc)
        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        val messageAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction::class.java)

        val games = (1..11).map { idx ->
            game(2010 + idx, homeTeam = "Nebraska", homeScore = 30, awayTeam = "Iowa", awayScore = 20)
        }
        val data = matchup(games = games)
        `when`(svc.getTeamMatchup("Nebraska", "Iowa")).thenReturn(data)

        // Simulate component id for next from page 0
        `when`(event.componentId).thenReturn("compare|next|Nebraska|Iowa|0|u1")
        `when`(event.editMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)
        `when`(messageAction.setActionRow(Mockito.anyList<Button>())).thenReturn(messageAction)

        cmd.buttonEvent(event)

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(event).editMessageEmbeds(embedCaptor.capture())
        Mockito.verify(messageAction).setActionRow(Mockito.anyList())
        Mockito.verify(messageAction).queue()

        // Now simulate clicking last from page 1 (mid)
        `when`(event.componentId).thenReturn("compare|last|Nebraska|Iowa|1|u1")
        cmd.buttonEvent(event)
        Mockito.verify(event, Mockito.times(2)).editMessageEmbeds(Mockito.any(MessageEmbed::class.java))
    }

    @Test
    fun `execute catches exception and replies error`() {
        val svc = Mockito.mock(CfbMatchupService::class.java)
        val cmd = Compare(svc)
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt1 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        val opt2 = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt1.asString).thenReturn("Nebraska")
        `when`(opt2.asString).thenReturn("Iowa")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("team1")).thenReturn(opt1)
        `when`(event.getOption("team2")).thenReturn(opt2)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        `when`(svc.getTeamMatchup("Nebraska", "Iowa")).thenThrow(RuntimeException("boom"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Sorry, there was an error comparing the teams.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }
}
