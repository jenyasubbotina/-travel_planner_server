package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.InvitationStatus
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.UserRepository
import java.time.Instant
import java.util.UUID

class AcceptInvitationUseCase(
    private val participantRepository: ParticipantRepository,
    private val userRepository: UserRepository
) {

    suspend fun execute(invitationId: UUID, userId: UUID): TripParticipant {
        val invitation = participantRepository.findInvitationById(invitationId)
            ?: throw DomainException.InvitationNotFound(invitationId)

        if (invitation.status != InvitationStatus.PENDING) {
            throw DomainException.InvitationAlreadyResolved(invitationId)
        }

        val user = userRepository.findById(userId)
            ?: throw DomainException.UserNotFound(userId)

        if (!user.email.equals(invitation.email, ignoreCase = true)) {
            throw DomainException.AccessDenied("This invitation was sent to a different email address")
        }

        // Mark invitation as accepted
        val resolvedInvitation = invitation.copy(
            status = InvitationStatus.ACCEPTED,
            resolvedAt = Instant.now()
        )
        participantRepository.updateInvitation(resolvedInvitation)

        // Add user as participant
        val now = Instant.now()
        val participant = TripParticipant(
            tripId = invitation.tripId,
            userId = userId,
            role = invitation.role,
            joinedAt = now
        )
        participantRepository.add(participant)

        return participant
    }
}
