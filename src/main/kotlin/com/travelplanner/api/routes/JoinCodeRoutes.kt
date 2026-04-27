package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.JoinByCodeRequest
import com.travelplanner.api.dto.response.JoinRequestUserResponse
import com.travelplanner.api.dto.response.RegenerateCodeResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.joincode.ListJoinRequestsUseCase
import com.travelplanner.application.usecase.joincode.RegenerateJoinCodeUseCase
import com.travelplanner.application.usecase.joincode.RequestJoinByCodeUseCase
import com.travelplanner.application.usecase.joincode.ResolveJoinRequestUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.joinCodeRoutes() {
    val requestJoinByCodeUseCase by inject<RequestJoinByCodeUseCase>()
    val regenerateJoinCodeUseCase by inject<RegenerateJoinCodeUseCase>()
    val listJoinRequestsUseCase by inject<ListJoinRequestsUseCase>()
    val resolveJoinRequestUseCase by inject<ResolveJoinRequestUseCase>()

    authenticate("auth-jwt") {
        route("/api/v1/trips") {
            post("/join-request") {
                val userId = currentUserId()
                val req = call.receive<JoinByCodeRequest>()
                val trip = requestJoinByCodeUseCase.execute(
                    RequestJoinByCodeUseCase.Input(code = req.code, requesterUserId = userId)
                )
                call.respond(HttpStatusCode.OK, trip.toResponse())
            }

            route("/{tripId}") {
                post("/regenerate-code") {
                    val userId = currentUserId()
                    val tripId = tripIdParam()
                    val newCode = regenerateJoinCodeUseCase.execute(
                        RegenerateJoinCodeUseCase.Input(tripId = tripId, userId = userId)
                    )
                    call.respond(HttpStatusCode.OK, RegenerateCodeResponse(newCode = newCode))
                }

                get("/requests") {
                    val userId = currentUserId()
                    val tripId = tripIdParam()
                    val pending = listJoinRequestsUseCase.execute(
                        ListJoinRequestsUseCase.Input(tripId = tripId, userId = userId)
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        pending.map {
                            JoinRequestUserResponse(
                                userId = it.userId.toString(),
                                displayName = it.user.displayName,
                                email = it.user.email,
                            )
                        }
                    )
                }

                post("/requests/{userId}/resolve") {
                    val resolverUserId = currentUserId()
                    val tripId = tripIdParam()
                    val targetUserId = uuidParam("userId")
                    val approve = call.request.queryParameters["approve"]?.toBoolean() ?: false
                    resolveJoinRequestUseCase.execute(
                        ResolveJoinRequestUseCase.Input(
                            tripId = tripId,
                            requesterUserId = targetUserId,
                            resolverUserId = resolverUserId,
                            approve = approve,
                        )
                    )
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
