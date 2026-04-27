package com.travelplanner.domain.model

import java.time.Instant
import java.util.UUID

data class ExpenseHistoryEntry(
    val expenseId: UUID,
    val version: Long,
    val snapshot: String,
    val editedByUserId: UUID,
    val editedAt: Instant,
)
