package com.travelplanner.application.usecase.joincode

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.JoinRequestStatus
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripJoinRequest
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.JoinRequestRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class RequestJoinByCodeUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val joinRequestRepository: JoinRequestRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(val code: String, val requesterUserId: UUID)

    suspend fun execute(input: Input): Trip = transactionRunner.runInTransaction {
        val normalized = input.code.trim().uppercase()
        if (normalized.isBlank()) {
            throw DomainException.ValidationError("Join code is required")
        }

        val trip = tripRepository.findByJoinCode(normalized)
            ?: throw DomainException.TripNotFound(UUID.fromString("00000000-0000-0000-0000-000000000000"))

        if (participantRepository.isParticipant(trip.id, input.requesterUserId)) {
            return@runInTransaction trip
        }

        val existingPending = joinRequestRepository.findPendingByTripAndUser(trip.id, input.requesterUserId)
        if (existingPending != null) {
            return@runInTransaction trip
        }

        val now = Instant.now()
        val request = TripJoinRequest(
            id = UUID.randomUUID(),
            tripId = trip.id,
            requesterUserId = input.requesterUserId,
            status = JoinRequestStatus.PENDING,
            createdAt = now,
        )
        joinRequestRepository.create(request)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "JOIN_REQUEST_CREATED",
                aggregateType = "TRIP",
                aggregateId = trip.id,
                payload = HistoryPayload.build(
                    actorUserId = input.requesterUserId,
                    entityType = HistoryPayload.EntityType.JOIN_REQUEST,
                    entityId = request.id,
                    actionType = HistoryPayload.ActionType.REQUEST_JOIN,
                    context = buildJsonObject {
                        put("tripTitle", trip.title)
                        put("requesterUserId", input.requesterUserId.toString())
                    },
                ),
                createdAt = now,
            )
        )

        trip
    }
}
