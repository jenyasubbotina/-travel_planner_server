package com.travelplanner.api.routes

import com.travelplanner.api.dto.response.toHistoryEntryResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.application.usecase.history.GetTripHistoryUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.historyRoutes() {
    val getTripHistoryUseCase by inject<GetTripHistoryUseCase>()

    authenticate("auth-jwt") {
        route("/api/v1/trips/{tripId}/history") {
            get {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 200
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val events = getTripHistoryUseCase.execute(
                    GetTripHistoryUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        limit = limit,
                        offset = offset,
                    )
                )
                call.respond(HttpStatusCode.OK, events.map { it.toHistoryEntryResponse() })
            }
        }
    }
}
