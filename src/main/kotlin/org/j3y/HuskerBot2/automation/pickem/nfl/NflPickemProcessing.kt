package org.j3y.HuskerBot2.automation.pickem.nfl

import org.springframework.stereotype.Component

import com.fasterxml.jackson.databind.JsonNode
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.j3y.HuskerBot2.model.NflGameEntity
import org.j3y.HuskerBot2.model.NflPick
import org.j3y.HuskerBot2.repository.NflGameRepo
import org.j3y.HuskerBot2.repository.NflPickRepo
import org.j3y.HuskerBot2.service.EspnService
import org.j3y.HuskerBot2.util.WeekResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import java.awt.Color
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.EnumSet

@Component
class NflPickemProcessing {
    private final val log = LoggerFactory.getLogger(NflPickemProcessing::class.java)

    @Autowired private lateinit var nflPickRepo: NflPickRepo
    @Autowired @Lazy lateinit var jda: JDA
    @Autowired lateinit var espnService: EspnService
    @Autowired lateinit var nflGameRepo: NflGameRepo
    @Value("\${discord.channels.nfl-pickem}") lateinit var pickemChannelId: String

    // Every Tuesday at 2:00 AM Central (db-scheduler recurring task configured)
    fun postWeeklyPickem() {
        processPreviousWeek()
        deleteAllPosts()

        val week = WeekResolver.currentNflWeek()
        log.info("Posting NFL Pick'em for week {}", week)
        val data = espnService.getNflScoreboard(week)

        val channel: TextChannel? = jda.getTextChannelById(pickemChannelId)
        if (channel == null) {
            log.warn("Did not find the nfl pickem channel with ID: {}", pickemChannelId)
            return
        }

        // Ensure channel is read-only for users (no messages or threads)
        try {
            ensurePickemChannelReadOnly(channel)
        } catch (e: Exception) {
            log.warn("Unable to verify/enforce pick'em channel permissions: {}", e.message)
        }

        // First, post results from previous week and current season leaderboard
        try {
            postPreviousWeekResults(channel)
            postSeasonLeaderboard(channel)
        } catch (e: Exception) {
            log.error("Failed to post previous week results or leaderboard", e)
        }

        try {
            val events = data.path("events")
            if (events.isEmpty) {
                channel.sendMessage("No NFL games found for week $week.").queue()
            } else {
                channel.sendMessage("# \uD83C\uDFC8 NFL Pick'em for week $week \uD83C\uDFC8 \nGet your picks in before game time for each game!").queue()
                events.forEach { event ->
                    val embed = buildGameEmbed(event)
                    val (awayName, awayId, homeName, homeId, eventId) = extractIds(event)
                    val dateTime = OffsetDateTime.parse(event.path("date").asText("")).toInstant()
                    val season = LocalDateTime.now().year

                    val nflGame = getGame(eventId)
                    nflGame.homeTeam = homeName
                    nflGame.homeTeamId = homeId.toLong()
                    nflGame.awayTeam = awayName
                    nflGame.awayTeamId = awayId.toLong()
                    nflGame.dateTime = dateTime
                    nflGame.season = season
                    nflGame.week = week

                    nflGameRepo.save(nflGame)

                    // Determine current pick counts for this game
                    val existingPicks = try { nflPickRepo.findByGameId(eventId.toLong()) } catch (e: Exception) { emptyList() }
                    val awayCount = existingPicks.count { it.winningTeamId == awayId.toLong() }
                    val homeCount = existingPicks.count { it.winningTeamId == homeId.toLong() }

                    val awayLabel = "✈\uFE0F $awayName ($awayCount)"
                    val homeLabel = "\uD83C\uDFE0 $homeName ($homeCount)"

                    val buttons = listOf(
                        Button.primary("nflpickem|$eventId|$awayId", awayLabel),
                        Button.primary("nflpickem|$eventId|$homeId", homeLabel)
                    )
                    channel.sendMessageEmbeds(embed).setActionRow(buttons).queue()
                }
            }

            channel.sendMessageEmbeds(
                EmbedBuilder()
                    .setTitle("Pick Information")
                    .setDescription(
                        """You can change your pick any time before a game starts.
                        
                        Click the button below to see your current picks for the week.
                        You can also use the command `/nfl-pickem show [week<optional>]` to see your picks at any time.
                        """)
                    .setColor(Color(0x00, 0x66, 0xCC))
                    .build()
            ).addActionRow(
                Button.primary("nflpickem|mypicks", "View My Picks")
            ).queue()
        } catch (e: Exception) {
            log.error("Failed to post pick'em", e)
        }
    }

