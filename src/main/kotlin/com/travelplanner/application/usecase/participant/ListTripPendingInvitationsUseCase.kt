package com.travelplanner.application.usecase.participant

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.TripInvitation
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.util.UUID

class ListTripPendingInvitationsUseCase(
    private val participantRepository: ParticipantRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(val tripId: UUID, val userId: UUID)

    suspend fun execute(input: Input): List<TripInvitation> = transactionRunner.runInTransaction {
        participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        participantRepository.findPendingInvitationsByTrip(input.tripId)
    }
}
