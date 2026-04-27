package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import java.time.Instant
import java.util.UUID

class DeleteTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    suspend fun execute(tripId: UUID, userId: UUID) = transactionRunner.runInTransaction {
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

        val now = Instant.now()
        tripRepository.softDelete(tripId, now)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "TRIP_DELETED",
                aggregateType = "TRIP",
                aggregateId = tripId,
                payload = HistoryPayload.build(
                    actorUserId = userId,
                    entityType = HistoryPayload.EntityType.TRIP,
                    entityId = tripId,
                    actionType = HistoryPayload.ActionType.DELETE,
                    entity = HistoryPayload.tripSnapshot(trip),
                ),
                createdAt = now
            )
        )
        Unit
    }
}
