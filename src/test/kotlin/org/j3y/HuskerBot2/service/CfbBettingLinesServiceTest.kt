package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.net.URI

class CfbBettingLinesServiceTest {

    private lateinit var service: CfbBettingLinesService
    private lateinit var mockRestTemplate: RestTemplate
    private val mapper = ObjectMapper()

    @BeforeEach
    fun setup() {
        service = CfbBettingLinesService(
            baseUrl = "https://api.collegefootballdata.com/betting",
            userAgent = "HB-Tests/1.0",
            apiKey = "TEST_KEY"
        )
        mockRestTemplate = Mockito.mock(RestTemplate::class.java)
        val field = CfbBettingLinesService::class.java.getDeclaredField("restTemplate")
        field.isAccessible = true
        field.set(service, mockRestTemplate)
    }

    @Test
    fun `returns body when non-empty array and constructs correct request`() {
        val year = 2024
        val week = 3
        val team = "Nebraska"

        val body: ArrayNode = mapper.createArrayNode()
        val obj: ObjectNode = mapper.createObjectNode()
        obj.put("gameId", 12345)
        body.add(obj)

        val uriCaptor = ArgumentCaptor.forClass(URI::class.java)
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java as Class<HttpEntity<String>>)

        `when`(
            mockRestTemplate.exchange(
                uriCaptor.capture(),
                Mockito.eq(HttpMethod.GET),
                entityCaptor.capture(),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(body, HttpStatus.OK))

        val result = service.getLines(year, week, team)

        // Returned body is not null and matches
        assertNotNull(result)
        assertEquals(1, result!!.size())
        assertEquals(12345, result[0].get("gameId").asInt())

        // Verify URI path and query parameters
        val calledUri = uriCaptor.value
        val uriStr = calledUri.toString()
        // Should go to /lines endpoint
        assertNotNull(calledUri)
        assert(uriStr.contains("/lines")) { "URI should point to /lines but was $uriStr" }
        // Query params
        assert(uriStr.contains("year=$year")) { "Missing year param in $uriStr" }
        assert(uriStr.contains("seasonType=regular")) { "Missing seasonType=regular param in $uriStr" }
        assert(uriStr.contains("week=$week")) { "Missing week param in $uriStr" }
        assert(uriStr.contains("team=$team")) { "Missing team param in $uriStr" }

        // Verify headers include User-Agent and Authorization Bearer
        val entity = entityCaptor.value
        val headers = entity.headers
        assertEquals("HB-Tests/1.0", headers.getFirst("User-Agent"))
        assertEquals("Bearer TEST_KEY", headers.getFirst("Authorization"))
    }

    @Test
    fun `returns null when body is empty array`() {
        val emptyBody: ArrayNode = mapper.createArrayNode()

        `when`(
            mockRestTemplate.exchange(
                Mockito.any(URI::class.java),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(HttpEntity::class.java as Class<HttpEntity<String>>),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity(emptyBody, HttpStatus.OK))

        val result = service.getLines(2024, 5, "Nebraska")
        assertNull(result)
    }

    @Test
    fun `returns null when body is null`() {
        `when`(
            mockRestTemplate.exchange(
                Mockito.any(URI::class.java),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(HttpEntity::class.java as Class<HttpEntity<String>>),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity<JsonNode>(null, HttpStatus.OK))

        val result = service.getLines(2024, 6, "Nebraska")
        assertNull(result)
    }

    @Test
    fun `returns null when exception occurs`() {
        `when`(
            mockRestTemplate.exchange(
                Mockito.any(URI::class.java),
                Mockito.eq(HttpMethod.GET),
                Mockito.any(HttpEntity::class.java as Class<HttpEntity<String>>),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("Boom"))

        val result = service.getLines(2024, 7, "Nebraska")
        assertNull(result)
    }
}
