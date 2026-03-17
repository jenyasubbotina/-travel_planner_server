package com.travelplanner.application.usecase.expense

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.Instant
import java.util.UUID

class DeleteExpenseUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository
) {

    data class Input(val expenseId: UUID, val tripId: UUID, val userId: UUID)

    suspend fun execute(input: Input) {
        val expense = expenseRepository.findById(input.expenseId)
            ?: throw DomainException.ExpenseNotFound(input.expenseId)

        if (expense.deletedAt != null) {
            throw DomainException.ExpenseNotFound(input.expenseId)
        }

        if (expense.tripId != input.tripId) {
            throw DomainException.ExpenseNotFound(input.expenseId)
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        expenseRepository.softDelete(input.expenseId, Instant.now())
    }
}
