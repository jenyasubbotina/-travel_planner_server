package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class ArchiveTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    suspend fun execute(tripId: UUID, userId: UUID): Trip = transactionRunner.runInTransaction {
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

        val archived = trip.copy(status = TripStatus.ARCHIVED)

        val saved = tripRepository.update(archived)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "TRIP_UPDATED",
                aggregateType = "TRIP",
                aggregateId = saved.id,
                payload = buildJsonObject {
                    put("actorUserId", userId.toString())
                    put("tripTitle", saved.title)
                }.toString(),
                createdAt = Instant.now()
            )
        )

        saved
    }
}
