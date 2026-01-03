package org.j3y.HuskerBot2.automation.pickem.nfl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction
import org.j3y.HuskerBot2.model.NflGameEntity
import org.j3y.HuskerBot2.model.NflPick
import org.j3y.HuskerBot2.repository.NflGameRepo
import org.j3y.HuskerBot2.repository.NflPickRepo
import org.j3y.HuskerBot2.service.EspnService
import org.j3y.HuskerBot2.service.NflPickemLeaderboardService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.time.Instant
import java.time.LocalDate
import java.util.*

class NflPickemProcessingTest {

    private lateinit var processing: NflPickemProcessing
    private lateinit var pickRepo: NflPickRepo
    private lateinit var gameRepo: NflGameRepo
    private lateinit var espn: EspnService
    private lateinit var jda: JDA

    private val mapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        processing = NflPickemProcessing()
        pickRepo = Mockito.mock(NflPickRepo::class.java)
        gameRepo = Mockito.mock(NflGameRepo::class.java)
        espn = Mockito.mock(EspnService::class.java)
        jda = Mockito.mock(JDA::class.java)

        // helper to set fields by reflection to avoid lateinit getter access
        fun setField(name: String, value: Any) {
            val f = NflPickemProcessing::class.java.getDeclaredField(name)
            f.isAccessible = true
            f.set(processing, value)
        }
        setField("nflPickRepo", pickRepo)
        setField("nflGameRepo", gameRepo)
        setField("espnService", espn)
        setField("jda", jda)
        setField("pickemChannelId", "chan")
        setField("leaderboardService", NflPickemLeaderboardService())
    }

    private fun scoreboardWithOneEvent(
        eventId: String = "1001",
        homeName: String = "NE",
        homeId: String = "1",
        awayName: String = "KC",
        awayId: String = "2",
        dateIso: String = "2025-10-12T17:25:00Z",
        includeOdds: Boolean = true
    ): JsonNode {
        val root = mapper.createObjectNode()
        val events = mapper.createArrayNode()
        root.set<ArrayNode>("events", events)
        val season = mapper.createObjectNode()
        season.put("year", SeasonResolver.currentNflSeason())
        root.set<ObjectNode>("season", season)

        val event = mapper.createObjectNode()
        event.put("id", eventId)
        event.put("date", dateIso)
        val status = mapper.createObjectNode()
        val statusType = mapper.createObjectNode()
        statusType.put("name", "STATUS_SCHEDULED")
        statusType.put("shortDetail", "Sun 12:25 PM")
        status.set<ObjectNode>("type", statusType)
        event.set<ObjectNode>("status", status)

        val comp = mapper.createObjectNode()
        val competitors = mapper.createArrayNode()
        // home at index 0 per code, away at index 1
        fun competitor(abbrev: String, id: String, homeAway: String, logo: String = "https://example.com/logo.png", recSummary: String = "1-0"): ObjectNode {
            val compNode = mapper.createObjectNode()
            compNode.put("homeAway", homeAway)
            compNode.put("score", 0)
            val team = mapper.createObjectNode()
            team.put("abbreviation", abbrev)
            team.put("displayName", abbrev)
            team.put("id", id)
            team.put("logo", logo)
            val logos = mapper.createArrayNode()
            val logoObj = mapper.createObjectNode()
            logoObj.put("href", logo)
            logos.add(logoObj)
            team.set<ArrayNode>("logos", logos)
            compNode.set<ObjectNode>("team", team)
            val records = mapper.createArrayNode()
            val r = mapper.createObjectNode()
            r.put("summary", recSummary)
            records.add(r)
            compNode.set<ArrayNode>("records", records)
            return compNode
        }
        competitors.add(competitor(homeName, homeId, "home"))
        competitors.add(competitor(awayName, awayId, "away"))
        comp.set<ArrayNode>("competitors", competitors)

        if (includeOdds) {
            val odds = mapper.createArrayNode()
            val o = mapper.createObjectNode()
            o.put("details", "$homeName -3.5")
            o.put("overUnder", 48.5)
            o.put("spread", 3.5)
            odds.add(o)
            comp.set<ArrayNode>("odds", odds)
        }

        val competitions = mapper.createArrayNode()
        competitions.add(comp)
        event.set<ArrayNode>("competitions", competitions)

        events.add(event)
        return root
    }

    private data class ChannelBundle(
        val channel: TextChannel,
        val msgAction: MessageCreateAction,
    )

    private fun mockChannelWithPermissions(id: String = "chan"): ChannelBundle {
        val channel = Mockito.mock(TextChannel::class.java)
        val msgAction = Mockito.mock(MessageCreateAction::class.java)
        val guild = Mockito.mock(Guild::class.java)
        val role = Mockito.mock(Role::class.java)
        val override = Mockito.mock(net.dv8tion.jda.api.entities.PermissionOverride::class.java)
        val history = Mockito.mock(net.dv8tion.jda.api.entities.MessageHistory::class.java)

        // First call for deleteAllPosts returns the channel; next calls also return the channel
        `when`(jda.getTextChannelById(id)).thenReturn(channel)
        `when`(channel.id).thenReturn(id)
        `when`(channel.history).thenReturn(history)
        `when`(history.retrievePast(Mockito.anyInt())).thenReturn(Mockito.mock(net.dv8tion.jda.api.requests.RestAction::class.java) as net.dv8tion.jda.api.requests.RestAction<List<net.dv8tion.jda.api.entities.Message>>)
        `when`(history.retrievePast(Mockito.anyInt()).complete()).thenReturn(emptyList())
        // read-only already ensured by existing override
        `when`(channel.guild).thenReturn(guild)
        `when`(guild.publicRole).thenReturn(role)
        `when`(channel.getPermissionOverride(role)).thenReturn(override)
        val denied: EnumSet<Permission> = EnumSet.of(
            Permission.MESSAGE_SEND,
            Permission.CREATE_PUBLIC_THREADS,
            Permission.CREATE_PRIVATE_THREADS,
            Permission.MESSAGE_SEND_IN_THREADS
        )
        `when`(override.denied).thenReturn(denied)

        // sendMessage(String) and sendMessageEmbeds(embed) stubs
        `when`(channel.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(channel.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(msgAction)
        `when`(msgAction.addActionRow(Mockito.any(Button::class.java))).thenReturn(msgAction)
        `when`(msgAction.setActionRow(Mockito.anyList())).thenReturn(msgAction)
        `when`(msgAction.queue()).then { null }

        // In case ensurePickemChannelReadOnly tries to upsert
        val upsert = Mockito.mock(PermissionOverrideAction::class.java)
        `when`(channel.upsertPermissionOverride(role)).thenReturn(upsert)
        `when`(upsert.deny(Mockito.anySet<Permission>())).thenReturn(upsert)
        `when`(upsert.queue(Mockito.any(), Mockito.any())).then { null }

        return ChannelBundle(channel, msgAction)
    }

    @Test
    fun `postWeeklyPickem posts game embed, saves game, and includes pick buttons with counts`() {
        val scoreboard = scoreboardWithOneEvent(eventId = "2002", homeName = "SF", homeId = "99", awayName = "DAL", awayId = "77")
        `when`(espn.getNflScoreboard(Mockito.anyInt())).thenReturn(scoreboard)

        // getGame() path: not found -> new entity
        `when`(gameRepo.findById(2002L)).thenReturn(Optional.empty())

        // Picks for this game: 2 picks DAL, 1 pick SF
        val picks = listOf(
            NflPick(gameId = 2002L, userId = 1, season = SeasonResolver.currentNflSeason(), week = 2, winningTeamId = 77L),
            NflPick(gameId = 2002L, userId = 2, season = SeasonResolver.currentNflSeason(), week = 2, winningTeamId = 77L),
            NflPick(gameId = 2002L, userId = 3, season = SeasonResolver.currentNflSeason(), week = 2, winningTeamId = 99L)
        )
        `when`(pickRepo.findByGameId(2002L)).thenReturn(picks)

        val ch = mockChannelWithPermissions("chan")
        `when`(ch.msgAction.addActionRow(Mockito.any(net.dv8tion.jda.api.interactions.components.ItemComponent::class.java))).thenReturn(ch.msgAction)

        // Capture the per-game embed and action row buttons
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(ch.channel.sendMessageEmbeds(embedCaptor.capture())).thenReturn(ch.msgAction)

        // Also capture any setActionRow, buttons list
        val buttonsCaptor = ArgumentCaptor.forClass(List::class.java as Class<List<Button>>)
        `when`(ch.msgAction.setActionRow(buttonsCaptor.capture())).thenReturn(ch.msgAction)
        
        processing.postWeeklyPickem(2)

        // Verify game saved with ids/names set from JSON
        val gameSavedCaptor = ArgumentCaptor.forClass(NflGameEntity::class.java)
        Mockito.verify(gameRepo).save(gameSavedCaptor.capture())
        val saved = gameSavedCaptor.value
        assertEquals(2002L, saved.id)
        assertEquals("SF", saved.homeTeam)
        assertEquals(99L, saved.homeTeamId)
        assertEquals("DAL", saved.awayTeam)
        assertEquals(77L, saved.awayTeamId)
        assertEquals(SeasonResolver.currentNflSeason(), saved.season)
        assertTrue(saved.week > 0)
        assertNotNull(saved.dateTime)

        // Verify embed title and fields
        val gameEmbed = embedCaptor.allValues.firstOrNull { it.title == "DAL @ SF" }
        assertNotNull(gameEmbed)
        val fields = gameEmbed!!.fields
        assertEquals(4, fields.size)
        assertEquals("Away", fields[0].name)
        assertTrue((fields[0].value ?: "").contains("DAL"))
        assertEquals("Home", fields[1].name)
        assertTrue((fields[1].value ?: "").contains("SF"))
        assertEquals("Kickoff", fields[2].name)
        assertEquals("Line", fields[3].name)

        // Verify two buttons with proper ids and counts reflected in labels
        val buttons = buttonsCaptor.value
        assertEquals(2, buttons.size)
        val id0 = buttons[0].id
        val id1 = buttons[1].id
        assertNotNull(id0)
        assertNotNull(id1)
        assertTrue(id0!!.startsWith("nflpickem|2002|"))
        assertTrue(id1!!.startsWith("nflpickem|2002|"))
        val labels = buttons.map { it.label }
        // We expect counts (away DAL=2, home SF=1) somewhere in labels
        assertTrue(labels.any { it.contains("(2)") })
        assertTrue(labels.any { it.contains("(1)") })

        // Verify informational embed was posted as well
        Mockito.verify(ch.channel, Mockito.atLeastOnce()).sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))
    }

    @Test
    fun `postWeeklyPickem posts season leaderboard message when no picks recorded`() {
        // Scoreboard with no events to shortcut
        val root = mapper.createObjectNode()
        root.set<ArrayNode>("events", mapper.createArrayNode())
        `when`(espn.getNflScoreboard(Mockito.anyInt())).thenReturn(root)

        val ch = mockChannelWithPermissions("chan")

        // No picks for the season
        `when`(pickRepo.findAll()).thenReturn(emptyList())

        val msgCaptor = ArgumentCaptor.forClass(String::class.java)
        `when`(ch.channel.sendMessage(msgCaptor.capture())).thenReturn(ch.msgAction)
        `when`(ch.msgAction.addActionRow(Mockito.any(net.dv8tion.jda.api.interactions.components.ItemComponent::class.java))).thenReturn(ch.msgAction)

        processing.postWeeklyPickem(2)

        // Among messages, we should have the season no-picks notice
        val allMsgs = msgCaptor.allValues.joinToString("\n")
        assertTrue(allMsgs.contains("No season picks recorded yet for ${SeasonResolver.currentNflSeason()}."))
    }
    
    @Test
    fun `ensurePickemChannelReadOnly updates permissions when missing or incomplete`() {
        // Prepare a channel where no override exists
        val channel = Mockito.mock(TextChannel::class.java)
        val guild = Mockito.mock(Guild::class.java)
        val role = Mockito.mock(Role::class.java)
        val upsert = Mockito.mock(PermissionOverrideAction::class.java)

        `when`(jda.getTextChannelById("chan")).thenReturn(channel)
        `when`(channel.guild).thenReturn(guild)
        `when`(guild.publicRole).thenReturn(role)
        `when`(channel.getPermissionOverride(role)).thenReturn(null)
        `when`(channel.upsertPermissionOverride(role)).thenReturn(upsert)
        `when`(upsert.deny(Mockito.anySet<Permission>())).thenReturn(upsert)
        `when`(upsert.queue(Mockito.any(), Mockito.any())).then { null }

        // Call public method that triggers ensurePickemChannelReadOnly internally
        // We also need ESPn scoreboard and channel to continue
        val root = mapper.createObjectNode()
        root.set<ArrayNode>("events", mapper.createArrayNode())
        `when`(espn.getNflScoreboard(Mockito.anyInt())).thenReturn(root)

        // Also stub sendMessage for season leaderboard path
        val msgAction = Mockito.mock(MessageCreateAction::class.java)
        `when`(channel.sendMessage(Mockito.anyString())).thenReturn(msgAction)
        `when`(msgAction.queue()).then { null }
        `when`(channel.sendMessageEmbeds(Mockito.any(MessageEmbed::class.java))).thenReturn(msgAction)

        assertDoesNotThrow { processing.postWeeklyPickem(2) }

        Mockito.verify(channel).upsertPermissionOverride(role)
        val permsCaptor = ArgumentCaptor.forClass(Collection::class.java as Class<Collection<Permission>>)
        Mockito.verify(upsert).deny(permsCaptor.capture())
        val perms = permsCaptor.value
        assertTrue(perms.containsAll(EnumSet.of(
            Permission.MESSAGE_SEND,
            Permission.CREATE_PUBLIC_THREADS,
            Permission.CREATE_PRIVATE_THREADS,
            Permission.MESSAGE_SEND_IN_THREADS
        )))
        Mockito.verify(upsert).queue(Mockito.any(), Mockito.any())
    }


    @Test
    fun `postSeasonLeaderboard lists sorted users with medals`() {
        val ch = mockChannelWithPermissions("chan").channel
        val msgAction = Mockito.mock(MessageCreateAction::class.java)
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        `when`(ch.sendMessageEmbeds(embedCaptor.capture())).thenReturn(msgAction)
        `when`(msgAction.queue()).then { null }
        `when`(ch.sendMessage(Mockito.anyString())).thenReturn(msgAction)

        val season = SeasonResolver.currentNflSeason()
        val allPicks = listOf(
            NflPick(gameId = 1, userId = 1, season = season, week = 1, winningTeamId = 2, correctPick = true, processed = true),
            NflPick(gameId = 2, userId = 1, season = season, week = 1, winningTeamId = 3, correctPick = false, processed = true),
            NflPick(gameId = 3, userId = 2, season = season, week = 1, winningTeamId = 4, correctPick = true, processed = true),
            NflPick(gameId = 4, userId = 2, season = season, week = 1, winningTeamId = 4, correctPick = true, processed = true),
            NflPick(gameId = 5, userId = 3, season = season, week = 1, winningTeamId = 4, correctPick = false, processed = true)
        )
        `when`(pickRepo.findAll()).thenReturn(allPicks)

        // Return scoreboard without events to simplify
        val root = mapper.createObjectNode()
        root.set<ArrayNode>("events", mapper.createArrayNode())
        `when`(espn.getNflScoreboard(Mockito.anyInt())).thenReturn(root)
        
        `when`(msgAction.addActionRow(Mockito.any(net.dv8tion.jda.api.interactions.components.ItemComponent::class.java))).thenReturn(msgAction)

        processing.postWeeklyPickem(2)

        val embed = embedCaptor.allValues.firstOrNull { it.title?.contains("Season Leaderboard") == true }
        assertNotNull(embed)
        val body = embed!!.fields.firstOrNull { it.name == "Leaderboard" }?.value ?: ""
        assertTrue(body.contains("🥇 <@2> — 20 pts (2/2 correct)"))
        assertTrue(body.contains("🥈 <@1> — 10 pts (1/2 correct)"))
    }

    @Test
    fun `postWeeklyPickem with week 19 congratulates winner and returns early`() {
        val ch = mockChannelWithPermissions("chan").channel
        val msgAction = Mockito.mock(MessageCreateAction::class.java)
        val embedCaptor = ArgumentCaptor.forClass(MessageEmbed::class.java)
        val msgCaptor = ArgumentCaptor.forClass(String::class.java)

        `when`(ch.sendMessageEmbeds(embedCaptor.capture())).thenReturn(msgAction)
        `when`(ch.sendMessage(msgCaptor.capture())).thenReturn(msgAction)
        `when`(msgAction.queue()).then { null }

        val season = SeasonResolver.currentNflSeason()
        val allPicks = listOf(
            NflPick(gameId = 1, userId = 123, season = season, week = 18, winningTeamId = 1, correctPick = true, processed = true),
            NflPick(gameId = 2, userId = 456, season = season, week = 18, winningTeamId = 2, correctPick = false, processed = true)
        )
        `when`(pickRepo.findBySeasonAndWeek(season, 18)).thenReturn(allPicks)
        `when`(pickRepo.findAll()).thenReturn(allPicks)

        // For week 19, it should still process prev week (18) and post results
        val scoreboard18 = mapper.createObjectNode()
        scoreboard18.set<ArrayNode>("events", mapper.createArrayNode())
        `when`(espn.getNflScoreboard(18)).thenReturn(scoreboard18)

        processing.postWeeklyPickem(19)

        // Verify winner message
        val allMsgs = msgCaptor.allValues
        assertTrue(allMsgs.any { it.contains("Congratulations to our NFL Pick'em season winner, <@123>!") })
        assertTrue(allMsgs.any { it.contains("That concludes the NFL regular season pick'em.") })

        // Verify leaderboard was posted
        val allEmbeds = embedCaptor.allValues
        assertTrue(allEmbeds.any { it.title?.contains("Season Leaderboard") == true })
        assertTrue(allEmbeds.any { it.title?.contains("Week 18 Results") == true })

        // Verify it didn't call getNflScoreboard(19)
        Mockito.verify(espn, Mockito.never()).getNflScoreboard(19)
    }


    @Test
    fun `buildGameEmbed formats odds with fallbacks and kickoff parsing`() {
        val scoreboard = scoreboardWithOneEvent(includeOdds = false)
        val event = scoreboard.path("events").path(0)

        // Use reflection to call private buildGameEmbed
        val method = NflPickemProcessing::class.java.getDeclaredMethod("buildGameEmbed", JsonNode::class.java)
        method.isAccessible = true
        val embed = method.invoke(processing, event) as MessageEmbed

        // With no odds, line should be TBD and Kickoff a formatted date
        val lineField = embed.fields.first { it.name == "Line" }
        assertEquals("TBD", lineField.value)
        assertEquals("DAL @ NE".replace("DAL", event.path("competitions").path(0).path("competitors").path(1).path("team").path("abbreviation").asText()).replace("NE", event.path("competitions").path(0).path("competitors").path(0).path("team").path("abbreviation").asText()), embed.title)
    }
}
