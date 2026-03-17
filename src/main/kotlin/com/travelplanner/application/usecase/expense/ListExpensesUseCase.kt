package com.travelplanner.application.usecase.expense

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.LocalDate
import java.util.UUID

class ListExpensesUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val category: String? = null,
        val payerUserId: UUID? = null,
        val dateFrom: LocalDate? = null,
        val dateTo: LocalDate? = null
    )

    suspend fun execute(input: Input): List<Expense> {
        if (!participantRepository.isParticipant(input.tripId, input.userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        val allExpenses = expenseRepository.findByTrip(input.tripId)

        return allExpenses.filter { expense ->
            expense.deletedAt == null
                    && (input.category == null || expense.category.equals(input.category, ignoreCase = true))
                    && (input.payerUserId == null || expense.payerUserId == input.payerUserId)
                    && (input.dateFrom == null || !expense.expenseDate.isBefore(input.dateFrom))
                    && (input.dateTo == null || !expense.expenseDate.isAfter(input.dateTo))
        }
    }
}
