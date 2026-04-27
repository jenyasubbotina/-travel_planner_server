package com.travelplanner.application.usecase.joincode

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.User
import com.travelplanner.domain.repository.JoinRequestRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.UserRepository
import java.util.UUID

class ListJoinRequestsUseCase(
    private val participantRepository: ParticipantRepository,
    private val joinRequestRepository: JoinRequestRepository,
    private val userRepository: UserRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(val tripId: UUID, val userId: UUID)

    data class Pending(val userId: UUID, val user: User)

    suspend fun execute(input: Input): List<Pending> = transactionRunner.runInTransaction {
        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!participant.role.canManageParticipants()) {
            throw DomainException.InsufficientRole("OWNER")
        }

        val pending = joinRequestRepository.findPendingByTrip(input.tripId)
        pending.mapNotNull { req ->
            val user = userRepository.findById(req.requesterUserId) ?: return@mapNotNull null
            Pending(req.requesterUserId, user)
        }
    }
}
