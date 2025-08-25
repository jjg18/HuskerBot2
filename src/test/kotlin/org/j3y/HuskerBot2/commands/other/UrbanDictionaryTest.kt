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
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.service.UrbanDictionaryService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class UrbanDictionaryTest {

    private fun def(
        word: String = "foo",
        definition: String = "a definition",
        example: String? = "an example",
        author: String? = "author",
        permalink: String? = "https://urbandictionary.com/define.php?term=foo"
    ) = UrbanDictionaryService.UrbanDefinition(word, definition, example, author, permalink)

    @Test
    fun `metadata and options are correct`() {
        val cmd = UrbanDictionary()
        assertEquals("ud", cmd.getCommandKey())
        assertEquals("Search Urban Dictionary for a term", cmd.getDescription())

        val opts: List<OptionData> = cmd.getOptions()
        assertEquals(1, opts.size)
        assertEquals("term", opts[0].name)
        assertEquals(OptionType.STRING, opts[0].type)
        assertTrue(opts[0].isRequired)
    }

    @Test
    fun `execute replies when term missing`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        // term missing
        `when`(event.getOption("term")).thenReturn(null)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Please provide a term to search.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
        Mockito.verifyNoInteractions(svc)
    }

    @Test
    fun `execute replies no results when service returns empty`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("foo")
        `when`(event.getOption("term")).thenReturn(opt)

        `when`(svc.defineAll("foo")).thenReturn(emptyList())
        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("No results found for 'foo'.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute success sends embed and pagination buttons`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)

        `when`(event.user).thenReturn(user)
        `when`(user.id).thenReturn("u1")

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("foo")
        `when`(event.getOption("term")).thenReturn(opt)

        val defs = listOf(
            def(word = "foo", definition = "def1", example = "ex1", author = "a1"),
            def(word = "foo", definition = "def2", example = "ex2", author = "a2")
        )
        `when`(svc.defineAll("foo")).thenReturn(defs)

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)

        // Capture buttons passed to setActionRow
        val buttonsCaptor = ArgumentCaptor.forClass(MutableList::class.java as Class<MutableList<Button>>)
        `when`(messageAction.setActionRow(Mockito.anyList<Button>())).thenAnswer { messageAction }

        cmd.execute(event)

        // Verify embed sent
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        val embed = embedCaptor.value
        assertEquals("Urban Dictionary: foo", embed.title)
        assertEquals("Result 1 of 2", embed.footer?.text)
        // Definition field exists
        val defField = embed.fields.firstOrNull { it.name == "Definition" }
        assertNotNull(defField)
        assertEquals("def1", defField!!.value)
        // Example field exists
        val exField = embed.fields.firstOrNull { it.name == "Example" }
        assertNotNull(exField)
        assertEquals("ex1", exField!!.value)
        // Author field
        val authorField = embed.fields.firstOrNull { it.name == "Author" }
        assertNotNull(authorField)
        assertEquals("a1", authorField!!.value)

        // Verify buttons
        Mockito.verify(messageAction).setActionRow(buttonsCaptor.capture())
        val buttons: List<Button> = buttonsCaptor.value
        assertEquals(4, buttons.size)
        assertTrue(buttons[0].isDisabled) // first disabled at index 0
        assertTrue(buttons[1].isDisabled) // prev disabled at index 0
        assertFalse(buttons[2].isDisabled) // next enabled
        assertFalse(buttons[3].isDisabled) // last enabled
        assertEquals("ud|first|foo|0|2|u1", buttons[0].id)
        assertEquals("ud|next|foo|0|2|u1", buttons[2].id)

        Mockito.verify(replyAction).queue()
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `execute catches exception and replies error`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("foo")
        `when`(event.getOption("term")).thenReturn(opt)

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(messageAction)

        `when`(svc.defineAll("foo")).thenThrow(RuntimeException("boom"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(msgCaptor.capture())
        assertEquals("Sorry, there was an error searching Urban Dictionary.", msgCaptor.value)
        Mockito.verify(messageAction).queue()
    }

    @Test
    fun `buttonEvent next and last update page and buttons`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        val editAction = Mockito.mock(MessageEditCallbackAction::class.java)

        val defs = listOf(
            def(definition = "d1", example = "e1"),
            def(definition = "d2", example = "e2")
        )
        `when`(svc.defineAll("foo")).thenReturn(defs)

        // start at index 0, click next
        `when`(event.componentId).thenReturn("ud|next|foo|0|2|u1")
        `when`(event.editMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(editAction)
        `when`(editAction.setActionRow(Mockito.anyList<Button>())).thenReturn(editAction)

        cmd.buttonEvent(event)

        Mockito.verify(event).editMessageEmbeds(Mockito.any(MessageEmbed::class.java))
        Mockito.verify(editAction).setActionRow(Mockito.anyList())
        Mockito.verify(editAction).queue()

        // from index 1, click last (should remain at last)
        `when`(event.componentId).thenReturn("ud|last|foo|1|2|u1")
        cmd.buttonEvent(event)
        Mockito.verify(event, Mockito.times(2)).editMessageEmbeds(Mockito.any(MessageEmbed::class.java))
    }

    @Test
    fun `buttonEvent replies ephemeral when no results available`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        val replyAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction::class.java)
        val ephemeralReply = Mockito.mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction::class.java)

        `when`(svc.defineAll("foo")).thenReturn(emptyList())
        `when`(event.componentId).thenReturn("ud|next|foo|0|2|u1")
        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(ephemeralReply)

        cmd.buttonEvent(event)

        Mockito.verify(event).reply("No more results available.")
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(ephemeralReply).queue()
    }

    @Test
    fun `buttonEvent catches exception and replies error`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(ButtonInteractionEvent::class.java)
        val replyAction = Mockito.mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction::class.java)
        val ephemeralReply = Mockito.mock(net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction::class.java)

        `when`(event.componentId).thenReturn("ud|next|foo|0|2|u1")
        `when`(svc.defineAll("foo")).thenThrow(RuntimeException("boom"))

        `when`(event.reply(Mockito.anyString())).thenReturn(replyAction)
        `when`(replyAction.setEphemeral(true)).thenReturn(ephemeralReply)

        cmd.buttonEvent(event)

        Mockito.verify(event).reply("An error occurred processing that action.")
        Mockito.verify(replyAction).setEphemeral(true)
        Mockito.verify(ephemeralReply).queue()
    }

    @Test
    fun `long definition and example are truncated to 1024 characters`() {
        val cmd = UrbanDictionary()
        val svc = Mockito.mock(UrbanDictionaryService::class.java)
        cmd.urbanService = svc

        val event = Mockito.mock(SlashCommandInteractionEvent::class.java)
        val replyAction = Mockito.mock(ReplyCallbackAction::class.java)
        val hook = Mockito.mock(InteractionHook::class.java)
        @Suppress("UNCHECKED_CAST")
        val messageAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        val user = Mockito.mock(net.dv8tion.jda.api.entities.User::class.java)

        `when`(event.user).thenReturn(user)
        `when`(user.id).thenReturn("u1")

        val opt = Mockito.mock(net.dv8tion.jda.api.interactions.commands.OptionMapping::class.java)
        `when`(opt.asString).thenReturn("foo")
        `when`(event.getOption("term")).thenReturn(opt)

        val longText = "x".repeat(2000)
        val defs = listOf(
            def(word = "foo", definition = longText, example = longText, author = "a1")
        )
        `when`(svc.defineAll("foo")).thenReturn(defs)

        `when`(event.deferReply()).thenReturn(replyAction)
        `when`(event.hook).thenReturn(hook)
        `when`(hook.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(messageAction)
        `when`(messageAction.setActionRow(Mockito.anyList<Button>())).thenReturn(messageAction)

        cmd.execute(event)

        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        Mockito.verify(hook).sendMessageEmbeds(embedCaptor.capture())
        val embed = embedCaptor.value

        val defField = embed.fields.first { it.name == "Definition" }
        val exField = embed.fields.first { it.name == "Example" }
        val defVal = defField.value
        val exVal = exField.value
        assertNotNull(defVal)
        assertNotNull(exVal)
        assertEquals(1024, defVal!!.length)
        assertTrue(defVal.endsWith("..."))
        assertEquals(1024, exVal!!.length)
        assertTrue(exVal.endsWith("..."))

        Mockito.verify(messageAction).queue()
    }
}
