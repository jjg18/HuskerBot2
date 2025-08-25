package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import java.net.URI

class UrbanDictionaryServiceTest {

    private val mapper = ObjectMapper()

    private fun buildServiceWithMock(
        baseUrl: String = "https://api.urbandictionary.com",
        userAgent: String = "HuskerBot-Tests/1.0"
    ): Pair<UrbanDictionaryService, RestTemplate> {
        val svc = UrbanDictionaryService(baseUrl, userAgent)
        val mockClient = Mockito.mock(RestTemplate::class.java)
        val field = UrbanDictionaryService::class.java.getDeclaredField("client")
        field.isAccessible = true
        field.set(svc, mockClient)
        return svc to mockClient
    }

    @Test
    fun `defineAll builds correct request parses list and cleans brackets`() {
        val (svc, client) = buildServiceWithMock()

        val json: JsonNode = mapper.readTree(
            """
            {
              "list": [
                {
                  "word": "foo",
                  "definition": "A [foo] is [great]",
                  "example": "Such a [foo] example",
                  "author": "Alice",
                  "permalink": "https://urbandictionary.com/define.php?term=foo"
                },
                {
                  "definition": "Missing word falls back to term",
                  "example": "",
                  "author": null,
                  "permalink": null
                }
              ]
            }
            """.trimIndent()
        )

        `when`(
            client.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(json))

        val term = "foobar"
        val results = svc.defineAll(term)
        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("foo", first.word)
        assertEquals("A foo is great", first.definition) // brackets removed
        assertEquals("Such a foo example", first.example)
        assertEquals("Alice", first.author)
        assertEquals("https://urbandictionary.com/define.php?term=foo", first.permalink)

        val second = results[1]
        assertEquals(term, second.word) // fallback to input term when word missing
        assertEquals("Missing word falls back to term", second.definition)
        assertEquals("", second.example) // example cleaned but empty preserved
        assertEquals("null", second.author)
        assertEquals("null", second.permalink)

        // Verify request composed correctly (URL, method, headers)
        val uriCaptor = ArgumentCaptor.forClass(URI::class.java)
        val methodCaptor = ArgumentCaptor.forClass(HttpMethod::class.java)
        @Suppress("UNCHECKED_CAST")
        val entityCaptor = ArgumentCaptor.forClass(HttpEntity::class.java) as ArgumentCaptor<HttpEntity<Void>>

        Mockito.verify(client).exchange(
            uriCaptor.capture(),
            methodCaptor.capture(),
            entityCaptor.capture(),
            Mockito.eq(JsonNode::class.java)
        )

        val uri = uriCaptor.value.toString()
        assertTrue(uri.startsWith("https://api.urbandictionary.com/v0/define"))
        assertTrue(uri.contains("term="))
        // URL should include the endpoint and query param
        assertTrue(uri.contains("term="))
        assertEquals(HttpMethod.GET, methodCaptor.value)

        val headers: HttpHeaders = entityCaptor.value.headers
        assertEquals("HuskerBot-Tests/1.0", headers.getFirst("User-Agent"))
    }

    @Test
    fun `defineAll returns empty list when list field missing or empty`() {
        val (svc1, client1) = buildServiceWithMock()
        val emptyListJson: JsonNode = mapper.readTree("{" + "\"list\": []" + "}")
        `when`(
            client1.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(emptyListJson))
        assertTrue(svc1.defineAll("term").isEmpty())

        val (svc2, client2) = buildServiceWithMock()
        val missingListJson: JsonNode = mapper.readTree("{}")
        `when`(
            client2.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(missingListJson))
        assertTrue(svc2.defineAll("term").isEmpty())
    }

    @Test
    fun `defineAll returns empty list on exception`() {
        val (svc, client) = buildServiceWithMock()
        `when`(
            client.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenThrow(RuntimeException("server boom"))

        val results = svc.defineAll("term")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `define returns first result or null`() {
        val (svc1, client1) = buildServiceWithMock()
        val json: JsonNode = mapper.readTree(
            """
            {"list":[{"word":"w","definition":"d","example":"e","author":"a","permalink":"p"}]}
            """.trimIndent()
        )
        `when`(
            client1.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(json))
        val def = svc1.define("x")
        assertNotNull(def)
        assertEquals("w", def!!.word)

        val (svc2, client2) = buildServiceWithMock()
        val emptyJson: JsonNode = mapper.readTree("{\"list\":[]}")
        `when`(
            client2.exchange(
                Mockito.any(URI::class.java),
                Mockito.any(HttpMethod::class.java),
                Mockito.any(HttpEntity::class.java),
                Mockito.eq(JsonNode::class.java)
            )
        ).thenReturn(ResponseEntity.ok(emptyJson))
        val none = svc2.define("x")
        assertNull(none)
    }
}
