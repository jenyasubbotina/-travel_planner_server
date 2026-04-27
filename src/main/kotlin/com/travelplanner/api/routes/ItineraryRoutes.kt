package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.AddPointCommentRequest
import com.travelplanner.api.dto.request.AddPointLinkRequest
import com.travelplanner.api.dto.request.CreateItineraryPointRequest
import com.travelplanner.api.dto.request.ReorderRequest
import com.travelplanner.api.dto.request.UpdateItineraryPointRequest
import com.travelplanner.api.dto.response.ItineraryPointResponse
import com.travelplanner.api.dto.response.PointCommentResponse
import com.travelplanner.api.dto.response.PointLinkResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.requireTripParticipant
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.itinerary.AddItineraryPointCommentUseCase
import com.travelplanner.application.usecase.itinerary.AddItineraryPointLinkUseCase
import com.travelplanner.application.usecase.itinerary.CreateItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.DeleteItineraryPointLinkUseCase
import com.travelplanner.application.usecase.itinerary.DeleteItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.ListItineraryPointCommentsUseCase
import com.travelplanner.application.usecase.itinerary.ListItineraryPointLinksUseCase
import com.travelplanner.application.usecase.itinerary.ReorderItineraryUseCase
import com.travelplanner.application.usecase.itinerary.UpdateItineraryPointUseCase
import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.ItineraryPointComment
import com.travelplanner.domain.model.ItineraryPointLink
import com.travelplanner.domain.model.ItineraryPointStatus
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.UserRepository
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
    val addItineraryPointLinkUseCase by inject<AddItineraryPointLinkUseCase>()
    val deleteItineraryPointLinkUseCase by inject<DeleteItineraryPointLinkUseCase>()
    val listItineraryPointLinksUseCase by inject<ListItineraryPointLinksUseCase>()
    val addItineraryPointCommentUseCase by inject<AddItineraryPointCommentUseCase>()
    val listItineraryPointCommentsUseCase by inject<ListItineraryPointCommentsUseCase>()
    val itineraryRepository by inject<ItineraryRepository>()
    val participantRepository by inject<ParticipantRepository>()
    val userRepository by inject<UserRepository>()

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
                        category = req.category,
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
                        category = req.category,
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

            route("/{pointId}/links") {
                get {
                    val userId = currentUserId()
                    val pointId = uuidParam("pointId")
                    val links = listItineraryPointLinksUseCase.execute(
                        ListItineraryPointLinksUseCase.Input(pointId = pointId, userId = userId)
                    )
                    call.respond(HttpStatusCode.OK, links.map { it.toResponse() })
                }

                post {
                    val userId = currentUserId()
                    val pointId = uuidParam("pointId")
                    val req = call.receive<AddPointLinkRequest>()
                    val link = addItineraryPointLinkUseCase.execute(
                        AddItineraryPointLinkUseCase.Input(
                            pointId = pointId,
                            userId = userId,
                            title = req.title,
                            url = req.url,
                        )
                    )
                    call.respond(HttpStatusCode.Created, link.toResponse())
                }

                delete("/{linkId}") {
                    val userId = currentUserId()
                    val pointId = uuidParam("pointId")
                    val linkId = uuidParam("linkId")
                    deleteItineraryPointLinkUseCase.execute(
                        DeleteItineraryPointLinkUseCase.Input(
                            pointId = pointId,
                            linkId = linkId,
                            userId = userId,
                        )
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            route("/{pointId}/comments") {
                get {
                    val userId = currentUserId()
                    val pointId = uuidParam("pointId")
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val comments = listItineraryPointCommentsUseCase.execute(
                        ListItineraryPointCommentsUseCase.Input(
                            pointId = pointId,
                            userId = userId,
                            limit = limit,
                            offset = offset,
                        )
                    )
                    val authorIds = comments.map { it.authorUserId }.distinct()
                    val authorNames = authorIds.associateWith { id ->
                        userRepository.findById(id)?.displayName ?: ""
                    }
                    call.respond(HttpStatusCode.OK, comments.map { it.toResponse(authorNames[it.authorUserId].orEmpty()) })
                }

                post {
                    val userId = currentUserId()
                    val pointId = uuidParam("pointId")
                    val req = call.receive<AddPointCommentRequest>()
                    val comment = addItineraryPointCommentUseCase.execute(
                        AddItineraryPointCommentUseCase.Input(
                            pointId = pointId,
                            userId = userId,
                            text = req.text,
                        )
                    )
                    val authorName = userRepository.findById(comment.authorUserId)?.displayName ?: ""
                    call.respond(HttpStatusCode.Created, comment.toResponse(authorName))
                }
            }
        }
    }
}

private fun ItineraryPointLink.toResponse() = PointLinkResponse(
    id = id.toString(),
    pointId = pointId.toString(),
    title = title,
    url = url,
    sortOrder = sortOrder,
    createdAt = createdAt.toString(),
)

private fun ItineraryPointComment.toResponse(authorDisplayName: String) = PointCommentResponse(
    id = id.toString(),
    pointId = pointId.toString(),
    authorUserId = authorUserId.toString(),
    authorDisplayName = authorDisplayName,
    text = text,
    createdAt = createdAt.toString(),
)

internal fun ItineraryPoint.toResponse() = ItineraryPointResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    title = title,
    description = description,
    subtitle = subtitle,
    type = type,
    category = category,
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
