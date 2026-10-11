package org.j3y.HuskerBot2.automation.betting

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.UserSnowflake
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import org.j3y.HuskerBot2.commands.betting.BetShow
import org.j3y.HuskerBot2.repository.BetRepo
import org.j3y.HuskerBot2.repository.MessageRepo
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.service.CfbBettingLinesService
import org.j3y.HuskerBot2.service.BetLeaderboardService
import org.j3y.HuskerBot2.util.SeasonResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.util.EnumSet

@Component
class BetProcessing {
    @Autowired
    private lateinit var betShow: BetShow
    private final val log = LoggerFactory.getLogger(BetProcessing::class.java)
    
    private final val FINAL_WEEK = 14;

    @Autowired @Lazy lateinit var jda: JDA
    @Value("\${discord.channels.husker-bets}")lateinit var huskerBetsChannelId: String
    @Autowired private lateinit var cfbBettingLinesService: CfbBettingLinesService
    @Autowired private lateinit var betRepo: BetRepo
    @Autowired private lateinit var scheduleRepo: ScheduleRepo
    @Autowired private lateinit var messageRepo: MessageRepo
    @Autowired private lateinit var leaderboardService: BetLeaderboardService

    // Every Monday at 2:00 AM Central (db-scheduler recurring task configured)
    final fun processBets() {
        return processBets(SeasonResolver.currentCfbWeek() - 1)
    }

    final fun processBets(week: Int) {
        val season = SeasonResolver.currentCfbSeason()

        val gameEntity = scheduleRepo.findBySeasonAndWeek(season, week) ?: return log.info("No schedule found for week $week.")

        if (gameEntity.completed ?: false) {
            log.warn("Game $week for season $season has already been processed.")
            return;
        }

        val data = cfbBettingLinesService.getLines(season, week, "nebraska")?.path(0) ?:
            return log.warn("Unable to get lines for week $week for season $season.")

        log.info("Line Response: {}", data)

        val homeScore = data.path("homeScore").asInt()
        val awayScore = data.path("awayScore").asInt()

        val lines = data.path("lines").path(0)
        val overUnder = lines.path("overUnder").asDouble(0.0)
        val spread = lines.path("spread").asDouble(0.0)
        val formattedSpread = lines.path("formattedSpread").asText()
        val adjustedHomeScore = homeScore + spread
        val homeBeatSpread = adjustedHomeScore > awayScore

        var winner: String
        var beatSpread: String
        val overUnderIsOver = homeScore + awayScore > overUnder

        if (data.path("homeTeam").asText() == "Nebraska") {
            gameEntity.huskersScore = homeScore
            gameEntity.opponentScore = awayScore
            gameEntity.didNebraskaBeatSpread = homeBeatSpread
            winner = if (homeScore > awayScore) "Nebraska" else "Opponent"
            beatSpread = if (homeBeatSpread) "Nebraska" else "Opponent"
        } else {
            gameEntity.huskersScore = awayScore
            gameEntity.opponentScore = homeScore
            gameEntity.didNebraskaBeatSpread = homeBeatSpread.not()
            winner = if (awayScore > homeScore) "Nebraska" else "Opponent"
            beatSpread = if (homeBeatSpread) "Opponent" else "Nebraska"
        }

        log.info("Is Nebraska Home? {}", data.path("homeTeam").asText() == "Nebraska")
        log.info("Home Score: {} - Away Score: {}", homeScore, awayScore)
        log.info("Spread: {} - Over/Under: {}", formattedSpread, overUnder)
        log.info("Winner: {} - Beat Spread: {} - Over/Under: {}", winner, beatSpread, if (overUnderIsOver) "Over" else "Under")

        gameEntity.spread = formattedSpread
        gameEntity.overUnder = overUnder
        gameEntity.completed = true
        scheduleRepo.save(gameEntity)

        val bets = betRepo.findBySeasonAndWeek(season, week)
        bets.forEach { bet ->
            bet.correctWinner = bet.winner == winner
            bet.correctSpread = bet.predictSpread == beatSpread
            bet.correctPoints = bet.predictPoints == if (overUnderIsOver) "Over" else "Under"
        }
        betRepo.saveAll(bets)

        if (week >= FINAL_WEEK) {
            postWeekLeaderboard(week)
            postSeasonLeaderboard()
            postWinnerEmbed()
        } else {
            val nextGameWeek =
                scheduleRepo.findFirstByDateTimeAfterOrderByDateTimeAsc(gameEntity.dateTime)?.week ?: (week + 1)

            postWeeklyBets(nextGameWeek)
        }
    }

