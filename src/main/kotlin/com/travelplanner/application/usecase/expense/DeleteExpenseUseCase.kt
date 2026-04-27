package com.travelplanner.application.usecase.expense

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import java.time.Instant
import java.util.UUID

class DeleteExpenseUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(val expenseId: UUID, val tripId: UUID, val userId: UUID)

    suspend fun execute(input: Input) = transactionRunner.runInTransaction {
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

        val now = Instant.now()
        expenseRepository.softDelete(input.expenseId, now)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "EXPENSE_DELETED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.EXPENSE,
                    entityId = input.expenseId,
                    actionType = HistoryPayload.ActionType.DELETE,
                    entity = HistoryPayload.expenseSnapshot(expense),
                ),
                createdAt = now
            )
        )
        Unit
    }
}
