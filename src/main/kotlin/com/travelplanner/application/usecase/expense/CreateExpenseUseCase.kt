package com.travelplanner.application.usecase.expense

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.SplitType
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.validation.ExpenseSplitValidator
import com.travelplanner.domain.validation.SplitInput
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CreateExpenseUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val expenseRepository: ExpenseRepository
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val title: String,
        val description: String? = null,
        val amount: BigDecimal,
        val currency: String,
        val category: String,
        val expenseDate: LocalDate,
        val splitType: SplitType = SplitType.EQUAL,
        val payerUserId: UUID,
        val splits: List<SplitInputData>
    )

    data class SplitInputData(
        val participantUserId: UUID,
        val value: BigDecimal
    )

    data class Output(val expense: Expense, val splits: List<ExpenseSplit>)

    suspend fun execute(input: Input): Output {
        if (input.title.isBlank()) {
            throw DomainException.ValidationError("Expense title cannot be blank")
        }

        if (input.amount <= BigDecimal.ZERO) {
            throw DomainException.ValidationError("Expense amount must be positive")
        }

        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(input.tripId)
        }

        if (trip.status != TripStatus.ACTIVE) {
            throw DomainException.TripNotActive(input.tripId)
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        // Check that payer is a participant
        if (!participantRepository.isParticipant(input.tripId, input.payerUserId)) {
            throw DomainException.ParticipantNotInTrip(input.payerUserId, input.tripId)
        }

        // Get all participant user IDs for validation
        val tripParticipantIds = participantRepository.getUserIdsForTrip(input.tripId).toSet()

        // Validate and calculate splits
        val splitInputs = input.splits.map { split ->
            SplitInput(
                participantUserId = split.participantUserId,
                shareType = input.splitType,
                value = split.value
            )
        }

        val validatedSplits = ExpenseSplitValidator.validate(
            totalAmount = input.amount,
            splitType = input.splitType,
            splits = splitInputs,
            tripParticipantIds = tripParticipantIds
        )

        val now = Instant.now()
        val expenseId = UUID.randomUUID()
        val expense = Expense(
            id = expenseId,
            tripId = input.tripId,
            payerUserId = input.payerUserId,
            title = input.title.trim(),
            description = input.description?.trim(),
            amount = input.amount,
            currency = input.currency.uppercase().trim(),
            category = input.category.trim(),
            expenseDate = input.expenseDate,
            splitType = input.splitType,
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        val createdExpense = expenseRepository.create(expense)

        val expenseSplits = validatedSplits.map { (splitInput, calculatedAmount) ->
            ExpenseSplit(
                id = UUID.randomUUID(),
                expenseId = expenseId,
                participantUserId = splitInput.participantUserId,
                shareType = input.splitType,
                value = splitInput.value,
                amountInExpenseCurrency = calculatedAmount
            )
        }

        expenseRepository.replaceSplits(expenseId, expenseSplits)

        return Output(createdExpense, expenseSplits)
    }
}
