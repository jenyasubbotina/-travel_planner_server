package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.LoginRequest
import com.travelplanner.api.dto.request.RefreshTokenRequest
import com.travelplanner.api.dto.request.RegisterRequest
import com.travelplanner.api.dto.response.AuthResponse
import com.travelplanner.api.dto.response.UserResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.application.usecase.auth.LoginUseCase
import com.travelplanner.application.usecase.auth.LogoutUseCase
import com.travelplanner.application.usecase.auth.RefreshTokenUseCase
import com.travelplanner.application.usecase.auth.RegisterUseCase
import com.travelplanner.domain.model.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val registerUseCase by inject<RegisterUseCase>()
    val loginUseCase by inject<LoginUseCase>()
    val refreshTokenUseCase by inject<RefreshTokenUseCase>()
    val logoutUseCase by inject<LogoutUseCase>()

    route("/api/v1/auth") {
        post("/register") {
            val req = call.receive<RegisterRequest>()
            val result = registerUseCase.execute(
                RegisterUseCase.Input(
                    email = req.email,
                    displayName = req.displayName,
                    password = req.password
                )
            )
            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    user = result.user.toResponse()
                )
            )
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val result = loginUseCase.execute(
                LoginUseCase.Input(
                    email = req.email,
                    password = req.password
                )
            )
            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken,
                    user = result.user.toResponse()
                )
            )
        }

        post("/refresh") {
            val req = call.receive<RefreshTokenRequest>()
            val result = refreshTokenUseCase.execute(
                RefreshTokenUseCase.Input(refreshToken = req.refreshToken)
            )
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "accessToken" to result.accessToken,
                    "refreshToken" to result.refreshToken
                )
            )
        }

        authenticate("auth-jwt") {
            post("/logout") {
                val userId = currentUserId()
                logoutUseCase.execute(LogoutUseCase.Input(userId = userId))
                call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out successfully"))
            }
        }
    }
}

private fun User.toResponse() = UserResponse(
    id = id.toString(),
    email = email,
    displayName = displayName,
    avatarUrl = avatarUrl,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString()
)
