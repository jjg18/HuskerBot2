package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import org.j3y.HuskerBot2.util.SeasonResolver
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.awt.Color
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class DefaultEspnService(
    private val client: RestTemplate = RestTemplate()
) : EspnService {

    private final val log = LoggerFactory.getLogger(DefaultEspnService::class.java)

    private final val FORMATTER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")
    private final val FORMATTER_NHL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    private final val FORMATTER_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a z")
    private final val YEAR_CFB = SeasonResolver.currentCfbSeason()
    private final val YEAR_NFL = SeasonResolver.currentNflSeason()

    @Cacheable("cfbScoreboards")
    override fun getCfbScoreboard(league: Int, week: Int): JsonNode {
        log.info("Updating CFB ESPN Cache.")

        var league = league
        var week = week
        var seasonType = 2

        // Bowls (week = -1)
        if (week == -1) {
            seasonType = 3
            league = 0

        // CFP (week = -2)
        } else if (week == -2) {
            seasonType = 3
            league = 0
            week = 999
        }

        val espnUriBuilder =
            UriComponentsBuilder.fromUriString(
                "https://site.api.espn.com/apis/site/v2/sports/football/college-football/scoreboard" +
                    "?lang=en&region=us&calendartype=blacklist&limit=300&dates=${YEAR_CFB}&seasontype=${seasonType}"
            )

        if (week > 0) {
            espnUriBuilder.queryParam("week", week)
        }

        if (league != 0) {
            espnUriBuilder.queryParam("groups", league)
        }

        return client.getForObject(espnUriBuilder.build().toUri(), JsonNode::class.java)!!
    }

    @Cacheable("nflScoreboards")
    override fun getNflScoreboard(week: Int): JsonNode {
        log.info("Updating NFL Cache.")
        val espnUriBuilder = UriComponentsBuilder.fromUriString(
            "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard" +
                "?lang=en&region=us&calendartype=blacklist&limit=100&dates=${YEAR_NFL}&seasontype=2"
        ).queryParam("week", week)

        return client.getForObject(espnUriBuilder.build().toUri(), JsonNode::class.java)!!
    }

    @Cacheable("nhlScoreboards")
    override fun getNhlScoreboard(days: Int): JsonNode {
        log.info("Updating NHL Cache.")

        val lookupDate = LocalDate.now().plusDays(days.toLong())
        val dateQuery = lookupDate.format(FORMATTER_NHL_DATE)

        val espnUriBuilder = UriComponentsBuilder.fromUriString(
            "https://site.web.api.espn.com/apis/site/v2/" +
                "sports/hockey/nhl/scoreboard?region=us&lang=en&limit=100&calendartype=blacklist"
        )
            .queryParam("dates", dateQuery)

        return client.getForObject(espnUriBuilder.build().toUri(), JsonNode::class.java)!!
    }

    @Cacheable("teamData")
    override fun getTeamData(team: String): JsonNode {
        log.info("Updating Team Data Cache for Team $team.")

        val espnUriBuilder = UriComponentsBuilder.fromUriString(
            "http://site.api.espn.com/apis/site/v2/sports/football/college-football/teams/{team}"
        )

        return client.getForObject(espnUriBuilder.buildAndExpand(team).toUri(), JsonNode::class.java)!!
    }

    @CacheEvict(value = ["cfbScoreboards", "nflScoreboards", "nhlScoreboards"], allEntries = true)
    @Scheduled(fixedRate = 60000L)
    fun evictCaches() {
        log.debug("Evicted ESPN caches.")
    }

    override fun buildEventString(apiData: JsonNode, title: String): String {
        val events = apiData.path("events") as ArrayNode

        if (events.isEmpty) {
            return "##$title\nNo games were found."
        }

        val sb = StringBuilder("# $title\n```prolog")

        var currentDay = ""
        events.forEach { event ->
            val dateStr = event.path("date").asText()
            val period = event.path("status").path("period").asInt()
            var date = ZonedDateTime.parse(dateStr).withZoneSameInstant(ZoneOffset.UTC)
            date = date.withZoneSameInstant(ZoneId.of("America/New_York"))
            val centralDate = date.withZoneSameInstant(ZoneId.of("America/Chicago"))

            val eventDay = date.dayOfWeek.toString()
            if (currentDay != eventDay) {
                if (currentDay != "") sb.append("\n")
                currentDay = eventDay
                sb.append("\n${eventDay} - ${date.format(FORMATTER_DATE)}")
                sb.append('\n').append("-------------------------------------------------")
            }

            val competition = event.path("competitions").path(0)
            val network = competition.path("broadcasts").path(0)
                .path("names").path(0).asText("TBD")

            val away = competition.path("competitors").path(1)
            var awayTeam = away.path("team").path("abbreviation").asText()
            val isAwayWinner = away.path("winner").asBoolean(false)
            val awayRank = away.path("curatedRank").path("current").asInt(99)
            if (isAwayWinner) awayTeam = "${awayTeam}*"
            if (awayRank <= 25) awayTeam = "${awayRank} ${awayTeam}"

            val home = competition.path("competitors").path(0)
            var homeTeam = home.path("team").path("abbreviation").asText()
            val isHomeWinner = home.path("winner").asBoolean(false)
            val homeRank = home.path("curatedRank").path("current").asInt(99)
            if (isHomeWinner) homeTeam = "*${homeTeam}"
            if (homeRank <= 25) homeTeam = "${homeTeam} ${homeRank}"

            val status = if (period > 0) {
                val competitors = event.path("competitions").path(0).path("competitors")
                val homeScore = competitors.path(0).path("score").asText()
                val awayScore = competitors.path(1).path("score").asText()
                val statusTxt = event.path("status").path("type").path("shortDetail").asText()
                String.format("%3s-%-4s%-12s", awayScore, homeScore, statusTxt)
            } else if (date.hour != 0) {
                String.format("%12s", centralDate.format(FORMATTER_TIME))
            } else {
                "TBD"
            }

            sb.append(String.format("\n%-8s%10s @ %-10s %-7s", "[${network}]", awayTeam, homeTeam, status))
        }

        sb.append("\n```")
        return sb.toString()
    }

    override fun buildEventEmbed(apiData: JsonNode): List<MessageEmbed> {
        val events = apiData.path("events") as ArrayNode
        val embeds: MutableList<MessageEmbed> = mutableListOf()

        if (events.isEmpty) {
            val emb = EmbedBuilder().setTitle("Schedule...")
            emb.setDescription("No games were found.")
            return listOf(emb.build())
        }

        var currentDay = ""
        var emb = EmbedBuilder()
        var sb = StringBuilder()

        events.forEach { event ->
            val dateStr = event.path("date").asText()
            val period = event.path("status").path("period").asInt()
            var date = ZonedDateTime.parse(dateStr).withZoneSameInstant(ZoneOffset.UTC)
            date = date.withZoneSameInstant(ZoneId.of("America/New_York"))
            val centralDate = date.withZoneSameInstant(ZoneId.of("America/Chicago"))

            val eventDay = date.dayOfWeek.toString()
            if (currentDay != eventDay) {
                if (!currentDay.isEmpty()) {
                    emb.setDescription(sb.toString())
                    emb.setColor(Color.RED)
                    embeds.add(emb.build())

                    sb = StringBuilder()
                }

                currentDay = eventDay
                emb = EmbedBuilder().setTitle("\uD83D\uDCC5 \u200E $eventDay ${date.format(FORMATTER_DATE)}")
            }

            val competition = event.path("competitions").path(0)
            val network = competition.path("broadcasts").path(0)
                .path("names").path(0).asText("TBD")

            val away = competition.path("competitors").path(1)
            var awayTeam = "**${away.path("team").path("abbreviation").asText()}**"
            val isAwayWinner = away.path("winner").asBoolean(false)
            val awayRank = away.path("curatedRank").path("current").asInt(99)
            if (isAwayWinner) awayTeam = "${awayTeam}\\*"
            if (awayRank <= 25) awayTeam = "_${awayRank}_ ${awayTeam}"

            val home = competition.path("competitors").path(0)
            var homeTeam = "**${home.path("team").path("abbreviation").asText()}**"
            val isHomeWinner = home.path("winner").asBoolean(false)
            val homeRank = home.path("curatedRank").path("current").asInt(99)
            if (isHomeWinner) homeTeam = "\\*${homeTeam}"
            if (homeRank <= 25) homeTeam = "${homeTeam} _${homeRank}_"

            val status = if (period > 0) {
                val competitors = event.path("competitions").path(0).path("competitors")
                val homeScore = competitors.path(0).path("score").asText()
                val awayScore = competitors.path(1).path("score").asText()
                val statusTxt = event.path("status").path("type").path("shortDetail").asText()
                String.format("%3s-%-4s%-12s", awayScore, homeScore, statusTxt)
            } else if (date.hour != 0) {
                String.format("%12s", centralDate.format(FORMATTER_TIME))
            } else {
                "TBD"
            }

            //sb.append(String.format("\n%-8s%10s @ %-10s %-7s", "[${network}]", awayTeam, homeTeam, status))
            sb.append("\n$status \u200E \u200E \u200E \u200E $awayTeam @ $homeTeam \u200E \u200E  • \u200E \u200E  $network")
        }

        if (!currentDay.isEmpty()) {
            emb.setDescription(sb.toString())
            emb.setColor(Color.RED)
            embeds.add(emb.build())
        }

        return embeds
    }
}
