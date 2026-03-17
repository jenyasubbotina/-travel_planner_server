package com.travelplanner.domain.validation

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.SplitType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class SplitInput(
    val participantUserId: UUID,
    val shareType: SplitType,
    val value: BigDecimal
)

object ExpenseSplitValidator {

    fun validate(
        totalAmount: BigDecimal,
        splitType: SplitType,
        splits: List<SplitInput>,
        tripParticipantIds: Set<UUID>
    ): List<Pair<SplitInput, BigDecimal>> {
        if (splits.isEmpty()) {
            throw DomainException.ValidationError("At least one split is required")
        }

        if (totalAmount <= BigDecimal.ZERO) {
            throw DomainException.ValidationError("Expense amount must be positive")
        }

        splits.forEach { split ->
            if (split.value < BigDecimal.ZERO) {
                throw DomainException.ValidationError("Split values cannot be negative")
            }
            if (split.participantUserId !in tripParticipantIds) {
                throw DomainException.ParticipantNotInTrip(split.participantUserId, UUID(0, 0))
            }
        }

        val duplicates = splits.groupBy { it.participantUserId }.filter { it.value.size > 1 }
        if (duplicates.isNotEmpty()) {
            throw DomainException.ValidationError("Duplicate participant in splits: ${duplicates.keys.first()}")
        }

        return when (splitType) {
            SplitType.EQUAL -> validateEqual(totalAmount, splits)
            SplitType.PERCENTAGE -> validatePercentage(totalAmount, splits)
            SplitType.SHARES -> validateShares(totalAmount, splits)
            SplitType.EXACT_AMOUNT -> validateExactAmount(totalAmount, splits)
        }
    }

    private fun validateEqual(
        totalAmount: BigDecimal,
        splits: List<SplitInput>
    ): List<Pair<SplitInput, BigDecimal>> {
        val count = splits.size.toBigDecimal()
        val perPerson = totalAmount.divide(count, 2, RoundingMode.HALF_UP)
        val remainder = totalAmount - perPerson.multiply(count)

        return splits.mapIndexed { index, split ->
            val amount = if (index == 0) perPerson + remainder else perPerson
            split to amount
        }
    }

    private fun validatePercentage(
        totalAmount: BigDecimal,
        splits: List<SplitInput>
    ): List<Pair<SplitInput, BigDecimal>> {
        val totalPercentage = splits.fold(BigDecimal.ZERO) { acc, s -> acc + s.value }
        if (totalPercentage.compareTo(BigDecimal(100)) != 0) {
            throw DomainException.InvalidSplitSum("100%", "${totalPercentage}%")
        }

        return splits.map { split ->
            val amount = totalAmount.multiply(split.value).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            split to amount
        }
    }

    private fun validateShares(
        totalAmount: BigDecimal,
        splits: List<SplitInput>
    ): List<Pair<SplitInput, BigDecimal>> {
        val totalShares = splits.fold(BigDecimal.ZERO) { acc, s -> acc + s.value }
        if (totalShares <= BigDecimal.ZERO) {
            throw DomainException.ValidationError("Total shares must be positive")
        }

        return splits.map { split ->
            val amount = totalAmount.multiply(split.value).divide(totalShares, 2, RoundingMode.HALF_UP)
            split to amount
        }
    }

    private fun validateExactAmount(
        totalAmount: BigDecimal,
        splits: List<SplitInput>
    ): List<Pair<SplitInput, BigDecimal>> {
        val totalSplit = splits.fold(BigDecimal.ZERO) { acc, s -> acc + s.value }
        if (totalSplit.setScale(2, RoundingMode.HALF_UP).compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw DomainException.InvalidSplitSum(
                totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                totalSplit.setScale(2, RoundingMode.HALF_UP).toPlainString()
            )
        }

        return splits.map { split -> split to split.value.setScale(2, RoundingMode.HALF_UP) }
    }
}