    private fun buildGameEmbed(event: JsonNode): net.dv8tion.jda.api.entities.MessageEmbed {
        val comp = event.path("competitions").path(0)
        val away = comp.path("competitors").path(1)
        val home = comp.path("competitors").path(0)

        val awayName = away.path("team").path("displayName").asText(away.path("team").path("abbreviation").asText("Away"))
        val homeName = home.path("team").path("displayName").asText(home.path("team").path("abbreviation").asText("Home"))

        val awayLogo = getLogoUrl(away)
        val homeLogo = getLogoUrl(home)

        val awayRec = getRecord(away)
        val homeRec = getRecord(home)

        val status = event.path("status").path("type").path("shortDetail").asText("TBD")
        val dateStr = event.path("date").asText("")
        val kickoffCentral = try {
            val dt = java.time.ZonedDateTime.parse(dateStr).withZoneSameInstant(ZoneId.of("America/Chicago"))
            dt.format(DateTimeFormatter.ofPattern("EEE MMM d, h:mm a z"))
        } catch (e: Exception) { status }

        val title = "$awayName @ $homeName"
        // Odds / Lines
        val odds = comp.path("odds")
        var lineText = "TBD"
        if (odds.isArray && odds.size() > 0) {
            val o = odds.path(0)
            val details = o.path("details").asText("")
            val overUnderNode = o.path("overUnder")
            val spreadNode = o.path("spread")
            val overUnder = if (overUnderNode.isMissingNode || overUnderNode.isNull) null else overUnderNode.asDouble()
            val spread = if (spreadNode.isMissingNode || spreadNode.isNull) null else spreadNode.asDouble()

            lineText = when {
                !details.isNullOrEmpty() && overUnder != null -> "$details (O/U ${"%.1f".format(overUnder)})"
                !details.isNullOrEmpty() -> details
                spread != null && overUnder != null -> "Spread: ${"%.1f".format(spread)} • O/U ${"%.1f".format(overUnder)}"
                spread != null -> "Spread: ${"%.1f".format(spread)}"
                overUnder != null -> "O/U ${"%.1f".format(overUnder)}"
                else -> "TBD"
            }
        }

        return EmbedBuilder()
            .setTitle(title)
            .setAuthor(awayName, null, awayLogo)
            .setFooter(homeName, homeLogo)
            .setColor(Color(0x00, 0x7A, 0x33))
            .addField("Away", "$awayName\nRecord: $awayRec", true)
            .addField("Home", "$homeName\nRecord: $homeRec", true)
            .addField("Kickoff", kickoffCentral, false)
            .addField("Line", lineText, false)
            .build()
    }

    private fun getLogoUrl(teamNode: JsonNode): String {
        val team = teamNode.path("team")
        val direct = team.path("logo").asText(null)
        if (!direct.isNullOrEmpty()) return direct
        val logos = team.path("logos")
        if (logos.isArray && logos.size() > 0) {
            val href = logos.path(0).path("href").asText(null)
            if (!href.isNullOrEmpty()) return href
        }
        return ""
    }

