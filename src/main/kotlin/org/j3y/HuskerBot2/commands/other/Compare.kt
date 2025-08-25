package org.j3y.HuskerBot2.commands.other

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.j3y.HuskerBot2.commands.SlashCommand
import org.j3y.HuskerBot2.service.CfbMatchupService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.awt.Color

@Component
class Compare(
    private val cfbMatchupService: CfbMatchupService
) : SlashCommand() {
    
    private val log = LoggerFactory.getLogger(Compare::class.java)
    
    override fun getCommandKey(): String = "compare"
    override fun getDescription(): String = "Compare two CFB teams head-to-head history"
    override fun getOptions(): List<OptionData> = listOf(
        OptionData(OptionType.STRING, "team1", "First team name", true),
        OptionData(OptionType.STRING, "team2", "Second team name", true)
    )
    
    override fun execute(commandEvent: SlashCommandInteractionEvent) {
        commandEvent.deferReply().queue()
        
        try {
            val team1 = commandEvent.getOption("team1")?.asString?.trim()
            val team2 = commandEvent.getOption("team2")?.asString?.trim()
            
            if (team1.isNullOrBlank() || team2.isNullOrBlank()) {
                commandEvent.hook.sendMessage("Please provide both team names.").queue()
                return
            }
            
            log.info("Comparing CFB teams: $team1 vs $team2")
            
            val matchupData = cfbMatchupService.getTeamMatchup(team1, team2)
            
            if (matchupData == null) {
                commandEvent.hook.sendMessage("No matchup data found for $team1 vs $team2. Please check team names.").queue()
                return
            }
            
            val userId = commandEvent.user.id
            val embed = buildMatchupEmbed(matchupData, 0)
            
            if (matchupData.games.size > 5) {
                val buttons = buildPaginationButtons(team1, team2, 0, matchupData.games.size, userId)
                commandEvent.hook.sendMessageEmbeds(embed).setActionRow(buttons).queue()
            } else {
                commandEvent.hook.sendMessageEmbeds(embed).queue()
            }
            
        } catch (e: Exception) {
            log.error("Error executing compare command", e)
            commandEvent.hook.sendMessage("Sorry, there was an error comparing the teams.").queue()
        }
    }
    
    override fun buttonEvent(buttonEvent: ButtonInteractionEvent) {
        val id = buttonEvent.componentId
        if (!id.startsWith("compare|")) return
        
        try {
            val parts = id.split("|")
            if (parts.size < 6) return
            
            val action = parts[1]
            val team1 = parts[2]
            val team2 = parts[3]
            val currentPage = parts[4].toIntOrNull() ?: 0
            val userId = parts[5]
            
            val matchupData = cfbMatchupService.getTeamMatchup(team1, team2)
            if (matchupData == null) {
                buttonEvent.reply("Could not load matchup data.").setEphemeral(true).queue()
                return
            }
            
            val totalPages = (matchupData.games.size + 4) / 5 // 5 games per page
            val newPage = when (action) {
                "first" -> 0
                "prev" -> (currentPage - 1).coerceAtLeast(0)
                "next" -> (currentPage + 1).coerceAtMost(totalPages - 1)
                "last" -> totalPages - 1
                else -> currentPage
            }
            
            val embed = buildMatchupEmbed(matchupData, newPage)
            val buttons = buildPaginationButtons(team1, team2, newPage, matchupData.games.size, userId)
            
            buttonEvent.editMessageEmbeds(embed).setActionRow(buttons).queue()
            
        } catch (e: Exception) {
            log.error("Error handling compare button interaction", e)
            buttonEvent.reply("An error occurred processing that action.").setEphemeral(true).queue()
        }
    }
    
    private fun buildMatchupEmbed(matchupData: CfbMatchupService.TeamMatchupData, page: Int): net.dv8tion.jda.api.entities.MessageEmbed {
        val embed = EmbedBuilder()
        
        // Title and overall series info
        embed.setTitle("🏈 ${matchupData.team1} vs ${matchupData.team2}")
        embed.setColor(Color.BLUE)
        
        // Series summary
        val totalGames = matchupData.team1Wins + matchupData.team2Wins + matchupData.ties
        val seriesSummary = buildString {
            var leadsTrails = "leads"
            if (matchupData.team1Wins < matchupData.team2Wins) {
                leadsTrails = "trails"
            } else if (matchupData.team1Wins == matchupData.team2Wins) {
                leadsTrails = "ties"
            }
            append("**Series Record:** ${matchupData.team1} $leadsTrails ${matchupData.team1Wins}-${matchupData.team2Wins}")
            if (matchupData.ties > 0) append("-${matchupData.ties}")
            append(" ($totalGames games)")
        }
        embed.setDescription(seriesSummary)
        
        // Recent games (paginated if necessary)
        val gamesPerPage = 5
        val startIndex = page * gamesPerPage
        val endIndex = minOf(startIndex + gamesPerPage, matchupData.games.size)
        
        if (matchupData.games.isNotEmpty()) {
            val recentGames = matchupData.games.sortedByDescending { it.season }.subList(startIndex, endIndex)
            
            val gameResults = StringBuilder()
            recentGames.forEach { game ->
                val winner = when {
                    game.winner != null -> "**${game.winner}**"
                    game.homeScore != null && game.awayScore != null -> {
                        if (game.homeScore > game.awayScore) "**${game.homeTeam}**"
                        else "**${game.awayTeam}**"
                    }
                    else -> "Unknown"
                }
                
                val score = if (game.homeScore != null && game.awayScore != null) {
                    "${game.awayScore}-${game.homeScore}"
                } else {
                    "Score N/A"
                }
                
                val seasonType = when (game.seasonType.lowercase()) {
                    "postseason" -> "📋 Bowl/Playoff"
                    "regular" -> "🏟️ Regular"
                    else -> game.seasonType
                }
                
                val venue = game.venue?.let { " at $it" } ?: ""
                val neutral = if (game.neutralSite) " (Neutral)" else ""
                
                gameResults.append("**${game.season}** $seasonType\n")
                gameResults.append("${game.awayTeam} @ ${game.homeTeam}$venue$neutral\n")
                gameResults.append("Winner: $winner ($score)\n\n")
            }
            
            val fieldTitle = if (matchupData.games.size > gamesPerPage) {
                "Recent Games (Page ${page + 1} of ${(matchupData.games.size + gamesPerPage - 1) / gamesPerPage})"
            } else {
                "Recent Games"
            }
            
            embed.addField(fieldTitle, gameResults.toString().trim(), false)
        }
        
        // Last meeting info
        if (matchupData.games.isNotEmpty()) {
            val lastGame = matchupData.games.maxByOrNull { it.season }
            lastGame?.let { game ->
                val lastMeetingText = buildString {
                    append("**${game.season}** - ")
                    if (game.homeScore != null && game.awayScore != null) {
                        val winnerText = if (game.homeScore > game.awayScore) game.homeTeam else game.awayTeam
                        append("$winnerText won ${game.awayScore}-${game.homeScore}")
                    } else {
                        append("${game.awayTeam} @ ${game.homeTeam}")
                    }
                }
                embed.addField("Last Meeting", lastMeetingText, true)
            }
        }
        
        embed.setFooter("Data from College Football Data API")
        
        return embed.build()
    }
    
    private fun buildPaginationButtons(team1: String, team2: String, currentPage: Int, totalGames: Int, userId: String): List<Button> {
        val totalPages = (totalGames + 4) / 5
        val firstId = "compare|first|$team1|$team2|$currentPage|$userId"
        val prevId = "compare|prev|$team1|$team2|$currentPage|$userId"
        val nextId = "compare|next|$team1|$team2|$currentPage|$userId"
        val lastId = "compare|last|$team1|$team2|$currentPage|$userId"
        
        val atStart = currentPage <= 0
        val atEnd = currentPage >= totalPages - 1
        
        return listOf(
            Button.secondary(firstId, "⏮ First").withDisabled(atStart),
            Button.secondary(prevId, "◀ Prev").withDisabled(atStart),
            Button.secondary(nextId, "Next ▶").withDisabled(atEnd),
            Button.secondary(lastId, "Last ⏭").withDisabled(atEnd)
        )
    }
}