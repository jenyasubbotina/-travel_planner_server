package com.travelplanner.domain.repository

import com.travelplanner.domain.model.Expense
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
}
