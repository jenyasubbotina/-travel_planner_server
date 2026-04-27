package com.travelplanner.application.usecase.expense

import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

class ResolveExpenseConflictUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
) {

    data class Input(
        val tripId: UUID,
        val expenseId: UUID,
        val resolverUserId: UUID,
        val accept: Boolean,
    )

    data class Output(val expense: Expense, val splits: List<ExpenseSplit>)

    suspend fun execute(input: Input): Output = transactionRunner.runInTransaction {
        val expense = expenseRepository.findById(input.expenseId)
            ?: throw DomainException.ExpenseNotFound(input.expenseId)
        if (expense.tripId != input.tripId) {
            throw DomainException.ExpenseNotFound(input.expenseId)
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.resolverUserId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")
        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        if (expense.createdBy != input.resolverUserId) {
            throw DomainException.AccessDenied("Only the expense creator can resolve a pending edit")
        }

        val pending = expenseRepository.findPendingUpdate(input.expenseId)
            ?: throw DomainException.NoPendingUpdate(input.expenseId)

        return@runInTransaction if (input.accept) {
            val pendingInput = updateExpenseUseCase
                .deserializeInput(pending.payload, input.expenseId, input.resolverUserId)
                .copy(expectedVersion = expense.version)
            val output = updateExpenseUseCase.applyUpdate(expense, pendingInput)
            expenseRepository.deletePendingUpdate(input.expenseId)
            Output(output.expense, output.splits)
        } else {
            expenseRepository.deletePendingUpdate(input.expenseId)
            domainEventRepository.save(
                DomainEvent(
                    id = UUID.randomUUID(),
                    eventType = "EXPENSE_PENDING_REJECTED",
                    aggregateType = "TRIP",
                    aggregateId = expense.tripId,
                    payload = HistoryPayload.build(
                        actorUserId = input.resolverUserId,
                        entityType = HistoryPayload.EntityType.EXPENSE,
                        entityId = expense.id,
                        actionType = HistoryPayload.ActionType.REJECT_PENDING_UPDATE,
                        context = buildJsonObject { put("title", expense.title) },
                    ),
                    createdAt = Instant.now(),
                )
            )
            Output(expense, expenseRepository.findSplitsByExpense(expense.id))
        }
    }
}
