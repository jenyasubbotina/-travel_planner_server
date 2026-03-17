package com.travelplanner.application.usecase.sync

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.TripSnapshot
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.SyncRepository
import java.util.UUID

class GetSnapshotUseCase(
    private val participantRepository: ParticipantRepository,
    private val syncRepository: SyncRepository
) {

    suspend fun execute(tripId: UUID, userId: UUID): TripSnapshot {
        if (!participantRepository.isParticipant(tripId, userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        return syncRepository.getTripSnapshot(tripId)
            ?: throw DomainException.TripNotFound(tripId)
    }
}
