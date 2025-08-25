package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.lang.reflect.Field
import java.net.URI

class OsrsStatsTest {

    private fun setPrivateField(target: Any, fieldName: String, value: Any) {
        val field: Field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun sampleHiscoreLines(): String {
        // rank,level,xp — 24 skills
        val lines = mutableListOf<String>()
        // Overall
        lines += listOf(
            "1,2277,200000000",
            "2,99,13034431", // Attack
            "3,99,13034431", // Defence
            "4,99,13034431", // Strength
            "5,99,13034431", // HP
            "6,99,13034431", // Ranged
            "7,99,13034431", // Prayer
            "8,99,13034431", // Magic
            "9,99,13034431", // Cooking
            "10,99,13034431", // Woodcutting
            "11,99,13034431", // Fletching
            "12,99,13034431", // Fishing
            "13,99,13034431", // Firemaking
            "14,99,13034431", // Crafting
            "15,99,13034431", // Smithing
            "16,99,13034431", // Mining
            "17,99,13034431", // Herblore
            "18,99,13034431", // Agility
            "19,99,13034431", // Thieving
            "20,99,13034431", // Slayer
            "21,99,13034431", // Farming
            "22,99,13034431", // Runecrafting
            "23,99,13034431", // Hunter
            "24,99,13034431"  // Construction
        )
        // Join with newlines and an extra newline at the end to emulate real response
        return lines.joinToString("\n", postfix = "\n")
    }

    @Test
    fun `metadata and options are correct`() {
        val cmd = OsrsStats()
        assertEquals("osrs", cmd.getCommandKey())
        assertEquals("Get the high scores and XP for a OSRS player", cmd.getDescription())
        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(1, opts.size)
        assertEquals("player", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
    }

    @Test
    fun `execute sends error when highscores not found`() {
        val cmd = OsrsStats()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Zezima")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("player")).thenReturn(opt)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        // Mock RestTemplate to throw
        val rt = Mockito.mock(RestTemplate::class.java)
        `when`(rt.getForObject(Mockito.any(URI::class.java), Mockito.eq(String::class.java))).thenThrow(RestClientException("not found"))
        setPrivateField(cmd, "client", rt)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("High scores were not found for player: Zezima", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute formats skills table and sends message`() {
        val cmd = OsrsStats()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("MyPlayer")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("player")).thenReturn(opt)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val rt = Mockito.mock(RestTemplate::class.java)
        `when`(rt.getForObject(Mockito.any(URI::class.java), Mockito.eq(String::class.java))).thenReturn(sampleHiscoreLines())
        setPrivateField(cmd, "client", rt)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        Mockito.verify(messageAction).queue()

        val msg = msgCaptor.value
        assertTrue(msg.startsWith("## ⚔\uFE0F OSRS High Scores for MyPlayer"))
        assertTrue(msg.contains("```prolog"))
        // Check a couple of specific lines formatted with commas and alignment
        assertTrue(msg.contains("Overall"))
        // XP 200000000 should be formatted with commas
        assertTrue(msg.contains("200,000,000"))
        // Ensure it ends the code block
        assertTrue(msg.trim().endsWith("```"))
    }

    @Test
    fun `execute handles partial response safely`() {
        val cmd = OsrsStats()
        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("Shorty")

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(event.getOption("player")).thenReturn(opt)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        val rt = Mockito.mock(RestTemplate::class.java)
        val partial = listOf(
            "1,10,1000", // Overall
            "2,5,500"    // Attack
        ).joinToString("\n")
        `when`(rt.getForObject(Mockito.any(URI::class.java), Mockito.eq(String::class.java))).thenReturn(partial)
        setPrivateField(cmd, "client", rt)

        cmd.execute(event)

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        val msg = msgCaptor.value
        // Should contain two lines for Overall and Attack and still be a codeblock
        assertTrue(msg.contains("Overall"))
        assertTrue(msg.contains("Attack"))
        assertTrue(msg.contains("```prolog"))
        assertTrue(msg.trim().endsWith("```"))
    }
}
