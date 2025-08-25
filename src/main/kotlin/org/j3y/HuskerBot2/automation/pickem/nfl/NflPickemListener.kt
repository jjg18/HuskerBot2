package org.j3y.HuskerBot2.automation.pickem.nfl

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.components.buttons.Button
import org.j3y.HuskerBot2.commands.pickem.NflPickemShow
import org.j3y.HuskerBot2.model.NflPick
import org.j3y.HuskerBot2.repository.NflGameRepo
import org.j3y.HuskerBot2.repository.NflPickRepo
import org.j3y.HuskerBot2.util.WeekResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class NflPickemListener : ListenerAdapter() {
    private val log = LoggerFactory.getLogger(NflPickemListener::class.java)

    @Autowired lateinit var nflGameRepo: NflGameRepo
    @Autowired lateinit var nflPickRepo: NflPickRepo
    @Autowired lateinit var nflPickemShow: NflPickemShow

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        val id = event.componentId

        if (!id.startsWith("nflpickem|")) return
        if (id == "nflpickem|mypicks") return nflPickemShow.handleEvent(event, WeekResolver.currentNflWeek())
        try {
            val parts = id.split("|")
            if (parts.size < 3) return
            val eventId = parts[1]
            val teamId = parts[2]

            val game = nflGameRepo.findById(eventId.toLong()).orElse(null)
            if (game == null) {
                event.reply("Sorry, we don't have any data for game $eventId.").setEphemeral(true).queue()
                return
            }

            if (Instant.now().isAfter(game.dateTime)) {
                event.reply("You cannot make a pick after the game has already started!").setEphemeral(true).queue()
                return
            }

            val pickEntity = getPick(game.id, event.user.idLong)
            pickEntity.season = game.season
            pickEntity.week = game.week
            pickEntity.winningTeamId = teamId.toLong()

            nflPickRepo.save(pickEntity)

            // After saving, recompute counts and update the message buttons
            val picksForGame = try { nflPickRepo.findByGameId(game.id) } catch (e: Exception) { emptyList() }
            val awayCount = picksForGame.count { it.winningTeamId == game.awayTeamId }
            val homeCount = picksForGame.count { it.winningTeamId == game.homeTeamId }

            val awayLabel = "✈\uFE0F ${game.awayTeam} (${awayCount})"
            val homeLabel = "\uD83C\uDFE0 ${game.homeTeam} (${homeCount})"

            val eventIdStr = game.id.toString()
            val updatedButtons = listOf(
                Button.primary("nflpickem|$eventIdStr|${game.awayTeamId}", awayLabel),
                Button.primary("nflpickem|$eventIdStr|${game.homeTeamId}", homeLabel)
            )

            // Acknowledge ephemerally and also update the original message's action row
            event.message.editMessageComponents(net.dv8tion.jda.api.interactions.components.ActionRow.of(updatedButtons)).queue()

            event.deferEdit().queue()
        } catch (e: Exception) {
            log.error("Error handling NFL pick'em button", e)
            event.reply("An error occurred processing your pick.").setEphemeral(true).queue()
        }
    }

    private fun getPick(gameId: Long, userId: Long): NflPick {
        return nflPickRepo.findByGameIdAndUserId(gameId, userId) ?: NflPick(gameId, userId)
    }
}