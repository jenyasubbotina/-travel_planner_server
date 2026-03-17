package com.travelplanner.application.usecase.analytics

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ParticipantBalance
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.math.BigDecimal
import java.util.UUID

class CalculateBalancesUseCase(
    private val expenseRepository: ExpenseRepository,
    private val participantRepository: ParticipantRepository
) {

    suspend fun execute(tripId: UUID, userId: UUID): List<ParticipantBalance> {
        if (!participantRepository.isParticipant(tripId, userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        val participants = participantRepository.findByTrip(tripId)
        val expenses = expenseRepository.findByTrip(tripId).filter { it.deletedAt == null }
        val allSplits = expenseRepository.findSplitsByTrip(tripId)

        // Build a set of expense IDs that are not deleted for filtering splits
        val activeExpenseIds = expenses.map { it.id }.toSet()

        // For each participant, calculate totalPaid and totalOwed
        return participants.map { participant ->
            val totalPaid = expenses
                .filter { it.payerUserId == participant.userId }
                .fold(BigDecimal.ZERO) { acc, expense -> acc + expense.amount }

            val totalOwed = allSplits
                .filter { it.participantUserId == participant.userId && it.expenseId in activeExpenseIds }
                .fold(BigDecimal.ZERO) { acc, split -> acc + split.amountInExpenseCurrency }

            val netBalance = totalPaid - totalOwed

            ParticipantBalance(
                userId = participant.userId,
                totalPaid = totalPaid,
                totalOwed = totalOwed,
                netBalance = netBalance
            )
        }
    }
}
