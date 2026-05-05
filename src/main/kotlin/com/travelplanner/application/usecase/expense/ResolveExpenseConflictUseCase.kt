package com.travelplanner.application.usecase.expense

import com.travelplanner.application.service.NotificationService
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
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class ResolveExpenseConflictUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
    private val notificationService: NotificationService,
) {

    private val logger = LoggerFactory.getLogger(ResolveExpenseConflictUseCase::class.java)

    data class Input(
        val tripId: UUID,
        val expenseId: UUID,
        val resolverUserId: UUID,
        val accept: Boolean,
    )

    data class Output(val expense: Expense, val splits: List<ExpenseSplit>)

    suspend fun execute(input: Input): Output {
        val output = transactionRunner.runInTransaction {
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

            if (input.accept) {
                val pendingInput = updateExpenseUseCase
                    .deserializeInput(pending.payload, input.expenseId, input.resolverUserId)
                    .copy(expectedVersion = expense.version)
                val applied = updateExpenseUseCase.applyUpdate(expense, pendingInput)
                expenseRepository.deletePendingUpdate(input.expenseId)
                Output(applied.expense, applied.splits)
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

        notifyResolved(
            output.expense,
            if (input.accept) "EXPENSE_PENDING_ACCEPTED" else "EXPENSE_PENDING_REJECTED",
        )
        return output
    }

    private suspend fun notifyResolved(expense: Expense, eventType: String) {
        try {
            val participantIds = participantRepository.getUserIdsForTrip(expense.tripId)
            notificationService.notifyTripParticipants(
                tripParticipantUserIds = participantIds,
                excludeUserId = null,
                title = "Конфликт расхода разрешён",
                body = "Версия расхода «${expense.title}» обновлена.",
                data = mapOf(
                    "eventType" to eventType,
                    "tripId" to expense.tripId.toString(),
                    "expenseId" to expense.id.toString(),
                ),
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to send {} FCM for expenseId={} tripId={}: {}",
                eventType, expense.id, expense.tripId, e.message,
            )
        }
    }
}
