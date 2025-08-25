package org.j3y.HuskerBot2.automation.betting

import net.dv8tion.jda.api.JDA
import org.j3y.HuskerBot2.repository.BetRepo
import org.j3y.HuskerBot2.repository.ScheduleRepo
import org.j3y.HuskerBot2.service.CfbBettingLinesService
import org.j3y.HuskerBot2.util.WeekResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class BetProcessing {
    private final val log = LoggerFactory.getLogger(BetProcessing::class.java)

    @Autowired @Lazy lateinit var jda: JDA
    @Autowired lateinit var cfbBettingLinesService: CfbBettingLinesService
    @Autowired lateinit var betRepo: BetRepo
    @Autowired lateinit var scheduleRepo: ScheduleRepo

    // Every Monday at 2:00 AM Central (db-scheduler recurring task configured)
    final fun processBets() {
        return processBets(WeekResolver.currentCfbWeek() - 1)
    }

    final fun processBets(week: Int) {
        val season = LocalDate.now().year

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
        val spread = lines.path("spreadustedHomeScore > awayScore\n" +
                "\n" +
                "        var winner: String\n" +
                "        var beatSpread: String\n" +
                "        val overUnderIsOver = homeScore + awayScore > overUn").asDouble(0.0)
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


    }
}