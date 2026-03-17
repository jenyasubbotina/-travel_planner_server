package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.CreateTripRequest
import com.travelplanner.api.dto.request.UpdateTripRequest
import com.travelplanner.api.dto.response.TripResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.application.usecase.trip.ArchiveTripUseCase
import com.travelplanner.application.usecase.trip.CreateTripUseCase
import com.travelplanner.application.usecase.trip.DeleteTripUseCase
import com.travelplanner.application.usecase.trip.GetTripUseCase
import com.travelplanner.application.usecase.trip.ListUserTripsUseCase
import com.travelplanner.application.usecase.trip.UpdateTripUseCase
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripStatus
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

fun Route.tripRoutes() {
    val createTripUseCase by inject<CreateTripUseCase>()
    val updateTripUseCase by inject<UpdateTripUseCase>()
    val getTripUseCase by inject<GetTripUseCase>()
    val listUserTripsUseCase by inject<ListUserTripsUseCase>()
    val archiveTripUseCase by inject<ArchiveTripUseCase>()
    val deleteTripUseCase by inject<DeleteTripUseCase>()

    authenticate("auth-jwt") {
        route("/api/v1/trips") {
            get {
                val userId = currentUserId()
                val trips = listUserTripsUseCase.execute(userId)
                call.respond(HttpStatusCode.OK, trips.map { it.toResponse() })
            }

            post {
                val userId = currentUserId()
                val req = call.receive<CreateTripRequest>()
                val trip = createTripUseCase.execute(
                    CreateTripUseCase.Input(
                        title = req.title,
                        description = req.description,
                        startDate = req.startDate?.let { LocalDate.parse(it) },
                        endDate = req.endDate?.let { LocalDate.parse(it) },
                        baseCurrency = req.baseCurrency,
                        userId = userId
                    )
                )
                call.respond(HttpStatusCode.Created, trip.toResponse())
            }

            route("/{tripId}") {
                get {
                    val userId = currentUserId()
                    val tripId = tripIdParam()
                    val trip = getTripUseCase.execute(tripId, userId)
                    call.respond(HttpStatusCode.OK, trip.toResponse())
                }

                patch {
                    val userId = currentUserId()
                    val tripId = tripIdParam()
                    val req = call.receive<UpdateTripRequest>()
                    val trip = updateTripUseCase.execute(
                        UpdateTripUseCase.Input(
                            tripId = tripId,
                            userId = userId,
                            title = req.title,
                            description = req.description,
                            startDate = req.startDate?.let { LocalDate.parse(it) },
                            endDate = req.endDate?.let { LocalDate.parse(it) },
                            baseCurrency = req.baseCurrency,
                            status = req.status?.let { TripStatus.valueOf(it) },
                            expectedVersion = req.expectedVersion ?: 0L
                        )
                    )
                    call.respond(HttpStatusCode.OK, trip.toResponse())
                }

                delete {
                    val userId = currentUserId()
                    val tripId = tripIdParam()
                    deleteTripUseCase.execute(tripId, userId)
                    call.respond(HttpStatusCode.NoContent)
                }

                post("/archive") {
                    val userId = currentUserId()
                    val tripId = tripIdParam()
                    val trip = archiveTripUseCase.execute(tripId, userId)
                    call.respond(HttpStatusCode.OK, trip.toResponse())
                }
            }
        }
    }
}

private fun Trip.toResponse() = TripResponse(
    id = id.toString(),
    title = title,
    description = description,
    startDate = startDate?.toString(),
    endDate = endDate?.toString(),
    baseCurrency = baseCurrency,
    status = status.name,
    createdBy = createdBy.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    version = version,
    deletedAt = deletedAt?.toString()
)
