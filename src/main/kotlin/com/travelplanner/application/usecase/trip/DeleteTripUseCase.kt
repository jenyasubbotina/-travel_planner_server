package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import java.time.Instant
import java.util.UUID

class DeleteTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository
) {

    suspend fun execute(tripId: UUID, userId: UUID) {
        val trip = tripRepository.findById(tripId)
            ?: throw DomainException.TripNotFound(tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(tripId)
        }

        val participant = participantRepository.findByTripAndUser(tripId, userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canManageParticipants()) {
            throw DomainException.InsufficientRole("OWNER")
        }

        tripRepository.softDelete(tripId, Instant.now())
    }
}
