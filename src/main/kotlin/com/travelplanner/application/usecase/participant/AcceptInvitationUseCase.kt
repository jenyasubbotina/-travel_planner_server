package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.UserRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class AcceptInvitationUseCase(
    private val participantRepository: ParticipantRepository,
    private val userRepository: UserRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    suspend fun execute(invitationId: UUID, userId: UUID): TripParticipant = transactionRunner.runInTransaction {
        val user = userRepository.findById(userId)
            ?: throw DomainException.UserNotFound(userId)

        val participant = participantRepository.acceptInvitation(invitationId, user)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "PARTICIPANT_ADDED",
                aggregateType = "TRIP",
                aggregateId = participant.tripId,
                payload = buildJsonObject {
                    put("actorUserId", userId.toString())
                    put("participantUserId", participant.userId.toString())
                    put("participantName", user.displayName)
                }.toString(),
                createdAt = Instant.now()
            )
        )

        participant
    }
}
