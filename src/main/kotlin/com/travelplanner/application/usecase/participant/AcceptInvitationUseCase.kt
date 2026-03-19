package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.UserRepository
import java.util.UUID

class AcceptInvitationUseCase(
    private val participantRepository: ParticipantRepository,
    private val userRepository: UserRepository
) {

    suspend fun execute(invitationId: UUID, userId: UUID): TripParticipant {
        val user = userRepository.findById(userId)
            ?: throw DomainException.UserNotFound(userId)

        return participantRepository.acceptInvitation(invitationId, user)
    }
}
