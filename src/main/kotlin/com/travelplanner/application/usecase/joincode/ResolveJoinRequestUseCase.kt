package com.travelplanner.application.usecase.joincode

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.JoinRequestStatus
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.JoinRequestRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class ResolveJoinRequestUseCase(
    private val participantRepository: ParticipantRepository,
    private val joinRequestRepository: JoinRequestRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val tripId: UUID,
        val requesterUserId: UUID,
        val resolverUserId: UUID,
        val approve: Boolean,
    )

    suspend fun execute(input: Input): Unit = transactionRunner.runInTransaction {
        val resolver = participantRepository.findByTripAndUser(input.tripId, input.resolverUserId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!resolver.role.canManageParticipants()) {
            throw DomainException.InsufficientRole("OWNER")
        }

        val pending = joinRequestRepository.findPendingByTripAndUser(input.tripId, input.requesterUserId)
            ?: throw DomainException.ValidationError("No pending join request for that user")

        val now = Instant.now()
        if (input.approve) {
            participantRepository.add(
                TripParticipant(
                    tripId = input.tripId,
                    userId = input.requesterUserId,
                    role = TripRole.EDITOR,
                    joinedAt = now,
                )
            )
            joinRequestRepository.updateStatus(pending.id, JoinRequestStatus.APPROVED, input.resolverUserId, now)
            domainEventRepository.save(
                DomainEvent(
                    id = UUID.randomUUID(),
                    eventType = "PARTICIPANT_ADDED",
                    aggregateType = "TRIP",
                    aggregateId = input.tripId,
                    payload = HistoryPayload.build(
                        actorUserId = input.resolverUserId,
                        entityType = HistoryPayload.EntityType.JOIN_REQUEST,
                        entityId = pending.id,
                        actionType = HistoryPayload.ActionType.APPROVE_JOIN,
                        context = buildJsonObject {
                            put("via", "JOIN_CODE")
                            put("participantUserId", input.requesterUserId.toString())
                        },
                    ),
                    createdAt = now,
                )
            )
        } else {
            joinRequestRepository.updateStatus(pending.id, JoinRequestStatus.DENIED, input.resolverUserId, now)
            domainEventRepository.save(
                DomainEvent(
                    id = UUID.randomUUID(),
                    eventType = "JOIN_REQUEST_DENIED",
                    aggregateType = "TRIP",
                    aggregateId = input.tripId,
                    payload = HistoryPayload.build(
                        actorUserId = input.resolverUserId,
                        entityType = HistoryPayload.EntityType.JOIN_REQUEST,
                        entityId = pending.id,
                        actionType = HistoryPayload.ActionType.DENY_JOIN,
                        context = buildJsonObject { put("requesterUserId", input.requesterUserId.toString()) },
                    ),
                    createdAt = now,
                )
            )
        }
    }
}
