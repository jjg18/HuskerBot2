package org.j3y.HuskerBot2.service

import tools.jackson.databind.JsonNode
import net.dv8tion.jda.api.entities.MessageEmbed

interface EspnService {
    fun getCfbScoreboard(league: Int, week: Int): JsonNode
    fun getNflScoreboard(week: Int): JsonNode
    fun getNhlScoreboard(days: Int): JsonNode
    fun getTeamData(team: String): JsonNode

    fun buildEventString(apiData: JsonNode, title: String): String
    fun buildEventEmbed(apiData: JsonNode): List<MessageEmbed>
}
