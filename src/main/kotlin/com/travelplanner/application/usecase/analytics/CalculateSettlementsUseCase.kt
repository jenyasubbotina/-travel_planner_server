package com.travelplanner.application.usecase.analytics

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Settlement
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

class CalculateSettlementsUseCase(
    private val expenseRepository: ExpenseRepository,
    private val participantRepository: ParticipantRepository,
    private val tripRepository: TripRepository
) {

    suspend fun execute(tripId: UUID, userId: UUID): List<Settlement> {
        if (!participantRepository.isParticipant(tripId, userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        val trip = tripRepository.findById(tripId)
            ?: throw DomainException.TripNotFound(tripId)

        val participants = participantRepository.findByTrip(tripId)
        val expenses = expenseRepository.findByTrip(tripId).filter { it.deletedAt == null }
        val allSplits = expenseRepository.findSplitsByTrip(tripId)

        val activeExpenseIds = expenses.map { it.id }.toSet()

        // Calculate net balances for each participant
        // netBalance > 0 means the participant is owed money (creditor)
        // netBalance < 0 means the participant owes money (debtor)
        val netBalances = mutableMapOf<UUID, BigDecimal>()

        for (participant in participants) {
            val totalPaid = expenses
                .filter { it.payerUserId == participant.userId }
                .fold(BigDecimal.ZERO) { acc, expense -> acc + expense.amount }

            val totalOwed = allSplits
                .filter { it.participantUserId == participant.userId && it.expenseId in activeExpenseIds }
                .fold(BigDecimal.ZERO) { acc, split -> acc + split.amountInExpenseCurrency }

            val net = totalPaid - totalOwed
            if (net.compareTo(BigDecimal.ZERO) != 0) {
                netBalances[participant.userId] = net
            }
        }

        // Greedy settlement minimization algorithm
        // Separate into debtors (negative balance) and creditors (positive balance)
        val debtors = netBalances
            .filter { it.value < BigDecimal.ZERO }
            .map { DebtEntry(it.key, it.value.abs()) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val creditors = netBalances
            .filter { it.value > BigDecimal.ZERO }
            .map { DebtEntry(it.key, it.value) }
            .sortedByDescending { it.amount }
            .toMutableList()

        val settlements = mutableListOf<Settlement>()

        // Greedily match: largest debtor pays largest creditor min(|debt|, |credit|)
        var debtorIdx = 0
        var creditorIdx = 0

        while (debtorIdx < debtors.size && creditorIdx < creditors.size) {
            val debtor = debtors[debtorIdx]
            val creditor = creditors[creditorIdx]

            val transferAmount = minOf(debtor.amount, creditor.amount)
                .setScale(2, RoundingMode.HALF_UP)

            if (transferAmount > BigDecimal.ZERO) {
                settlements.add(
                    Settlement(
                        fromUserId = debtor.userId,
                        toUserId = creditor.userId,
                        amount = transferAmount,
                        currency = trip.baseCurrency
                    )
                )
            }

            debtors[debtorIdx] = debtor.copy(amount = debtor.amount - transferAmount)
            creditors[creditorIdx] = creditor.copy(amount = creditor.amount - transferAmount)

            if (debtors[debtorIdx].amount.compareTo(BigDecimal.ZERO) == 0) {
                debtorIdx++
            }
            if (creditors[creditorIdx].amount.compareTo(BigDecimal.ZERO) == 0) {
                creditorIdx++
            }
        }

        return settlements
    }

    private data class DebtEntry(val userId: UUID, val amount: BigDecimal)
}
