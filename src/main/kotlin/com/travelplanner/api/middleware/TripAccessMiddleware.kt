package com.travelplanner.api.middleware

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.repository.ParticipantRepository
import io.ktor.server.routing.RoutingContext
import java.util.UUID

suspend fun RoutingContext.requireTripParticipant(
    participantRepository: ParticipantRepository,
    tripId: UUID,
    userId: UUID
): TripParticipant {
    return participantRepository.findByTripAndUser(tripId, userId)
        ?: throw DomainException.AccessDenied("Not a participant of this trip")
}

suspend fun RoutingContext.requireTripRole(
    participantRepository: ParticipantRepository,
    tripId: UUID,
    userId: UUID,
    minRole: TripRole
): TripParticipant {
    val participant = requireTripParticipant(participantRepository, tripId, userId)
    val allowed = when (minRole) {
        TripRole.VIEWER -> true
        TripRole.EDITOR -> participant.role.canEdit()
        TripRole.OWNER -> participant.role.canManageParticipants()
    }
    if (!allowed) {
        throw DomainException.InsufficientRole(minRole.name)
    }
    return participant
}

fun RoutingContext.tripIdParam(): UUID {
    val raw = call.parameters["tripId"]
        ?: throw DomainException.ValidationError("Missing tripId")
    return try {
        UUID.fromString(raw)
    } catch (e: IllegalArgumentException) {
        throw DomainException.ValidationError("Invalid tripId format")
    }
}

fun RoutingContext.uuidParam(name: String): UUID {
    val raw = call.parameters[name]
        ?: throw DomainException.ValidationError("Missing $name")
    return try {
        UUID.fromString(raw)
    } catch (e: IllegalArgumentException) {
        throw DomainException.ValidationError("Invalid $name format")
    }
}
