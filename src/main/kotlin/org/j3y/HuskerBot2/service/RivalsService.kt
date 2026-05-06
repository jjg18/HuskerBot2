package org.j3y.HuskerBot2.service

import tools.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Service
class RivalsService {
    private val searchUrl = "https://www.on3.com/_next/data/{buildId}/rivals/search.json?searchText={name}&minClassYear={year}&maxClassYear={year}&sportKey=1"
    private val playerUrl = "https://www.on3.com/_next/data/{buildId}/rivals/{playerSlug}/recruiting/{recruitKey}/interests.json"

    private val client = RestTemplate()

    data class RecruitData(
        val year: Int,
        val firstName: String,
        val lastName: String,
        val hometown: String,
        val highSchool: String,
        val height: String,
        val weight: String,
        val position: String,
        val status: String,
        val commitTeam: String,
        val commitTeamImageUrl: String? = null,
        val starRating: Int,
        val nationalRank: Int,
        val positionRank: Int,
        val stateRank: Int,
        val imageUrl: String? = null,
        val link: String,
        val prospects: List<RecruitProspects> = listOf(),
    )

    data class RecruitProspects(
        val teamName: String,
        val status: String,
        val prediction: Double,
        val officialVisitCount: Int,
        val unofficialVisitCount: Int,
    )

    /**
     * Calls the Rivals search page and extracts the Next.js buildId value from the embedded JSON.
     * @return buildId string if found, otherwise null
     */
    fun fetchBuildId(): String? {
        return try {
            val html = client.getForObject("https://www.on3.com/rivals/search/", String::class.java) ?: return null
            // Look for "buildId":"..." allowing for minified content
            val regex = Regex("\\\"buildId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            val match = regex.find(html)
            match?.groups?.get(1)?.value
        } catch (ex: Exception) {
            null
        }
    }

    fun getRecruitData(year: Int, name: String): RecruitData? {
        val buildId = fetchBuildId() ?: return null
        val uri = UriComponentsBuilder.fromUriString(searchUrl).buildAndExpand(buildId, name, year, year).toUri()
        val response = client.getForObject(uri, JsonNode::class.java)

        val recruit = response?.at("/pageProps/searchData/list/0") ?: return null
        val rating = recruit.path("rating")
        val slug = recruit.path("slug").asText()
        val recruitKey = recruit.path("recruitmentKey").asText()

        val link = "https://www.on3.com/rivals/$slug/"
        val prospects = getPlayerProspects(slug, recruitKey, buildId)

        return RecruitData(
            year = recruit.path("classYear").asInt(),
            firstName = recruit.path("firstName").asText("N/A"),
            lastName = recruit.path("lastName").asText("N/A"),
            hometown = recruit.path("homeTownName").asText("N/A"),
            highSchool = recruit.path("highSchoolName").asText("N/A"),
            height = recruit.path("formattedHeight").asText("N/A"),
            weight = recruit.path("weight").asText("N/A"),
            position = recruit.path("position").path("name").asText("N/A"),
            status = recruit.path("status").path("type").asText("N/A"),
            commitTeam = recruit.path("status").path("committedOrganization").path("fullName").asText("N/A"),
            commitTeamImageUrl = recruit.path("status").path("committedOrganization").path("assetUrl").asText(null),
            starRating = rating.path("consensusStars").asInt(),
            nationalRank = rating.path("consensusNationalRank").asInt(),
            positionRank = rating.path("consensusPositionRank").asInt(),
            stateRank = rating.path("consensusStateRank").asInt(),
            imageUrl = recruit.path("defaultAssetUrl").asText(null),
            link = link,
            prospects = prospects,
        )
    }

    fun getPlayerProspects(playerSlug: String, recruitKey: String, buildId: String? = null): List<RecruitProspects> {
        val thisBuildId = buildId ?: fetchBuildId() ?: return emptyList()
        val uri = UriComponentsBuilder.fromUriString(playerUrl).buildAndExpand(thisBuildId, playerSlug, recruitKey).toUri()
        val response = client.getForObject(uri, JsonNode::class.java)

        val prospects = response?.at("/pageProps/teamTargets/list") ?: return emptyList()
        val result = mutableListOf<RecruitProspects>()
        for (it in prospects) {
            result.add(RecruitProspects(
                teamName = it.at("/team/fullName").asText("N/A"),
                status = it.path("status").asText("N/A"),
                prediction = it.path("prediction").asDouble(),
                officialVisitCount = it.path("officialVisitCount").asInt(),
                unofficialVisitCount = it.path("unOfficialVisitCount").asInt(),
            ))
        }
        return result
    }
}