package com.travelplanner.application.usecase.sync

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.SyncDelta
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.SyncRepository
import java.time.Instant
import java.util.UUID

class GetDeltaSyncUseCase(
    private val participantRepository: ParticipantRepository,
    private val syncRepository: SyncRepository
) {

    data class Input(val tripId: UUID, val userId: UUID, val cursor: Instant)

    suspend fun execute(input: Input): SyncDelta {
        if (!participantRepository.isParticipant(input.tripId, input.userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        return syncRepository.getDelta(input.tripId, input.cursor)
    }
}
