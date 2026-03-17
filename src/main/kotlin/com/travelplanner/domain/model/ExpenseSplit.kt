package com.travelplanner.domain.model

import java.math.BigDecimal
import java.util.UUID

data class ExpenseSplit(
    val id: UUID,
    val expenseId: UUID,
    val participantUserId: UUID,
    val shareType: SplitType,
    val value: BigDecimal,
    val amountInExpenseCurrency: BigDecimal
)
