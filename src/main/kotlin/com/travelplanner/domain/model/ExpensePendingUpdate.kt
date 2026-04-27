package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class ExpensePendingUpdate(
    val expenseId: UUID,
    val proposedByUserId: UUID,
    val proposedAt: Instant,
    val baseVersion: Long,
    val payload: String,
)
