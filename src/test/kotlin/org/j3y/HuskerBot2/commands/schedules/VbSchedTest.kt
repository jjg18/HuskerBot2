package org.j3y.HuskerBot2.commands.schedules

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import org.j3y.HuskerBot2.service.HuskersDotComService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class VbSchedTest {

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
        val cmd = VbSched()
        assertEquals("vb", cmd.getCommandKey())
        assertEquals("Get the Nebraska volleyball schedule", cmd.getDescription())
        assertTrue(cmd.isSubcommand())

        val opts = cmd.getOptions()
        assertTrue(opts.isEmpty())
    }

    @Test
    fun `empty schedule sends appropriate message`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)

        val apiJson = Mockito.mock(JsonNode::class.java)
        val emptyData = Mockito.mock(JsonNode::class.java)
        `when`(emptyData.isEmpty).thenReturn(true)
        `when`(apiJson.path("data")).thenReturn(emptyData)
        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertEquals("No volleyball schedule found.", captor.value)
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with valid data calls service correctly`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create a real JSON object for testing
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Stanford")
        game1.put("venue_type", "home")
        game1.put("location", "Lincoln, Neb.")
        game1.put("datetime", "2025-08-24T19:30:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-espn.png")
        icon.put("title", "ESPN")
        icon.put("alt", "Watch on ESPN")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // verify deferReply and service call
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        
        // verify a message was sent
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `service exception sends error message`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)

        `when`(service.getScheduleById(1363)).thenThrow(RuntimeException("API Error"))

        cmd.execute(event)

        Mockito.verify(replyAction).queue()
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        assertTrue(captor.value.contains("Failed to retrieve volleyball schedule"))
        assertTrue(captor.value.contains("API Error"))
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with ESPN network from title`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing ESPN in title
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Stanford")
        game1.put("venue_type", "home")
        game1.put("location", "Lincoln, Neb.")
        game1.put("datetime", "2025-08-24T19:30:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-generic.png")
        icon.put("title", "ESPN")
        icon.put("alt", "Watch on ESPN")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with FS1 network from title`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing FS1 in title
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Stanford")
        game1.put("venue_type", "home")
        game1.put("location", "Lincoln, Neb.")
        game1.put("datetime", "2025-08-24T19:30:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-generic.png")
        icon.put("title", "FOX Sports 1")
        icon.put("alt", "Watch on FS1")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with B1G+ network from title`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing B1G+ in title
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Wisconsin")
        game1.put("venue_type", "away")
        game1.put("location", "Madison, Wis.")
        game1.put("datetime", "2025-10-12T15:00:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-streaming.png")
        icon.put("title", "B1G+")
        icon.put("alt", "Big Ten Plus")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with NPM network from title`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing NPM in title
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Creighton")
        game1.put("venue_type", "home")
        game1.put("location", "Lincoln, Neb.")
        game1.put("datetime", "2025-09-10T19:00:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-local.png")
        icon.put("title", "NPM")
        icon.put("alt", "Nebraska Public Media")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with NPM network from filename`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing NPM in filename
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Penn State")
        game1.put("venue_type", "away")
        game1.put("location", "University Park, Pa.")
        game1.put("datetime", "2025-10-05T18:30:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-npm.svg")
        icon.put("title", "")
        icon.put("alt", "")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with BTN network from title`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing BTN in title
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Ohio State")
        game1.put("venue_type", "home")
        game1.put("location", "Lincoln, Neb.")
        game1.put("datetime", "2025-10-26T19:30:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-btn.png")
        icon.put("title", "BTN")
        icon.put("alt", "Big Ten Network")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with BTN network from alt text`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing Big Ten Network in alt text
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Michigan")
        game1.put("venue_type", "away")
        game1.put("location", "Ann Arbor, Mich.")
        game1.put("datetime", "2025-11-02T15:30:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-generic.png")
        icon.put("title", "")
        icon.put("alt", "Big Ten Network")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `successful execute with B1G+ network from alt text`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()
        @Suppress("UNCHECKED_CAST")
        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with schedule_event_links containing Big Ten Plus in alt text
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        val game1 = factory.objectNode()
        game1.put("opponent_name", "Iowa")
        game1.put("venue_type", "home")
        game1.put("location", "Lincoln, Neb.")
        game1.put("datetime", "2025-11-15T14:00:00.000000Z")
        game1.put("status", "as_scheduled")
        
        val result1 = factory.objectNode()
        result1.put("result", "")
        result1.put("nebraska_score", "")
        result1.put("opponent_score", "")
        game1.set<ObjectNode>("schedule_event_result", result1)
        
        val linksArray = factory.arrayNode()
        val tvLink = factory.objectNode()
        val icon = factory.objectNode()
        icon.put("name", "tv-streaming.png")
        icon.put("title", "")
        icon.put("alt", "Big Ten Plus")
        tvLink.set<ObjectNode>("icon", icon)
        linksArray.add(tvLink)
        game1.set<ArrayNode>("schedule_event_links", linksArray)
        
        dataArray.add(game1)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(hook).sendMessage(Mockito.anyString())
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `filters out non-Nebraska games correctly`() {
        val cmd = VbSched()
        val service = Mockito.mock(HuskersDotComService::class.java)
        cmd.huskersDotComService = service

        val (event, replyAction, hook) = setupEvent()

        val msgAction = Mockito.mock(WebhookMessageCreateAction::class.java) as WebhookMessageCreateAction<Message>
        `when`(hook.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.addEmbeds(Mockito.anyList<MessageEmbed>())).thenReturn(msgAction)

        // Create JSON with mixed Nebraska and non-Nebraska games
        val factory = JsonNodeFactory.instance
        val apiJson = factory.objectNode()
        val dataArray = factory.arrayNode()
        
        // Nebraska game (should be included)
        val nebraskaGame = factory.objectNode()
        nebraskaGame.put("opponent_name", "Stanford")
        nebraskaGame.put("venue_type", "home")
        nebraskaGame.put("location", "Lincoln, Neb.")
        nebraskaGame.put("datetime", "2025-08-24T19:30:00.000000Z")
        nebraskaGame.put("status", "as_scheduled")
        nebraskaGame.set<ObjectNode>("second_opponent_id", factory.nullNode()) // Null indicates Nebraska game
        
        val nebraskaResult = factory.objectNode()
        nebraskaResult.put("result", "")
        nebraskaResult.put("nebraska_score", "")
        nebraskaResult.put("opponent_score", "")
        nebraskaGame.set<ObjectNode>("schedule_event_result", nebraskaResult)
        
        val nebraskaLinksArray = factory.arrayNode()
        nebraskaGame.set<ArrayNode>("schedule_event_links", nebraskaLinksArray)
        
        // Non-Nebraska tournament game (should be filtered out)
        val nonNebraskaGame = factory.objectNode()
        nonNebraskaGame.put("opponent_name", "Team A vs Team B") // Tournament game between other teams
        nonNebraskaGame.put("venue_type", "") // Empty venue type
        nonNebraskaGame.put("second_opponent_id", 456) // Non-null indicates game between two other teams
        nonNebraskaGame.put("location", "Lincoln, Neb.") // Still at Nebraska venue but not Nebraska playing
        nonNebraskaGame.put("datetime", "2025-09-13T14:00:00.000000Z") // The problematic date
        nonNebraskaGame.put("status", "as_scheduled")
        
        val nonNebraskaResult = factory.objectNode()
        nonNebraskaResult.put("result", "")
        nonNebraskaResult.put("nebraska_score", "") // No Nebraska score
        nonNebraskaResult.put("opponent_score", "") // No opponent score
        nonNebraskaGame.set<ObjectNode>("schedule_event_result", nonNebraskaResult)
        
        val nonNebraskaLinksArray = factory.arrayNode()
        nonNebraskaGame.set<ArrayNode>("schedule_event_links", nonNebraskaLinksArray)
        
        dataArray.add(nebraskaGame)
        dataArray.add(nonNebraskaGame)
        apiJson.set<ArrayNode>("data", dataArray)

        `when`(service.getScheduleById(1363)).thenReturn(apiJson)

        cmd.execute(event)

        // Capture the message sent to verify only Nebraska games are included
        val captor = ArgumentCaptor.forClass(String::class.java)
        Mockito.verify(hook).sendMessage(captor.capture())
        
        // The message should mention "Nebraska Volleyball Schedule" but not include non-Nebraska game details
        val sentMessage = captor.value
        assertTrue(sentMessage.contains("Nebraska Volleyball Schedule"))
        
        // Verify the execution completed without errors
        Mockito.verify(replyAction).queue()
        Mockito.verify(service).getScheduleById(1363)
        Mockito.verify(msgAction).queue()
    }

    @Test
    fun `isNebraskaGame correctly identifies Nebraska games`() {
        val cmd = VbSched()
        val factory = JsonNodeFactory.instance
        
        // Test case 1: Valid Nebraska game (second_opponent_id is null)
        val nebraskaGame = factory.objectNode()
        nebraskaGame.put("opponent_name", "Stanford")
        nebraskaGame.put("venue_type", "home")
        nebraskaGame.set<ObjectNode>("second_opponent_id", factory.nullNode())
        val nebraskaResult = factory.objectNode()
        nebraskaResult.put("nebraska_score", "")
        nebraskaResult.put("opponent_score", "")
        nebraskaGame.set<ObjectNode>("schedule_event_result", nebraskaResult)
        
        assertTrue(cmd.isNebraskaGame(nebraskaGame))
        
        // Test case 2: Valid Nebraska game (second_opponent_id missing)
        val nebraskaGame2 = factory.objectNode()
        nebraskaGame2.put("opponent_name", "Iowa")
        nebraskaGame2.put("venue_type", "away")
        // second_opponent_id not included (missing)
        val nebraskaResult2 = factory.objectNode()
        nebraskaResult2.put("nebraska_score", "")
        nebraskaResult2.put("opponent_score", "")
        nebraskaGame2.set<ObjectNode>("schedule_event_result", nebraskaResult2)
        
        assertTrue(cmd.isNebraskaGame(nebraskaGame2))
        
        // Test case 3: Non-Nebraska tournament game (second_opponent_id has a value)
        val tournamentGame = factory.objectNode()
        tournamentGame.put("opponent_name", "Penn State")
        tournamentGame.put("venue_type", "")
        tournamentGame.put("second_opponent_id", 123) // Non-null value indicates game between two other teams
        tournamentGame.put("datetime", "2025-09-13T14:00:00.000000Z")
        val tournamentResult = factory.objectNode()
        tournamentResult.put("nebraska_score", "")
        tournamentResult.put("opponent_score", "")
        tournamentGame.set<ObjectNode>("schedule_event_result", tournamentResult)
        
        assertFalse(cmd.isNebraskaGame(tournamentGame))
        
        // Test case 4: Game with Nebraska score data (definitely Nebraska, even if second_opponent_id were present)
        val gameWithScores = factory.objectNode()
        gameWithScores.put("opponent_name", "Wisconsin")
        gameWithScores.put("venue_type", "home")
        gameWithScores.set<ObjectNode>("second_opponent_id", factory.nullNode())
        val scoreResult = factory.objectNode()
        scoreResult.put("nebraska_score", "25")
        scoreResult.put("opponent_score", "20")
        gameWithScores.set<ObjectNode>("schedule_event_result", scoreResult)
        
        assertTrue(cmd.isNebraskaGame(gameWithScores))
        
        // Test case 5: Empty opponent name (should be filtered out)
        val emptyOpponentGame = factory.objectNode()
        emptyOpponentGame.put("opponent_name", "")
        emptyOpponentGame.put("venue_type", "")
        emptyOpponentGame.set<ObjectNode>("second_opponent_id", factory.nullNode())
        val emptyResult = factory.objectNode()
        emptyResult.put("nebraska_score", "")
        emptyResult.put("opponent_score", "")
        emptyOpponentGame.set<ObjectNode>("schedule_event_result", emptyResult)
        
        assertFalse(cmd.isNebraskaGame(emptyOpponentGame))
        
        // Test case 6: Game with Nebraska scores but no opponent (edge case - should be included)
        val gameWithScoresNoOpponent = factory.objectNode()
        gameWithScoresNoOpponent.put("opponent_name", "")
        gameWithScoresNoOpponent.put("venue_type", "")
        gameWithScoresNoOpponent.set<ObjectNode>("second_opponent_id", factory.nullNode())
        val scoreResultNoOpponent = factory.objectNode()
        scoreResultNoOpponent.put("nebraska_score", "24")
        scoreResultNoOpponent.put("opponent_score", "21")
        gameWithScoresNoOpponent.set<ObjectNode>("schedule_event_result", scoreResultNoOpponent)
        
        assertTrue(cmd.isNebraskaGame(gameWithScoresNoOpponent))
    }
}