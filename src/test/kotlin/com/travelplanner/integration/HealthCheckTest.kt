package com.travelplanner.integration

import com.travelplanner.api.dto.response.HealthResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HealthCheckTest : BaseIntegrationTest() {

    @Test
    fun `health live endpoint returns UP`() = testApp { client ->
        val response = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HealthResponse>()
        assertEquals("UP", body.status)
        assertNotNull(body.timestamp)
    }

    @Test
    fun `health ready endpoint returns UP`() = testApp { client ->
        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<HealthResponse>()
        assertEquals("UP", body.status)
        assertNotNull(body.timestamp)
    }

    @Test
    fun `non-existent endpoint returns 404`() = testApp { client ->
        val response = client.get("/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
