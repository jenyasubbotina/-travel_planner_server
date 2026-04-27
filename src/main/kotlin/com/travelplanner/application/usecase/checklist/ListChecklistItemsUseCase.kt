package com.travelplanner.application.usecase.checklist

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ChecklistItem
import com.travelplanner.domain.repository.ChecklistRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.util.UUID

class ListChecklistItemsUseCase(
    private val participantRepository: ParticipantRepository,
    private val checklistRepository: ChecklistRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(val tripId: UUID, val userId: UUID)

    suspend fun execute(input: Input): List<ChecklistItem> = transactionRunner.runInTransaction {
        participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        checklistRepository.findByTripVisibleTo(input.tripId, input.userId)
    }
}
