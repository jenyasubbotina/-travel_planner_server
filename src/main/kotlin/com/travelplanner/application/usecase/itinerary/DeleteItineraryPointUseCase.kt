package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.Instant
import java.util.UUID

class DeleteItineraryPointUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository
) {

    data class Input(val pointId: UUID, val tripId: UUID, val userId: UUID)

    suspend fun execute(input: Input) {
        val point = itineraryRepository.findById(input.pointId)
            ?: throw DomainException.ItineraryPointNotFound(input.pointId)

        if (point.deletedAt != null) {
            throw DomainException.ItineraryPointNotFound(input.pointId)
        }

        if (point.tripId != input.tripId) {
            throw DomainException.ItineraryPointNotFound(input.pointId)
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        itineraryRepository.softDelete(input.pointId, Instant.now())
    }
}