    final fun postWeeklyBets(week: Int = SeasonResolver.currentCfbWeek()) {
        val season = SeasonResolver.currentCfbSeason()
        val gameEntity = scheduleRepo.findBySeasonAndWeek(season, week) ?: return log.info("No schedule found for week $week.")
        val channel = jda.getTextChannelById(huskerBetsChannelId) ?: return log.warn("No channel found for id $huskerBetsChannelId.")

        val data = cfbBettingLinesService.getLines(season, week, "nebraska")?.path(0) ?:
        return log.warn("Unable to get lines for week $week for season $season.")

        // Ensure channel is read-only for users (no messages or threads)
        try {
            ensureChannelReadOnly(channel)
        } catch (e: Exception) {
            log.warn("Unable to verify/enforce pick'em channel permissions: {}", e.message)
        }

        deleteAllPosts()
        val prevWeek = scheduleRepo.findFirstByDateTimeBeforeOrderByDateTimeDesc(gameEntity.dateTime)?.week ?: (week - 1)
        postWeekLeaderboard(prevWeek)
        postSeasonLeaderboard()
        
        val lines = data.path("lines").path(0)
        val overUnder = lines.path("overUnder").asDouble(0.0)
        val formattedSpread = lines.path("formattedSpread").asText()

        channel.sendMessage("# \uD83C\uDFC8 Husker Bets for Week $week \uD83C\uDFC8 \nGet your picks in an hour before game time!").queue()
        // Winner
        channel.sendMessageEmbeds(
            EmbedBuilder().setTitle("Winner: Nebraska or ${gameEntity.opponent}")
                .setDescription("Pick the winner.")
                .build()
        ).addComponents(
            ActionRow.of(
                Button.danger("huskerbets|winner|$week|Nebraska", "Nebraska"),
                Button.secondary("huskerbets|winner|$week|Opponent", gameEntity.opponent)
            )
        ).queue()

        // Over/Under
        channel.sendMessageEmbeds(
            EmbedBuilder().setTitle("Over/Under: ${overUnder}")
                .setDescription("Pick the over or the under.")
                .build()
        ).addComponents(
            ActionRow.of(
                Button.success("huskerbets|overunder|$week|Over", "Over"),
                Button.secondary("huskerbets|overunder|$week|Under", "Under")
            )
        ).queue()

        // Spread Beat
        channel.sendMessageEmbeds(
            EmbedBuilder().setTitle("Spread: ${formattedSpread}")
                .setDescription("Pick who will beat the spread")
                .build()
        ).addComponents(
            ActionRow.of(
                Button.danger("huskerbets|spread|$week|Nebraska", "Nebraska"),
                Button.secondary("huskerbets|spread|$week|Opponent", gameEntity.opponent)
            )
        ).queue()

        log.info("Posted bets for week $week")
        betShow.sendBetChannelMessage(channel.guild, channel, week)
    }

