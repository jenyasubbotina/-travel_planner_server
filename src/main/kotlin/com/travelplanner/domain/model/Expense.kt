package com.travelplanner.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class Expense(
    val id: UUID,
    val tripId: UUID,
    val payerUserId: UUID,
    val title: String,
    val description: String? = null,
    val amount: BigDecimal,
    val currency: String,
    val category: String,
    val expenseDate: LocalDate,
    val splitType: SplitType = SplitType.EQUAL,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 1,
    val deletedAt: Instant? = null
)

enum class SplitType {
    EQUAL,
    PERCENTAGE,
    SHARES,
    EXACT_AMOUNT
}
