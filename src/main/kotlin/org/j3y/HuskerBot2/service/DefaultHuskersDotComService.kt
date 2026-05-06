package org.j3y.HuskerBot2.service

import tools.jackson.databind.JsonNode
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder


@Service
class DefaultHuskersDotComService : HuskersDotComService {
    val yearToSchedIdMap: Map<Int, Int> = mapOf(
        2025 to 241,
        2026 to 242,
        2027 to 243,
        2028 to 244
    )
    val url: String = "https://huskers.com/website-api/schedule-events?filter[schedule_id]={schedId}&filter[hide_from_specific_sport_schedule]=false&include=conference.image,opponent.customLogo,opponent.officialLogo,opponentLogo,postEventArticle.image,preEventArticle.image,presentedBy,promotionalItems.image,schedule.sport,scheduleEventLinks.icon,scheduleEventResult,secondOpponent.customLogo,secondOpponent.officialLogo,secondOpponentLogo,tournament&per_page=100&sort=datetime&page=1"

    val client = RestTemplate()

    init {
        client.setRequestFactory(HttpComponentsClientHttpRequestFactory())
    }

    override fun getSchedule(year: Int): JsonNode {
        val uri = UriComponentsBuilder.fromUriString(url).buildAndExpand(yearToSchedIdMap[year])

        return client.getForObject(uri.toUriString(), JsonNode::class.java) ?: throw RuntimeException("Unable to retrieve schedule for year $year")
    }

    override fun getScheduleById(scheduleId: Int): JsonNode {
        val uri = UriComponentsBuilder.fromUriString(url).buildAndExpand(scheduleId)

        return client.getForObject(uri.toUriString(), JsonNode::class.java) ?: throw RuntimeException("Unable to retrieve schedule for id $scheduleId")
    }
}