    private fun getRecord(compNode: JsonNode): String {
        val records = compNode.path("records")
        if (records.isArray && records.size() > 0) {
            val summary = records.path(0).path("summary").asText(null)
            if (!summary.isNullOrEmpty()) return summary
        }
        // fallback to wins/losses if present
        val wins = compNode.path("record").path("wins").asInt(-1)
        val losses = compNode.path("record").path("losses").asInt(-1)
        if (wins >= 0 && losses >= 0) return "$wins-$losses"
        return "0-0"
    }

    private data class Ids(
        val awayName: String, val awayId: String,
        val homeName: String, val homeId: String,
        val eventId: String,
    )

    private fun extractIds(event: JsonNode): Ids {
        val comp = event.path("competitions").path(0)
        val away = comp.path("competitors").path(1)
        val home = comp.path("competitors").path(0)
        val awayName = away.path("team").path("abbreviation").asText("AWY")
        val homeName = home.path("team").path("abbreviation").asText("HME")
        val awayId = away.path("team").path("id").asText("0")
        val homeId = home.path("team").path("id").asText("0")
        val eventId = event.path("id").asText("0")
        return Ids(awayName, awayId, homeName, homeId, eventId)
    }
    
    private fun deleteAllPosts() {
        val channel: TextChannel? = jda.getTextChannelById(pickemChannelId)
        if (channel == null) {
            log.warn("Did not find the nfl pickem channel with ID: {}", pickemChannelId)
            return
        }
        try {
            val history = channel.history
            while (true) {
                val messages = history.retrievePast(100).complete()
                if (messages.isEmpty()) break
                messages.forEach { msg ->
                    try {
                        msg.delete().complete()
                    } catch (e: Exception) {
                        log.warn("Error scheduling message deletion for {}", msg.id, e)
                    }
                }
            }
            log.info("Cleared all messages from NFL pick'em channel: {}", channel.id)
        } catch (e: Exception) {
            log.error("Failed to delete pick'em posts", e)
        }
    }

    private fun getGame(eventId: String): NflGameEntity {
        return nflGameRepo.findById(eventId.toLong()).orElse(NflGameEntity(eventId.toLong()))
    }

    private fun processPreviousWeek() {
        val prevWeek = WeekResolver.currentNflWeek() - 1

        if (prevWeek <= 0) {
            log.warn("Not processing previous week - there were no games in week {}", prevWeek)
            return
        }

        val data = espnService.getNflScoreboard(prevWeek)
        val events = data.path("events")
        if (events.isEmpty) {
            log.warn("No NFL games found for week {}", prevWeek)
            return
        }

        events.forEach { event ->
            val (_, awayId, _, homeId, eventId) = extractIds(event)
            val status = event.path("status").path("type").path("name").asText("TBD")

            if (status != "STATUS_FINAL") {
                log.info("Skipping game {} because it is not final", eventId)
                return@forEach
            }

            val teams = event.path("competitions").path(0).path("competitors")
            val homeTeam = teams.find { it.path("homeAway").asText() == "home" }
            val awayTeam = teams.find { it.path("homeAway").asText() == "away" }
            val homeScore = homeTeam?.path("score")?.asInt(0) ?: 0
            val awayScore = awayTeam?.path("score")?.asInt(0) ?: 0


            val game = nflGameRepo.findById(eventId.toLong())
                .orElseThrow { RuntimeException("Could not find game with id $eventId") }

            game.homeScore = homeScore
            game.awayScore = awayScore
            game.winnerId = if (homeScore > awayScore) homeId.toLong() else awayId.toLong()
            nflGameRepo.save(game)

            val gamePicks = nflPickRepo.findByGameId(game.id)
            gamePicks.forEach { this.processPick(it, game) }
            nflPickRepo.saveAll(gamePicks)
        }
    }

    private fun processPick(nflPick: NflPick, nflGameEntity: NflGameEntity) {
        nflPick.correctPick = nflPick.winningTeamId == nflGameEntity.winnerId
        nflPick.processed = true
    }

