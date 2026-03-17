package com.travelplanner.api.middleware

import com.travelplanner.domain.exception.DomainException
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.RoutingContext
import java.util.UUID

fun RoutingContext.currentUserId(): UUID {
    val principal = call.principal<JWTPrincipal>()
        ?: throw DomainException.AccessDenied("No authentication principal")
    val sub = principal.subject
        ?: throw DomainException.AccessDenied("No subject in token")
    return UUID.fromString(sub)
}
