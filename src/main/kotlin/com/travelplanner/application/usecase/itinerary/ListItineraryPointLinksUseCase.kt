package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ItineraryPointLink
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.util.UUID

class ListItineraryPointLinksUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(val pointId: UUID, val userId: UUID)

    suspend fun execute(input: Input): List<ItineraryPointLink> = transactionRunner.runInTransaction {
        val point = itineraryRepository.findById(input.pointId)
            ?: throw DomainException.ItineraryPointNotFound(input.pointId)

        participantRepository.findByTripAndUser(point.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        itineraryRepository.findLinks(input.pointId)
    }
}
