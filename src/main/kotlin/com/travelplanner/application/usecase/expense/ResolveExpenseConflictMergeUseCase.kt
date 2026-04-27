package com.travelplanner.application.usecase.expense

import com.travelplanner.application.service.NotificationService
import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.SplitType
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ResolveExpenseConflictMergeUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
    private val notificationService: NotificationService,
) {

    private val logger = LoggerFactory.getLogger(ResolveExpenseConflictMergeUseCase::class.java)

    data class Input(
        val tripId: UUID,
        val expenseId: UUID,
        val resolverUserId: UUID,
        val title: String? = null,
        val description: String? = null,
        val amount: BigDecimal? = null,
        val currency: String? = null,
        val category: String? = null,
        val expenseDate: LocalDate? = null,
        val splitType: SplitType? = null,
        val payerUserId: UUID? = null,
        val splits: List<CreateExpenseUseCase.SplitInputData>? = null,
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
                throw DomainException.AccessDenied("Only the expense creator can merge a pending edit")
            }

            expenseRepository.findPendingUpdate(input.expenseId)
                ?: throw DomainException.NoPendingUpdate(input.expenseId)

            val mergedInput = UpdateExpenseUseCase.Input(
                expenseId = input.expenseId,
                userId = input.resolverUserId,
                title = input.title,
                description = input.description,
                amount = input.amount,
                currency = input.currency,
                category = input.category,
                expenseDate = input.expenseDate,
                splitType = input.splitType,
                payerUserId = input.payerUserId,
                splits = input.splits,
                expectedVersion = expense.version,
            )

            val applied = updateExpenseUseCase.applyUpdate(expense, mergedInput)
            expenseRepository.deletePendingUpdate(input.expenseId)

            domainEventRepository.save(
                DomainEvent(
                    id = UUID.randomUUID(),
                    eventType = "EXPENSE_MERGED",
                    aggregateType = "TRIP",
                    aggregateId = expense.tripId,
                    payload = HistoryPayload.build(
                        actorUserId = input.resolverUserId,
                        entityType = HistoryPayload.EntityType.EXPENSE,
                        entityId = expense.id,
                        actionType = HistoryPayload.ActionType.MERGE_PENDING_UPDATE,
                        new = HistoryPayload.expenseSnapshot(applied.expense, applied.splits),
                    ),
                    createdAt = Instant.now(),
                )
            )

            Output(applied.expense, applied.splits)
        }

        notifyResolved(output.expense, "EXPENSE_MERGED")
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
