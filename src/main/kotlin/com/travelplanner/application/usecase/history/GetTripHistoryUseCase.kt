package com.travelplanner.application.usecase.history

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.util.UUID

class GetTripHistoryUseCase(
    private val participantRepository: ParticipantRepository,
    private val domainEventRepository: DomainEventRepository,
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val limit: Int = 200,
        val offset: Int = 0,
    )

    suspend fun execute(input: Input): List<DomainEvent> {
        participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        val sanitizedLimit = input.limit.coerceIn(1, 500)
        val sanitizedOffset = input.offset.coerceAtLeast(0)
        return domainEventRepository.findByAggregateId(input.tripId, sanitizedLimit, sanitizedOffset)
    }
}
