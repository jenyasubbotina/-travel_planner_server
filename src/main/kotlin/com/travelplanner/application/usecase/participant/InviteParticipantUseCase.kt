package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.InvitationStatus
import com.travelplanner.domain.model.TripInvitation
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.domain.validation.TripValidator
import java.time.Instant
import java.util.UUID

class InviteParticipantUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val userRepository: UserRepository
) {

    data class Input(
        val tripId: UUID,
        val inviterUserId: UUID,
        val email: String,
        val role: TripRole = TripRole.EDITOR
    )

    suspend fun execute(input: Input): TripInvitation {
        TripValidator.validateEmail(input.email)

        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(input.tripId)
        }

        if (trip.status != TripStatus.ACTIVE) {
            throw DomainException.TripNotActive(input.tripId)
        }

        val inviter = participantRepository.findByTripAndUser(input.tripId, input.inviterUserId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!inviter.role.canManageParticipants()) {
            throw DomainException.InsufficientRole("OWNER")
        }

        // Check if the user with this email is already a participant
        val existingUser = userRepository.findByEmail(input.email.lowercase().trim())
        if (existingUser != null) {
            val existingParticipant = participantRepository.findByTripAndUser(input.tripId, existingUser.id)
            if (existingParticipant != null) {
                throw DomainException.AlreadyParticipant(existingUser.id, input.tripId)
            }
        }

        // Check if there's already a pending invitation for this email
        val pendingInvitations = participantRepository.findPendingInvitationsByTrip(input.tripId)
        val alreadyInvited = pendingInvitations.any {
            it.email.equals(input.email.lowercase().trim(), ignoreCase = true)
        }
        if (alreadyInvited) {
            throw DomainException.ValidationError("An invitation has already been sent to ${input.email}")
        }

        val now = Instant.now()
        val invitation = TripInvitation(
            id = UUID.randomUUID(),
            tripId = input.tripId,
            email = input.email.lowercase().trim(),
            invitedBy = input.inviterUserId,
            role = input.role,
            status = InvitationStatus.PENDING,
            createdAt = now
        )

        return participantRepository.createInvitation(invitation)
    }
}
