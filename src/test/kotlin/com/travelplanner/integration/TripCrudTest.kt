package com.travelplanner.integration

import com.travelplanner.api.dto.ErrorResponse
import com.travelplanner.api.dto.request.CreateTripRequest
import com.travelplanner.api.dto.request.UpdateTripRequest
import com.travelplanner.api.dto.response.TripResponse
import com.travelplanner.application.usecase.trip.ArchiveTripUseCase
import com.travelplanner.application.usecase.trip.CreateTripUseCase
import com.travelplanner.application.usecase.trip.DeleteTripUseCase
import com.travelplanner.application.usecase.trip.GetTripUseCase
import com.travelplanner.application.usecase.trip.ListUserTripsUseCase
import com.travelplanner.application.usecase.trip.UpdateTripUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripStatus
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TripCrudTest : BaseIntegrationTest() {

    private val userId = UUID.randomUUID()
    private val tripId = UUID.randomUUID()
    private val now = Instant.parse("2025-06-01T12:00:00Z")

    private val sampleTrip = Trip(
        id = tripId,
        title = "Summer Vacation",
        description = "A fun trip",
        startDate = LocalDate.parse("2025-07-01"),
        endDate = LocalDate.parse("2025-07-15"),
        baseCurrency = "EUR",
        status = TripStatus.ACTIVE,
        createdBy = userId,
        createdAt = now,
        updatedAt = now,
        version = 1
    )

    private val createTripUseCase = io.mockk.mockk<CreateTripUseCase>()
    private val updateTripUseCase = io.mockk.mockk<UpdateTripUseCase>()
    private val getTripUseCase = io.mockk.mockk<GetTripUseCase>()
    private val listUserTripsUseCase = io.mockk.mockk<ListUserTripsUseCase>()
    private val archiveTripUseCase = io.mockk.mockk<ArchiveTripUseCase>()
    private val deleteTripUseCase = io.mockk.mockk<DeleteTripUseCase>()

    override fun additionalKoinModules(): Module = module {
        single { createTripUseCase }
        single { updateTripUseCase }
        single { getTripUseCase }
        single { listUserTripsUseCase }
        single { archiveTripUseCase }
        single { deleteTripUseCase }
    }

    // ───────────────────── Authentication ─────────────────────

    @Test
    fun `create trip requires authentication`() = testApp { client ->
        val response = client.post("/api/v1/trips") {
            contentType(ContentType.Application.Json)
            setBody(CreateTripRequest(title = "Test"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `list trips requires authentication`() = testApp { client ->
        val response = client.get("/api/v1/trips")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // ───────────────────── Create ─────────────────────

    @Test
    fun `create trip returns 201`() = testApp { client ->
        coEvery { createTripUseCase.execute(any()) } returns sampleTrip
        val token = generateTestToken(userId)

        val response = client.post("/api/v1/trips") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateTripRequest(
                title = "Summer Vacation",
                description = "A fun trip",
                startDate = "2025-07-01",
                endDate = "2025-07-15",
                baseCurrency = "EUR"
            ))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<TripResponse>()
        assertEquals("Summer Vacation", body.title)
        assertEquals("EUR", body.baseCurrency)
        assertEquals("ACTIVE", body.status)
        assertEquals(tripId.toString(), body.id)
    }

    @Test
    fun `create trip with minimal fields returns 201`() = testApp { client ->
        val minimalTrip = sampleTrip.copy(description = null, startDate = null, endDate = null)
        coEvery { createTripUseCase.execute(any()) } returns minimalTrip
        val token = generateTestToken(userId)

        val response = client.post("/api/v1/trips") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateTripRequest(title = "Quick Trip"))
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    // ───────────────────── Get ─────────────────────

    @Test
    fun `get trip returns 200`() = testApp { client ->
        coEvery { getTripUseCase.execute(tripId, userId) } returns sampleTrip
        val token = generateTestToken(userId)

        val response = client.get("/api/v1/trips/$tripId") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<TripResponse>()
        assertEquals(tripId.toString(), body.id)
        assertEquals("Summer Vacation", body.title)
    }

    @Test
    fun `get non-existent trip returns 404`() = testApp { client ->
        val nonExistentId = UUID.randomUUID()
        coEvery { getTripUseCase.execute(nonExistentId, userId) } throws DomainException.TripNotFound(nonExistentId)
        val token = generateTestToken(userId)

        val response = client.get("/api/v1/trips/$nonExistentId") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.body<ErrorResponse>()
        assertEquals("TRIP_NOT_FOUND", body.code)
    }

    @Test
    fun `get trip with invalid UUID returns 422`() = testApp { client ->
        val token = generateTestToken(userId)

        val response = client.get("/api/v1/trips/not-a-uuid") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    // ───────────────────── List ─────────────────────

    @Test
    fun `list trips returns 200 with trip list`() = testApp { client ->
        val trip2 = sampleTrip.copy(id = UUID.randomUUID(), title = "Another Trip")
        coEvery { listUserTripsUseCase.execute(userId) } returns listOf(sampleTrip, trip2)
        val token = generateTestToken(userId)

        val response = client.get("/api/v1/trips") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<TripResponse>>()
        assertEquals(2, body.size)
    }

    @Test
    fun `list trips returns empty array when no trips`() = testApp { client ->
        coEvery { listUserTripsUseCase.execute(userId) } returns emptyList()
        val token = generateTestToken(userId)

        val response = client.get("/api/v1/trips") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<TripResponse>>()
        assertEquals(0, body.size)
    }

    // ───────────────────── Update ─────────────────────

    @Test
    fun `update trip returns 200`() = testApp { client ->
        val updatedTrip = sampleTrip.copy(title = "Updated Title", version = 2)
        coEvery { updateTripUseCase.execute(any()) } returns updatedTrip
        val token = generateTestToken(userId)

        val response = client.patch("/api/v1/trips/$tripId") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateTripRequest(title = "Updated Title", expectedVersion = 1))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<TripResponse>()
        assertEquals("Updated Title", body.title)
        assertEquals(2, body.version)
    }

    @Test
    fun `update trip with version conflict returns 409`() = testApp { client ->
        coEvery { updateTripUseCase.execute(any()) } throws DomainException.VersionConflict("Trip", tripId)
        val token = generateTestToken(userId)

        val response = client.patch("/api/v1/trips/$tripId") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(UpdateTripRequest(title = "Updated Title", expectedVersion = 0))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.body<ErrorResponse>()
        assertEquals("VERSION_CONFLICT", body.code)
    }

    // ───────────────────── Archive ─────────────────────

    @Test
    fun `archive trip returns 200`() = testApp { client ->
        val archivedTrip = sampleTrip.copy(status = TripStatus.ARCHIVED)
        coEvery { archiveTripUseCase.execute(tripId, userId) } returns archivedTrip
        val token = generateTestToken(userId)

        val response = client.post("/api/v1/trips/$tripId/archive") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<TripResponse>()
        assertEquals("ARCHIVED", body.status)
    }

    // ───────────────────── Delete ─────────────────────

    @Test
    fun `delete trip returns 204`() = testApp { client ->
        coEvery { deleteTripUseCase.execute(tripId, userId) } returns Unit
        val token = generateTestToken(userId)

        val response = client.delete("/api/v1/trips/$tripId") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `delete trip by non-owner returns 403`() = testApp { client ->
        coEvery { deleteTripUseCase.execute(tripId, userId) } throws DomainException.AccessDenied("Only owner can delete")
        val token = generateTestToken(userId)

        val response = client.delete("/api/v1/trips/$tripId") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = response.body<ErrorResponse>()
        assertEquals("ACCESS_DENIED", body.code)
    }
}
