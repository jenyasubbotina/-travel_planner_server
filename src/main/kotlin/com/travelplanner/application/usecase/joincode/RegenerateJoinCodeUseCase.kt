package com.travelplanner.application.usecase.joincode

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.infrastructure.util.JoinCodeGenerator
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class RegenerateJoinCodeUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(val tripId: UUID, val userId: UUID)

    suspend fun execute(input: Input): String = transactionRunner.runInTransaction {
        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!participant.role.canManageParticipants()) {
            throw DomainException.InsufficientRole("OWNER")
        }

        val newCode = generateUnique()
        tripRepository.update(trip.copy(joinCode = newCode))

        val now = Instant.now()
        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "TRIP_JOIN_CODE_REGENERATED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.TRIP,
                    entityId = input.tripId,
                    actionType = HistoryPayload.ActionType.REGENERATE_JOIN_CODE,
                    context = buildJsonObject { put("tripTitle", trip.title) },
                ),
                createdAt = now,
            )
        )

        newCode
    }

    private suspend fun generateUnique(): String {
        repeat(5) {
            val candidate = JoinCodeGenerator.generate()
            if (tripRepository.findByJoinCode(candidate) == null) return candidate
        }
        return JoinCodeGenerator.generate()
    }
}
