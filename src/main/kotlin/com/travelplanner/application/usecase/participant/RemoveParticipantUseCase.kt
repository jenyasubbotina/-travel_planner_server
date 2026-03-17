package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import java.util.UUID

class RemoveParticipantUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository
) {

    data class Input(
        val tripId: UUID,
        val requesterUserId: UUID,
        val targetUserId: UUID
    )

    suspend fun execute(input: Input) {
        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(input.tripId)
        }

        val requester = participantRepository.findByTripAndUser(input.tripId, input.requesterUserId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!requester.role.canManageParticipants()) {
            throw DomainException.InsufficientRole("OWNER")
        }

        if (input.requesterUserId == input.targetUserId) {
            throw DomainException.ValidationError("Owner cannot remove themselves. Delete the trip instead.")
        }

        val target = participantRepository.findByTripAndUser(input.tripId, input.targetUserId)
            ?: throw DomainException.ParticipantNotInTrip(input.targetUserId, input.tripId)

        participantRepository.remove(input.tripId, input.targetUserId)
    }
}
