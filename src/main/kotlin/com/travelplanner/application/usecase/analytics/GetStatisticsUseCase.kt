package com.travelplanner.application.usecase.analytics

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.TripStatistics
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import java.math.BigDecimal
import java.util.UUID

class GetStatisticsUseCase(
    private val expenseRepository: ExpenseRepository,
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository
) {

    suspend fun execute(tripId: UUID, userId: UUID): TripStatistics {
        if (!participantRepository.isParticipant(tripId, userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        val trip = tripRepository.findById(tripId)
            ?: throw DomainException.TripNotFound(tripId)

        val expenses = expenseRepository.findByTrip(tripId).filter { it.deletedAt == null }

        val totalSpent = expenses.fold(BigDecimal.ZERO) { acc, expense -> acc + expense.amount }

        val spentByCategory = expenses
            .groupBy { it.category }
            .mapValues { (_, categoryExpenses) ->
                categoryExpenses.fold(BigDecimal.ZERO) { acc, expense -> acc + expense.amount }
            }

        val spentByParticipant = expenses
            .groupBy { it.payerUserId }
            .mapValues { (_, participantExpenses) ->
                participantExpenses.fold(BigDecimal.ZERO) { acc, expense -> acc + expense.amount }
            }

        val spentByDay = expenses
            .groupBy { it.expenseDate.toString() }
            .mapValues { (_, dayExpenses) ->
                dayExpenses.fold(BigDecimal.ZERO) { acc, expense -> acc + expense.amount }
            }

        return TripStatistics(
            totalSpent = totalSpent,
            currency = trip.baseCurrency,
            spentByCategory = spentByCategory,
            spentByParticipant = spentByParticipant,
            spentByDay = spentByDay
        )
    }
}
