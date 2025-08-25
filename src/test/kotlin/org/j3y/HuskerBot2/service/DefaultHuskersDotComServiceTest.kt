package org.j3y.HuskerBot2.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate

class DefaultHuskersDotComServiceTest {

    private val mapper = ObjectMapper()

    @Test
    fun `constructor sets HttpComponentsClientHttpRequestFactory on RestTemplate`() {
        Mockito.mockConstruction(RestTemplate::class.java).use { construction ->
            // When service is constructed, it should call setRequestFactory with HttpComponentsClientHttpRequestFactory
            DefaultHuskersDotComService()

            val constructed = construction.constructed()
            assertEquals(1, constructed.size, "Expected one RestTemplate constructed")
            val mockRt = constructed[0]
            Mockito.verify(mockRt).setRequestFactory(ArgumentMatchers.isA(HttpComponentsClientHttpRequestFactory::class.java))
        }
    }

    @Test
    fun `getSchedule builds URL with mapped schedId and returns Json`() {
        val body: JsonNode = mapper.readTree("""{"data":[{"id":1}]}""")

        Mockito.mockConstruction(RestTemplate::class.java) { mock, _ ->
            `when`(mock.getForObject(ArgumentMatchers.anyString(), ArgumentMatchers.eq(JsonNode::class.java))).thenReturn(body)
        }.use { construction: MockedConstruction<RestTemplate> ->
            val svc = DefaultHuskersDotComService()

            val result = svc.getSchedule(2025)
            assertEquals(body, result)

            val mockRt = construction.constructed()[0]
            val urlCaptor = ArgumentCaptor.forClass(String::class.java)
            Mockito.verify(mockRt).getForObject(urlCaptor.capture(), ArgumentMatchers.eq(JsonNode::class.java))
            val calledUrl = urlCaptor.value

            assertTrue(calledUrl.contains("schedule-events"), "URL should target schedule-events endpoint: $calledUrl")
            // The parameter name may be encoded, but the value 241 must appear
            assertTrue(calledUrl.contains("241"), "Mapped schedule id 241 should appear in URL: $calledUrl")
        }
    }

    @Test
    fun `getSchedule with unmapped year uses null expansion and still returns when response non-null`() {
        val body: JsonNode = mapper.readTree("""{"data":[]}""")

        Mockito.mockConstruction(RestTemplate::class.java) { mock, _ ->
            `when`(mock.getForObject(ArgumentMatchers.anyString(), ArgumentMatchers.eq(JsonNode::class.java))).thenReturn(body)
        }.use { construction ->
            val svc = DefaultHuskersDotComService()

            val result = svc.getSchedule(1990)
            assertEquals(body, result)

            val mockRt = construction.constructed()[0]
            val urlCaptor = ArgumentCaptor.forClass(String::class.java)
            Mockito.verify(mockRt).getForObject(urlCaptor.capture(), ArgumentMatchers.eq(JsonNode::class.java))
            val calledUrl = urlCaptor.value

            // When no mapping exists, UriComponentsBuilder will expand to an empty value for the template variable
            assertTrue(calledUrl.contains("filter[schedule_id]="), "Expected empty expansion for unmapped year in URL: $calledUrl")
        }
    }

    @Test
    fun `getSchedule throws when RestTemplate returns null`() {
        Mockito.mockConstruction(RestTemplate::class.java) { mock, _ ->
            `when`(mock.getForObject(ArgumentMatchers.anyString(), ArgumentMatchers.eq(JsonNode::class.java))).thenReturn(null)
        }.use {
            val svc = DefaultHuskersDotComService()
            val ex = assertThrows(RuntimeException::class.java) {
                svc.getSchedule(2026)
            }
            assertTrue(ex.message!!.contains("Unable to retrieve schedule for year 2026"))
        }
    }
}
