package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.time.Instant
import java.util.UUID

class DeleteItineraryPointUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(val pointId: UUID, val tripId: UUID, val userId: UUID)

    suspend fun execute(input: Input) = transactionRunner.runInTransaction {
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

        val now = Instant.now()
        itineraryRepository.softDelete(input.pointId, now)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ITINERARY_POINT_DELETED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.EVENT,
                    entityId = input.pointId,
                    actionType = HistoryPayload.ActionType.DELETE,
                    entity = HistoryPayload.eventSnapshot(point),
                ),
                createdAt = now
            )
        )
        Unit
    }
}
