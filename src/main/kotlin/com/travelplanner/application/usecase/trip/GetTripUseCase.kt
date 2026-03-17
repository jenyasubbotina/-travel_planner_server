package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import java.util.UUID

class GetTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository
) {

    suspend fun execute(tripId: UUID, userId: UUID): Trip {
        val trip = tripRepository.findById(tripId)
            ?: throw DomainException.TripNotFound(tripId)

        if (!participantRepository.isParticipant(tripId, userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        return trip
    }
}
