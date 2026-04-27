package com.travelplanner.domain.repository

import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseHistoryEntry
import com.travelplanner.domain.model.ExpensePendingUpdate
import com.travelplanner.domain.model.ExpenseSplit
import java.time.Instant
import java.util.UUID

interface ExpenseRepository {
    suspend fun findByTrip(tripId: UUID): List<Expense>
    suspend fun findById(id: UUID): Expense?
    suspend fun create(expense: Expense): Expense
    suspend fun update(expense: Expense): Expense
    suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean
    suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<Expense>

    // Splits
    suspend fun findSplitsByExpense(expenseId: UUID): List<ExpenseSplit>
    suspend fun findSplitsByTrip(tripId: UUID): List<ExpenseSplit>
    suspend fun replaceSplits(expenseId: UUID, splits: List<ExpenseSplit>)
    suspend fun findSplitsModifiedAfter(tripId: UUID, after: Instant): List<ExpenseSplit>

    // Pending update (conflict resolution)
    suspend fun findPendingUpdate(expenseId: UUID): ExpensePendingUpdate?
    suspend fun upsertPendingUpdate(pending: ExpensePendingUpdate)
    suspend fun deletePendingUpdate(expenseId: UUID): Boolean
    suspend fun findPendingUpdatesByTrip(tripId: UUID): List<ExpensePendingUpdate>

    // Version history (used by conflict resolution to restore a base version)
    suspend fun appendHistory(entry: ExpenseHistoryEntry)
    suspend fun findHistoryAt(expenseId: UUID, version: Long): ExpenseHistoryEntry?
}
