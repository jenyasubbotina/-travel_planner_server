package com.travelplanner.application.usecase.expense

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.SplitType
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.validation.ExpenseSplitValidator
import com.travelplanner.domain.validation.SplitInput
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
    private val transactionRunner: TransactionRunner
) {

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

        if (expense.version != input.expectedVersion) {
            throw DomainException.VersionConflict("Expense", input.expenseId)
        }

        if (input.title != null && input.title.isBlank()) {
            throw DomainException.ValidationError("Expense title cannot be blank")
        }

        if (input.amount != null && input.amount <= BigDecimal.ZERO) {
            throw DomainException.ValidationError("Expense amount must be positive")
        }

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
                    expenseId = input.expenseId,
                    participantUserId = splitInput.participantUserId,
                    shareType = updatedExpense.splitType,
                    value = splitInput.value,
                    amountInExpenseCurrency = calculatedAmount
                )
            }

            expenseRepository.replaceSplits(input.expenseId, expenseSplits)
            expenseSplits
        } else {
            expenseRepository.findSplitsByExpense(input.expenseId)
        }

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "EXPENSE_UPDATED",
                aggregateType = "TRIP",
                aggregateId = savedExpense.tripId,
                payload = buildJsonObject {
                    put("actorUserId", input.userId.toString())
                    put("expenseId", savedExpense.id.toString())
                    put("description", savedExpense.title)
                }.toString(),
                createdAt = Instant.now()
            )
        )

        Output(savedExpense, finalSplits)
    }
}
