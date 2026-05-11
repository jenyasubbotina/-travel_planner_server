package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.InvitationStatus
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.UserRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class DeclineInvitationUseCase(
    private val participantRepository: ParticipantRepository,
    private val userRepository: UserRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    suspend fun execute(invitationId: UUID, userId: UUID): Unit = transactionRunner.runInTransaction {
        val user = userRepository.findById(userId)
            ?: throw DomainException.UserNotFound(userId)

        val invitation = participantRepository.findInvitationById(invitationId)
            ?: throw DomainException.InvitationNotFound(invitationId)

        if (invitation.status != InvitationStatus.PENDING) {
            throw DomainException.InvitationAlreadyResolved(invitationId)
        }

        if (!user.email.equals(invitation.email, ignoreCase = true)) {
            throw DomainException.AccessDenied("This invitation was sent to a different email address")
        }

        val now = Instant.now()
        participantRepository.updateInvitation(
            invitation.copy(status = InvitationStatus.DECLINED, resolvedAt = now)
        )

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "INVITATION_DECLINED",
                aggregateType = "TRIP",
                aggregateId = invitation.tripId,
                payload = HistoryPayload.build(
                    actorUserId = userId,
                    entityType = HistoryPayload.EntityType.PARTICIPANT,
                    entityId = userId,
                    actionType = "DECLINE_INVITATION",
                    context = buildJsonObject {
                        put("invitationId", invitation.id.toString())
                        put("declinerEmail", invitation.email)
                    },
                ),
                createdAt = now,
            )
        )
    }
}
