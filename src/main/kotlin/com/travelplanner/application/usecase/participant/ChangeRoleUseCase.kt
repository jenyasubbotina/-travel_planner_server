package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.repository.UserRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class ChangeRoleUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val userRepository: UserRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(
        val tripId: UUID,
        val requesterUserId: UUID,
        val targetUserId: UUID,
        val newRole: TripRole
    )

    suspend fun execute(input: Input) = transactionRunner.runInTransaction {
        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(input.tripId)
        }

        val requester = participantRepository.findByTripAndUser(input.tripId, input.requesterUserId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!requester.role.canManageParticipants()) {
            throw DomainException.InsufficientRole("OWNER")
        }

        if (input.requesterUserId == input.targetUserId) {
            throw DomainException.ValidationError("Cannot change your own role")
        }

        if (input.newRole == TripRole.OWNER) {
            throw DomainException.ValidationError("Cannot assign OWNER role. There can only be one owner.")
        }

        val target = participantRepository.findByTripAndUser(input.tripId, input.targetUserId)
            ?: throw DomainException.ParticipantNotInTrip(input.targetUserId, input.tripId)

        val previousRole = target.role
        val targetUser = userRepository.findById(input.targetUserId)

        participantRepository.updateRole(input.tripId, input.targetUserId, input.newRole)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "PARTICIPANT_UPDATED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.requesterUserId,
                    entityType = HistoryPayload.EntityType.PARTICIPANT,
                    entityId = input.targetUserId,
                    actionType = HistoryPayload.ActionType.CHANGE_ROLE,
                    context = buildJsonObject {
                        put("participantUserId", input.targetUserId.toString())
                        put("participantName", targetUser?.displayName ?: "Someone")
                        put("oldRole", previousRole.name)
                        put("newRole", input.newRole.name)
                    },
                ),
                createdAt = Instant.now()
            )
        )
        Unit
    }
}
