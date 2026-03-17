package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sse.*

fun Application.configureRouting() {
    install(SSE)
}

suspend fun ApplicationCall.withTripAccess(
    repository: TripRepository,
    allowArchived: Boolean = false,
    block: suspend (tripId: Long, userId: String) -> Unit
) {
    val tripId = parameters["tripId"]?.toLongOrNull()
    val userId = request.headers["X-User-Id"]

    if (tripId == null || userId == null) {
        respond(HttpStatusCode.BadRequest, "Missing tripId or X-User-Id")
        return
    }

    if (!repository.isUserInTrip(tripId, userId)) {
        respond(HttpStatusCode.Forbidden, "You are not a member of this trip")
        return
    }

    if (!allowArchived && request.httpMethod != HttpMethod.Get && repository.isTripArchived(tripId)) {
        respond(HttpStatusCode.Forbidden, "This trip is archived and cannot be modified")
        return
    }

    block(tripId, userId)
}

suspend fun ApplicationCall.withTripOwner(
    repository: TripRepository,
    allowArchived: Boolean = false,
    block: suspend (tripId: Long, ownerId: String) -> Unit
) {
    val tripId = parameters["tripId"]?.toLongOrNull()
    val userId = request.headers["X-User-Id"]

    if (tripId == null || userId == null) {
        respond(HttpStatusCode.BadRequest, "Missing tripId or X-User-Id")
        return
    }

    if (!repository.isTripOwner(tripId, userId)) {
        respond(HttpStatusCode.Forbidden, "Only the owner can perform this action")
        return
    }

    if (!allowArchived && repository.isTripArchived(tripId)) {
        respond(HttpStatusCode.Forbidden, "This trip is archived and cannot be modified")
        return
    }

    block(tripId, userId)
}
