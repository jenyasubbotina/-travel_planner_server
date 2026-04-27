package com.travelplanner.application.usecase.expense

import com.travelplanner.application.service.NotificationService
import com.travelplanner.domain.event.HistoryPayload
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseHistoryEntry
import com.travelplanner.domain.model.ExpensePendingUpdate
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.SplitType
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.validation.ExpenseSplitValidator
import com.travelplanner.domain.validation.SplitInput
import org.slf4j.LoggerFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class UpdateExpenseUseCase(
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner,
    private val notificationService: NotificationService,
) {

    private val logger = LoggerFactory.getLogger(UpdateExpenseUseCase::class.java)

    data class Input(
        val expenseId: UUID,
        val userId: UUID,
        val title: String? = null,
        val description: String? = null,
        val amount: BigDecimal? = null,
        val currency: String? = null,
        val category: String? = null,
        val expenseDate: LocalDate? = null,
        val splitType: SplitType? = null,
        val payerUserId: UUID? = null,
        val splits: List<CreateExpenseUseCase.SplitInputData>? = null,
        val expectedVersion: Long
    )

    data class Output(val expense: Expense, val splits: List<ExpenseSplit>)

    suspend fun execute(input: Input): Output = transactionRunner.runInTransaction {
        val expense = expenseRepository.findById(input.expenseId)
            ?: throw DomainException.ExpenseNotFound(input.expenseId)

        if (expense.deletedAt != null) {
            throw DomainException.ExpenseNotFound(input.expenseId)
        }

        val participant = participantRepository.findByTripAndUser(expense.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        val isCreator = expense.createdBy == input.userId

        if (isCreator) {
            if (expense.version != input.expectedVersion) {
                queuePendingProposal(expense, input)
                throw DomainException.PendingUpdateStored(expense.id)
            }
            return@runInTransaction applyUpdate(expense, input)
        }

        val existing = expenseRepository.findPendingUpdate(expense.id)
        if (existing != null && existing.proposedByUserId != input.userId) {
            throw DomainException.AnotherPendingUpdate(expense.id, existing.proposedByUserId)
        }

        queuePendingProposal(expense, input)
        throw DomainException.PendingUpdateStored(expense.id)
    }

    private suspend fun queuePendingProposal(expense: Expense, input: Input) {
        val payload = serializeInput(input)
        expenseRepository.upsertPendingUpdate(
            ExpensePendingUpdate(
                expenseId = expense.id,
                proposedByUserId = input.userId,
                proposedAt = Instant.now(),
                baseVersion = expense.version,
                payload = payload,
            )
        )
        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "EXPENSE_PENDING_UPDATE_STORED",
                aggregateType = "TRIP",
                aggregateId = expense.tripId,
                payload = HistoryPayload.build(
                    actorUserId = input.userId,
                    entityType = HistoryPayload.EntityType.EXPENSE,
                    entityId = expense.id,
                    actionType = HistoryPayload.ActionType.STORE_PENDING_UPDATE,
                    context = buildJsonObject {
                        put("title", expense.title)
                        input.amount?.let { put("proposedAmount", it.toPlainString()) }
                    },
                ),
                createdAt = Instant.now(),
            )
        )
        notifyConflictDetected(expense, input.userId)
    }

    suspend fun applyUpdate(expense: Expense, input: Input): Output = transactionRunner.runInTransaction {
        if (input.title != null && input.title.isBlank()) {
            throw DomainException.ValidationError("Expense title cannot be blank")
        }

        if (input.amount != null && input.amount <= BigDecimal.ZERO) {
            throw DomainException.ValidationError("Expense amount must be positive")
        }

        val previousSplits = expenseRepository.findSplitsByExpense(expense.id)

        val newPayerUserId = input.payerUserId ?: expense.payerUserId
        if (input.payerUserId != null) {
            if (!participantRepository.isParticipant(expense.tripId, input.payerUserId)) {
                throw DomainException.ParticipantNotInTrip(input.payerUserId, expense.tripId)
            }
        }

        val updatedExpense = expense.copy(
            title = input.title?.trim() ?: expense.title,
            description = if (input.description != null) input.description.trim() else expense.description,
            amount = input.amount ?: expense.amount,
            currency = input.currency?.uppercase()?.trim() ?: expense.currency,
            category = input.category?.trim() ?: expense.category,
            expenseDate = input.expenseDate ?: expense.expenseDate,
            splitType = input.splitType ?: expense.splitType,
            payerUserId = newPayerUserId
        )

        val savedExpense = expenseRepository.update(updatedExpense)

        val finalSplits = if (input.splits != null) {
            val tripParticipantIds = participantRepository.getUserIdsForTrip(expense.tripId).toSet()
            val splitInputs = input.splits.map { split ->
                SplitInput(
                    participantUserId = split.participantUserId,
                    shareType = updatedExpense.splitType,
                    value = split.value
                )
            }

            val validatedSplits = ExpenseSplitValidator.validate(
                totalAmount = updatedExpense.amount,
                splitType = updatedExpense.splitType,
                splits = splitInputs,
                tripParticipantIds = tripParticipantIds
            )

            val expenseSplits = validatedSplits.map { (splitInput, calculatedAmount) ->
                ExpenseSplit(
                    id = UUID.randomUUID(),
                    expenseId = expense.id,
                    participantUserId = splitInput.participantUserId,
                    shareType = updatedExpense.splitType,
                    value = splitInput.value,
                    amountInExpenseCurrency = calculatedAmount
                )
            }

            expenseRepository.replaceSplits(expense.id, expenseSplits)
            expenseSplits
        } else {
            expenseRepository.findSplitsByExpense(expense.id)
        }

        val newSnapshot = HistoryPayload.expenseSnapshot(savedExpense, finalSplits)

        expenseRepository.appendHistory(
            ExpenseHistoryEntry(
                expenseId = savedExpense.id,
                version = savedExpense.version,
                snapshot = newSnapshot.toString(),
                editedByUserId = input.userId,
                editedAt = savedExpense.updatedAt,
            )
        )

        val diff = HistoryPayload.diff(
            HistoryPayload.expenseSnapshot(expense, previousSplits),
            newSnapshot,
        )
        if (diff != null) {
            domainEventRepository.save(
                DomainEvent(
                    id = UUID.randomUUID(),
                    eventType = "EXPENSE_UPDATED",
                    aggregateType = "TRIP",
                    aggregateId = savedExpense.tripId,
                    payload = HistoryPayload.build(
                        actorUserId = input.userId,
                        entityType = HistoryPayload.EntityType.EXPENSE,
                        entityId = savedExpense.id,
                        actionType = HistoryPayload.ActionType.UPDATE,
                        old = diff.first,
                        new = diff.second,
                    ),
                    createdAt = Instant.now()
                )
            )
        }

        Output(savedExpense, finalSplits)
    }

    private suspend fun notifyConflictDetected(expense: Expense, proposerUserId: java.util.UUID) {
        try {
            val participantIds = participantRepository.getUserIdsForTrip(expense.tripId)
            notificationService.notifyTripParticipants(
                tripParticipantUserIds = participantIds,
                excludeUserId = null,
                title = "Конфликт расхода",
                body = "Расход «${expense.title}» был изменён двумя участниками одновременно.",
                data = mapOf(
                    "eventType" to "EXPENSE_PENDING_UPDATE_STORED",
                    "tripId" to expense.tripId.toString(),
                    "expenseId" to expense.id.toString(),
                    "proposerUserId" to proposerUserId.toString(),
                ),
            )
        } catch (e: Exception) {
            logger.warn(
                "Failed to send EXPENSE_PENDING_UPDATE_STORED FCM for expenseId={} tripId={}: {}",
                expense.id, expense.tripId, e.message,
            )
        }
    }

    @Serializable
    private data class SerializedInput(
        val expenseId: String,
        val userId: String,
        val title: String? = null,
        val description: String? = null,
        val amount: String? = null,
        val currency: String? = null,
        val category: String? = null,
        val expenseDate: String? = null,
        val splitType: String? = null,
        val payerUserId: String? = null,
        val splits: List<SerializedSplit>? = null,
    )

    @Serializable
    private data class SerializedSplit(val participantUserId: String, val value: String)

    private val payloadJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun serializeInput(input: Input): String =
        payloadJson.encodeToString(SerializedInput.serializer(), input.toSerialized())

    fun deserializeInput(payload: String, expenseId: UUID, userId: UUID): Input {
        val s = payloadJson.decodeFromString(SerializedInput.serializer(), payload)
        return Input(
            expenseId = expenseId,
            userId = userId,
            title = s.title,
            description = s.description,
            amount = s.amount?.let { BigDecimal(it) },
            currency = s.currency,
            category = s.category,
            expenseDate = s.expenseDate?.let { java.time.LocalDate.parse(it) },
            splitType = s.splitType?.let { SplitType.valueOf(it) },
            payerUserId = s.payerUserId?.let { UUID.fromString(it) },
            splits = s.splits?.map {
                CreateExpenseUseCase.SplitInputData(
                    participantUserId = UUID.fromString(it.participantUserId),
                    value = BigDecimal(it.value),
                )
            },
            expectedVersion = 0L,
        )
    }

    private fun Input.toSerialized() = SerializedInput(
        expenseId = expenseId.toString(),
        userId = userId.toString(),
        title = title,
        description = description,
        amount = amount?.toPlainString(),
        currency = currency,
        category = category,
        expenseDate = expenseDate?.toString(),
        splitType = splitType?.name,
        payerUserId = payerUserId?.toString(),
        splits = splits?.map { SerializedSplit(it.participantUserId.toString(), it.value.toPlainString()) },
    )
}
