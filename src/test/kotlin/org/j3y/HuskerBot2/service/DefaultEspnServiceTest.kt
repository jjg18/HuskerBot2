package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import net.dv8tion.jda.api.entities.MessageEmbed
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DefaultEspnServiceTest {

    private val mapper = ObjectMapper()

    private fun emptyEventsJson(): ObjectNode {
        return mapper.readTree("""{ "events": [] }""") as ObjectNode
    }

    private fun sampleEventsJson(): ObjectNode {
        val json = """
            {
              "events": [
                {
                  "date": "2025-08-25T18:30:00Z",
                  "status": {
                    "period": 0,
                    "type": { "shortDetail": "Kickoff 3:30 PM ET" }
                  },
                  "competitions": [
                    {
                      "broadcasts": [ { "names": ["ABC"] } ],
                      "competitors": [
                        {
                          "team": { "abbreviation": "HOME" },
                          "curatedRank": { "current": 10 },
                          "winner": false,
                          "score": "0"
                        },
                        {
                          "team": { "abbreviation": "AWY" },
                          "curatedRank": { "current": 5 },
                          "winner": false,
                          "score": "0"
                        }
                      ]
                    }
                  ]
                },
                {
                  "date": "2025-08-26T04:00:00Z",
                  "status": {
                    "period": 4,
                    "type": { "shortDetail": "Final" }
                  },
                  "competitions": [
                    {
                      "broadcasts": [ { "names": ["ESPN"] } ],
                      "competitors": [
                        {
                          "team": { "abbreviation": "HOM2" },
                          "curatedRank": { "current": 99 },
                          "winner": false,
                          "score": "21"
                        },
                        {
                          "team": { "abbreviation": "AW2" },
                          "curatedRank": { "current": 3 },
                          "winner": true,
                          "score": "28"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        return mapper.readTree(json) as ObjectNode
    }

    @Test
    fun `buildEventString returns no-games message when events empty`() {
        val svc = DefaultEspnService()
        val result = svc.buildEventString(emptyEventsJson(), "My Title")
        assertEquals("##My Title\nNo games were found.", result)
    }

    @Test
    fun `buildEventString formats days, teams, network, status and time`() {
        val svc = DefaultEspnService()
        val json = sampleEventsJson()
        val result = svc.buildEventString(json, "Weekly Slate")

        // Header and code block
        assertTrue(result.startsWith("# Weekly Slate\n```prolog"))
        assertTrue(result.endsWith("\n```"))

        // Expect two day sections: MONDAY and TUESDAY (depending on timezone conversion)
        assertTrue(result.contains("MONDAY - 08/25") || result.contains("Monday - 08/25".uppercase()),
            "Expected Monday date header present: $result")
        assertTrue(result.contains("TUESDAY - 08/26") || result.contains("Tuesday - 08/26".uppercase()),
            "Expected Tuesday date header present: $result")

        // First scheduled game line should include network, teams and a time (do not assert timezone abbrev)
        assertTrue(result.contains("[ABC]"))
        assertTrue(result.contains("AWY @ HOME"))
        // Time in 12-hour format appears somewhere
        val timeRegex = Regex("\\b(0[1-9]|1[0-2]):[0-5][0-9] (AM|PM)\\b")
        assertTrue(timeRegex.containsMatchIn(result), "Expected a 12-hour time in the output: $result")

        // Final game line should show score and short detail, and winner/star and rank formatting
        assertTrue(result.contains("[ESPN]"))
        assertTrue(result.contains("AW2* @ HOM2")) // away winner has trailing *; home no star prefix
        assertTrue(result.contains("28-21"))
        assertTrue(result.contains("Final"))
        // Ranked away team shows leading rank number
        assertTrue(result.contains("3 AW2*"))
    }

    @Test
    fun `buildEventEmbed returns single no-games embed when empty`() {
        val svc = DefaultEspnService()
        val embeds = svc.buildEventEmbed(emptyEventsJson())
        assertEquals(1, embeds.size)
        assertEquals("Schedule...", embeds[0].title)
        assertEquals("No games were found.", embeds[0].description)
    }

    @Test
    fun `buildEventEmbed creates per-day embeds with proper formatting`() {
        val svc = DefaultEspnService()
        val embeds: List<MessageEmbed> = svc.buildEventEmbed(sampleEventsJson())

        // Expect two embeds (two days)
        assertTrue(embeds.size >= 2, "Expected at least two embeds for two different days")

        // Titles contain calendar emoji and date
        assertTrue(embeds[0].title?.contains("📅") == true)
        assertTrue(embeds[0].title?.contains("08/25") == true)
        assertTrue(embeds[1].title?.contains("📅") == true)

        // Descriptions contain formatted lines
        val desc0 = embeds[0].description
        val desc1 = embeds[1].description
        assertNotNull(desc0)
        assertNotNull(desc1)

        // Scheduled game: time and teams bold, with network
        assertTrue(desc0!!.contains("**AWY** @ **HOME**"))
        assertTrue(desc0.contains("•") && desc0.contains("ABC"))
        val timeRegex = Regex("\\b(0[1-9]|1[0-2]):[0-5][0-9] (AM|PM)\\b")
        assertTrue(timeRegex.containsMatchIn(desc0), "Expected a 12-hour time in the embed description: $desc0")

        // Final game: score, detail, winner markers escaped, ranking italicized
        assertTrue(desc1!!.contains("28-21"))
        assertTrue(desc1.contains("Final"))
        assertTrue(desc1.contains("_3_ **AW2**\\*")) // ranked away, with escaped asterisk
        assertTrue(desc1.contains("**HOM2**"))
        assertTrue(desc1.contains("ESPN"))
    }
}
