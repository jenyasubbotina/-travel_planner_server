package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ItineraryPointComment
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.util.UUID

class ListItineraryPointCommentsUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val pointId: UUID,
        val userId: UUID,
        val limit: Int = 100,
        val offset: Int = 0,
    )

    suspend fun execute(input: Input): List<ItineraryPointComment> = transactionRunner.runInTransaction {
        val point = itineraryRepository.findById(input.pointId)
            ?: throw DomainException.ItineraryPointNotFound(input.pointId)

        participantRepository.findByTripAndUser(point.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        val sanitizedLimit = input.limit.coerceIn(1, 500)
        val sanitizedOffset = input.offset.coerceAtLeast(0)
        itineraryRepository.findComments(input.pointId, sanitizedLimit, sanitizedOffset)
    }
}
