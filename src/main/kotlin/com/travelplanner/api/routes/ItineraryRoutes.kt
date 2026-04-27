package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.CreateItineraryPointRequest
import com.travelplanner.api.dto.request.ReorderRequest
import com.travelplanner.api.dto.request.UpdateItineraryPointRequest
import com.travelplanner.api.dto.response.ItineraryPointResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.requireTripParticipant
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.itinerary.CreateItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.DeleteItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.ReorderItineraryUseCase
import com.travelplanner.application.usecase.itinerary.UpdateItineraryPointUseCase
import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.ItineraryPointStatus
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

fun Route.itineraryRoutes() {
    val createItineraryPointUseCase by inject<CreateItineraryPointUseCase>()
    val updateItineraryPointUseCase by inject<UpdateItineraryPointUseCase>()
    val deleteItineraryPointUseCase by inject<DeleteItineraryPointUseCase>()
    val reorderItineraryUseCase by inject<ReorderItineraryUseCase>()
    val itineraryRepository by inject<ItineraryRepository>()
    val participantRepository by inject<ParticipantRepository>()

    authenticate("auth-jwt") {
        route("/api/v1/trips/{tripId}/itinerary") {
            get {
                val tripId = tripIdParam()
                val userId = currentUserId()
                requireTripParticipant(participantRepository, tripId, userId)
                val points = itineraryRepository.findByTrip(tripId)
                    .filter { it.deletedAt == null }
                    .sortedBy { it.sortOrder }
                call.respond(HttpStatusCode.OK, points.map { it.toResponse() })
            }

            post {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val req = call.receive<CreateItineraryPointRequest>()
                val point = createItineraryPointUseCase.execute(
                    CreateItineraryPointUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        title = req.title,
                        description = req.description,
                        subtitle = req.subtitle,
                        type = req.type,
                        date = req.date?.let { LocalDate.parse(it) },
                        dayIndex = req.dayIndex ?: 0,
                        startTime = req.startTime?.let { LocalTime.parse(it) },
                        endTime = req.endTime?.let { LocalTime.parse(it) },
                        duration = req.duration,
                        latitude = req.latitude,
                        longitude = req.longitude,
                        address = req.address,
                        cost = req.cost,
                        actualCost = req.actualCost,
                        status = req.status?.let { ItineraryPointStatus.valueOf(it) }
                            ?: ItineraryPointStatus.NONE,
                        participantIds = req.participantIds
                            ?.map { UUID.fromString(it) }
                            .orEmpty()
                    )
                )
                call.respond(HttpStatusCode.Created, point.toResponse())
            }

            patch("/{pointId}") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val pointId = uuidParam("pointId")
                val req = call.receive<UpdateItineraryPointRequest>()
                val point = updateItineraryPointUseCase.execute(
                    UpdateItineraryPointUseCase.Input(
                        pointId = pointId,
                        userId = userId,
                        title = req.title,
                        description = req.description,
                        subtitle = req.subtitle,
                        type = req.type,
                        date = req.date?.let { LocalDate.parse(it) },
                        dayIndex = req.dayIndex,
                        startTime = req.startTime?.let { LocalTime.parse(it) },
                        endTime = req.endTime?.let { LocalTime.parse(it) },
                        duration = req.duration,
                        latitude = req.latitude,
                        longitude = req.longitude,
                        address = req.address,
                        cost = req.cost,
                        actualCost = req.actualCost,
                        status = req.status?.let { ItineraryPointStatus.valueOf(it) },
                        participantIds = req.participantIds?.map { UUID.fromString(it) },
                        expectedVersion = req.expectedVersion ?: 0L
                    )
                )
                call.respond(HttpStatusCode.OK, point.toResponse())
            }

            delete("/{pointId}") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val pointId = uuidParam("pointId")
                deleteItineraryPointUseCase.execute(
                    DeleteItineraryPointUseCase.Input(
                        pointId = pointId,
                        tripId = tripId,
                        userId = userId
                    )
                )
                call.respond(HttpStatusCode.NoContent)
            }

            post("/reorder") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val req = call.receive<ReorderRequest>()
                reorderItineraryUseCase.execute(
                    ReorderItineraryUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        orders = req.items.map { item ->
                            ReorderItineraryUseCase.PointOrder(
                                pointId = UUID.fromString(item.id),
                                newSortOrder = item.sortOrder
                            )
                        }
                    )
                )
                call.respond(HttpStatusCode.OK, mapOf("message" to "Reorder successful"))
            }
        }
    }
}

internal fun ItineraryPoint.toResponse() = ItineraryPointResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    title = title,
    description = description,
    subtitle = subtitle,
    type = type,
    date = date?.toString(),
    dayIndex = dayIndex,
    startTime = startTime?.toString(),
    endTime = endTime?.toString(),
    duration = duration,
    latitude = latitude,
    longitude = longitude,
    address = address,
    cost = cost,
    actualCost = actualCost,
    status = status.name,
    participantIds = participantIds.map { it.toString() },
    sortOrder = sortOrder,
    createdBy = createdBy.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    version = version,
    deletedAt = deletedAt?.toString()
)