    private fun postPreviousWeekResults(channel: TextChannel) {
        val prevWeek = WeekResolver.currentNflWeek() - 1
        val season = LocalDateTime.now().year
        if (prevWeek <= 0) {
            log.info("No previous week results to post (week {}))", prevWeek)
            return
        }

        val picks = try { nflPickRepo.findBySeasonAndWeek(season, prevWeek) } catch (e: Exception) { emptyList() }
        if (picks.isEmpty()) {
            channel.sendMessage("No pick'em results for week $prevWeek.").queue()
            return
        }

        val byUser = picks.groupBy { it.userId }
        val userSummaries = byUser.map { (userId, userPicks) ->
            val correct = userPicks.count { it.correctPick }
            val total = userPicks.size
            val points = correct * 10
            Triple(userId, correct to total, points)
        }.sortedWith(compareByDescending<Triple<Long, Pair<Int, Int>, Int>> { it.third }.thenBy { it.first })

        val eb = EmbedBuilder()
            .setColor(Color(0x1F, 0x8B, 0x4C))
            .setTitle("NFL Pick'em — Week $prevWeek Results")
            .setDescription("Each correct pick is worth 10 points.")

        val sb = StringBuilder()
        var rank = 1
        userSummaries.forEach { (userId, correctTotal, points) ->
            val (correct, total) = correctTotal
            val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "" }
            sb.append("$medal $rank. <@${userId}> — ${points} pts (${correct}/${total} correct)\n")
            rank++
        }
        eb.addField("Leaderboard", sb.toString().ifBlank { "No results." }, false)

        channel.sendMessageEmbeds(eb.build()).queue()
    }

    private fun postSeasonLeaderboard(channel: TextChannel) {
        val season = LocalDateTime.now().year
        val allPicks = try { nflPickRepo.findAll() } catch (e: Exception) { emptyList() }
        val correctByUser = allPicks
            .asSequence()
            .filter { it.season == season && it.correctPick }
            .groupBy { it.userId }
            .mapValues { (_, picks) -> picks.size }

        if (correctByUser.isEmpty()) {
            channel.sendMessage("No season picks recorded yet for $season.").queue()
            return
        }

        val leaderboard = correctByUser
            .map { (userId, correctCount) -> Triple(userId, correctCount, correctCount * 10) }
            .sortedWith(compareByDescending<Triple<Long, Int, Int>> { it.third }.thenBy { it.first })

        val eb = EmbedBuilder()
            .setColor(Color(0x00, 0x66, 0xCC))
            .setTitle("NFL Pick'em — Season Leaderboard ($season)")
            .setDescription("Points = 10 × correct picks so far this season.")

        val sb = StringBuilder()
        var rank = 1
        leaderboard.forEach { (userId, correctCount, points) ->
            val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "" }
            sb.append("$medal $rank. <@${userId}> — ${points} pts (${correctCount} correct)\n")
            rank++
        }
        eb.addField("Top Players", sb.toString(), false)

        channel.sendMessageEmbeds(eb.build()).queue()
    }

    private fun ensurePickemChannelReadOnly(channel: TextChannel) {
        val guild = channel.guild
        val everyone = guild.publicRole
        val requiredDenied = EnumSet.of(
            Permission.MESSAGE_SEND,
            Permission.CREATE_PUBLIC_THREADS,
            Permission.CREATE_PRIVATE_THREADS,
            Permission.MESSAGE_SEND_IN_THREADS
        )
        val existing = channel.getPermissionOverride(everyone)
        if (existing == null || !existing.denied.containsAll(requiredDenied)) {
            channel.upsertPermissionOverride(everyone)
                .deny(requiredDenied)
                .queue(
                    { log.info("Ensured pick'em channel is read-only for @everyone") },
                    { t -> log.warn("Failed to update pick'em channel permissions: {}", t.message) }
                )
        } else {
            log.debug("Pick'em channel already denies messaging/thread creation for @everyone")
        }
    }
}
