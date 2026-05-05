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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ResolveExpenseConflictRevertUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val updateExpenseUseCase: UpdateExpenseUseCase,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
    private val notificationService: NotificationService,
) {

    private val logger = LoggerFactory.getLogger(ResolveExpenseConflictRevertUseCase::class.java)

    private val payloadJson = Json { ignoreUnknownKeys = true }

    data class Input(
        val tripId: UUID,
        val expenseId: UUID,
        val resolverUserId: UUID,
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
                throw DomainException.AccessDenied("Only the expense creator can revert a pending edit")
            }

            val pending = expenseRepository.findPendingUpdate(input.expenseId)
                ?: throw DomainException.NoPendingUpdate(input.expenseId)

            val baseEntry = expenseRepository.findHistoryAt(input.expenseId, pending.baseVersion)
                ?: throw DomainException.ValidationError(
                    "Cannot revert: no history snapshot for expense ${input.expenseId} at version ${pending.baseVersion}"
                )

            val baseSnapshot = payloadJson.decodeFromString(JsonObject.serializer(), baseEntry.snapshot)
            val revertInput = baseSnapshot.toUpdateInput(
                expenseId = input.expenseId,
                userId = input.resolverUserId,
                expectedVersion = expense.version,
            )

            val applied = updateExpenseUseCase.applyUpdate(expense, revertInput)
            expenseRepository.deletePendingUpdate(input.expenseId)

            domainEventRepository.save(
                DomainEvent(
                    id = UUID.randomUUID(),
                    eventType = "EXPENSE_PENDING_REVERTED",
                    aggregateType = "TRIP",
                    aggregateId = expense.tripId,
                    payload = HistoryPayload.build(
                        actorUserId = input.resolverUserId,
                        entityType = HistoryPayload.EntityType.EXPENSE,
                        entityId = expense.id,
                        actionType = HistoryPayload.ActionType.REVERT_PENDING_UPDATE,
                        new = HistoryPayload.expenseSnapshot(applied.expense, applied.splits),
                    ),
                    createdAt = Instant.now(),
                )
            )

            Output(applied.expense, applied.splits)
        }

        notifyResolved(output.expense)
        return output
    }

    private suspend fun notifyResolved(expense: Expense) {
        try {
            val participantIds = participantRepository.getUserIdsForTrip(expense.tripId)
            notificationService.notifyTripParticipants(
                tripParticipantUserIds = participantIds,
                excludeUserId = null,
                title = "Конфликт расхода разрешён",
                body = "Версия расхода «${expense.title}» обновлена.",
                data = mapOf(
                    "eventType" to "EXPENSE_PENDING_REVERTED",
                    "tripId" to expense.tripId.toString(),
                    "expenseId" to expense.id.toString(),
                ),
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to send EXPENSE_PENDING_REVERTED FCM for expenseId={} tripId={}: {}",
                expense.id, expense.tripId, e.message,
            )
        }
    }

    private fun JsonObject.toUpdateInput(
        expenseId: UUID,
        userId: UUID,
        expectedVersion: Long,
    ): UpdateExpenseUseCase.Input = UpdateExpenseUseCase.Input(
        expenseId = expenseId,
        userId = userId,
        title = stringOrNull("title"),
        description = stringOrNull("description"),
        amount = stringOrNull("amount")?.let { BigDecimal(it) },
        currency = stringOrNull("currency"),
        category = stringOrNull("category"),
        expenseDate = stringOrNull("expenseDate")?.let { LocalDate.parse(it) },
        splitType = stringOrNull("splitType")?.let { SplitType.valueOf(it) },
        payerUserId = stringOrNull("payerUserId")?.let { UUID.fromString(it) },
        splits = this["splits"]?.jsonArray?.map { entry ->
            val obj = entry.jsonObject
            CreateExpenseUseCase.SplitInputData(
                participantUserId = UUID.fromString(obj["participantUserId"]!!.jsonPrimitive.content),
                value = BigDecimal(obj["value"]!!.jsonPrimitive.content),
            )
        },
        expectedVersion = expectedVersion,
    )

    private fun JsonObject.stringOrNull(key: String): String? = when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.content
        else -> null
    }
}