    // Posts a leaderboard for the previous week (current week - 1)
    fun postWeekLeaderboard(week: Int = SeasonResolver.currentCfbWeek()) {
        val season = SeasonResolver.currentCfbSeason()
        if (week < 1) {
            log.info("No previous week available to post leaderboard for (week computed: {}).", week)
            return
        }
        val channel = jda.getTextChannelById(huskerBetsChannelId) ?: return log.warn("No channel found for id $huskerBetsChannelId.")

        val bets = betRepo.findBySeasonAndWeek(season, week)
        if (bets.isEmpty()) {
            channel.sendMessage("No bets found for week ${week} of season ${season}.").queue()
            return
        }

        val embed = leaderboardService.buildLeaderboardEmbed(
            bets = bets,
            title = "🏆 Weekly Husker Betting Leaderboard — Week ${week} ($season)",
            guild = channel.guild
        )
        channel.sendMessageEmbeds(embed).queue()
    }

    // Posts a leaderboard for the entire season
    fun postSeasonLeaderboard() {
        val season = SeasonResolver.currentCfbSeason()
        val channel = jda.getTextChannelById(huskerBetsChannelId) ?: return log.warn("No channel found for id $huskerBetsChannelId.")

        val bets = betRepo.findBySeason(season)
        if (bets.isEmpty()) {
            channel.sendMessage("No bets found for the ${season} season.").queue()
            return
        }

        val embed = leaderboardService.buildLeaderboardEmbed(
            bets = bets,
            title = "🏆 Husker Betting Leaderboard — $season Season",
            guild = channel.guild
        )
        channel.sendMessageEmbeds(embed).queue()
    }

    /**
     * Posts a simple congratulations message for the season winner(s).
     *
     * This is only called once the final week has been processed.
     */
    private fun postWinnerEmbed() {
        val season = SeasonResolver.currentCfbSeason()
        val channel = jda.getTextChannelById(huskerBetsChannelId) ?: return log.warn("No channel found for id $huskerBetsChannelId.")

        val bets = betRepo.findBySeason(season)
        if (bets.isEmpty()) {
            channel.sendMessage("No bets found for the ${season} season, so no winner could be determined.").queue()
            return
        }

        val totals = leaderboardService.computeTotals(bets)
        if (totals.isEmpty()) {
            channel.sendMessage("No scored bets found for the ${season} season, so no winner could be determined.").queue()
            return
        }

        // Highest point total across all users
        val topScore = totals.first().second.points
        val winners = totals.filter { it.second.points == topScore }

        // Build a human-friendly winners list, preferring Discord display names when possible
        val names = winners.map { (userId, total) ->
            val displayName = try {
                channel.guild.retrieveMember(UserSnowflake.fromId(userId)).complete()?.effectiveName
            } catch (_: Exception) {
                null
            }

            displayName ?: total.userTag.ifBlank { userId.toString() }
        }

        val winnerLine = when (names.size) {
            1 -> names.first()
            2 -> names.joinToString(" and ")
            else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
        }

        val title = "f3c6 Husker Bets ${season} Season Champion${if (names.size > 1) "s" else ""}!"
        val description = buildString {
            append("Congratulations to ")
            append(winnerLine)
            append(" for finishing the season on top with ")
            append(topScore)
            append(" point")
            if (topScore != 1) append("s")
            append("!")
        }

        val embed = EmbedBuilder()
            .setTitle(title)
            .setDescription(description)
            .setColor(java.awt.Color(200, 16, 46))
            .build()

        channel.sendMessageEmbeds(embed).queue()
    }

    private fun deleteAllPosts() {
        val channel: TextChannel? = jda.getTextChannelById(huskerBetsChannelId)
        if (channel == null) {
            log.warn("Did not find the husker bets channel with ID: {}", huskerBetsChannelId)
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
            messageRepo.deleteById("huskerbet-bets")
            log.info("Cleared all messages from husker bets channel: {}", channel.id)
        } catch (e: Exception) {
            log.error("Failed to delete husker bets posts", e)
        }
    }

    private fun ensureChannelReadOnly(channel: TextChannel) {
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
                    { log.info("Ensured bet channel is read-only for @everyone") },
                    { t -> log.warn("Failed to update bet channel permissions: {}", t.message) }
                )
        } else {
            log.debug("Bet channel already denies messaging/thread creation for @everyone")
        }
    }
}