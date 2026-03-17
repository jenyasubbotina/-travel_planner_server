package com.travelplanner.api.plugins

import com.travelplanner.api.dto.ErrorResponse
import com.travelplanner.infrastructure.auth.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

fun Application.configureAuthentication(jwtService: JwtService) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtService.realm
            verifier(jwtService.getVerifier())
            validate { credential ->
                if (credential.payload.subject != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        code = "UNAUTHENTICATED",
                        message = "Token is invalid or expired"
                    )
                )
            }
        }
    }
}
