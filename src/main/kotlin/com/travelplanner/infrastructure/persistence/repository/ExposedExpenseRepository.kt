package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.SplitType
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.ExpenseSplitsTable
import com.travelplanner.infrastructure.persistence.tables.ExpensesTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedExpenseRepository : ExpenseRepository {

    override suspend fun findByTrip(tripId: UUID): List<Expense> = dbQuery {
        ExpensesTable.selectAll()
            .where {
                (ExpensesTable.tripId eq tripId) and ExpensesTable.deletedAt.isNull()
            }
            .map { it.toExpense() }
    }

    override suspend fun findById(id: UUID): Expense? = dbQuery {
        ExpensesTable.selectAll()
            .where {
                (ExpensesTable.id eq id) and ExpensesTable.deletedAt.isNull()
            }
            .singleOrNull()
            ?.toExpense()
    }

    override suspend fun create(expense: Expense): Expense = dbQuery {
        ExpensesTable.insert {
            it[id] = expense.id
            it[tripId] = expense.tripId
            it[payerUserId] = expense.payerUserId
            it[title] = expense.title
            it[description] = expense.description
            it[amount] = expense.amount
            it[currency] = expense.currency
            it[category] = expense.category
            it[expenseDate] = expense.expenseDate
            it[splitType] = expense.splitType.name
            it[createdBy] = expense.createdBy
            it[createdAt] = expense.createdAt
            it[updatedAt] = expense.updatedAt
            it[version] = expense.version
            it[deletedAt] = expense.deletedAt
        }
        expense
    }

    override suspend fun update(expense: Expense): Expense = dbQuery {
        val now = Instant.now()
        val newVersion = expense.version + 1
        ExpensesTable.update({ ExpensesTable.id eq expense.id }) {
            it[payerUserId] = expense.payerUserId
            it[title] = expense.title
            it[description] = expense.description
            it[amount] = expense.amount
            it[currency] = expense.currency
            it[category] = expense.category
            it[expenseDate] = expense.expenseDate
            it[splitType] = expense.splitType.name
            it[updatedAt] = now
            it[version] = newVersion
        }
        expense.copy(updatedAt = now, version = newVersion)
    }

    override suspend fun softDelete(id: UUID, deletedAt: Instant): Boolean = dbQuery {
        val updatedCount = ExpensesTable.update({ ExpensesTable.id eq id }) {
            it[ExpensesTable.deletedAt] = deletedAt
            it[updatedAt] = deletedAt
            it[version] = with(SqlExpressionBuilder) { ExpensesTable.version + 1L }
        }
        updatedCount > 0
    }

    override suspend fun findModifiedAfter(tripId: UUID, after: Instant): List<Expense> = dbQuery {
        ExpensesTable.selectAll()
            .where {
                (ExpensesTable.tripId eq tripId) and
                    (ExpensesTable.updatedAt greater after)
            }
            .map { it.toExpense() }
    }

    // --- Splits ---

    override suspend fun findSplitsByExpense(expenseId: UUID): List<ExpenseSplit> = dbQuery {
        ExpenseSplitsTable.selectAll()
            .where { ExpenseSplitsTable.expenseId eq expenseId }
            .map { it.toExpenseSplit() }
    }

    override suspend fun findSplitsByTrip(tripId: UUID): List<ExpenseSplit> = dbQuery {
        val expenseIds = ExpensesTable
            .select(ExpensesTable.id)
            .where {
                (ExpensesTable.tripId eq tripId) and ExpensesTable.deletedAt.isNull()
            }
            .map { it[ExpensesTable.id] }

        ExpenseSplitsTable.selectAll()
            .where { ExpenseSplitsTable.expenseId inList expenseIds }
            .map { it.toExpenseSplit() }
    }

    override suspend fun replaceSplits(expenseId: UUID, splits: List<ExpenseSplit>): Unit = dbQuery {
        ExpenseSplitsTable.deleteWhere { ExpenseSplitsTable.expenseId eq expenseId }

        for (split in splits) {
            ExpenseSplitsTable.insert {
                it[id] = split.id
                it[ExpenseSplitsTable.expenseId] = split.expenseId
                it[participantUserId] = split.participantUserId
                it[shareType] = split.shareType.name
                it[value] = split.value
                it[amountInExpenseCurrency] = split.amountInExpenseCurrency
            }
        }
    }

    override suspend fun findSplitsModifiedAfter(tripId: UUID, after: Instant): List<ExpenseSplit> = dbQuery {
        val modifiedExpenseIds = ExpensesTable
            .select(ExpensesTable.id)
            .where {
                (ExpensesTable.tripId eq tripId) and
                    (ExpensesTable.updatedAt greater after)
            }
            .map { it[ExpensesTable.id] }

        ExpenseSplitsTable.selectAll()
            .where { ExpenseSplitsTable.expenseId inList modifiedExpenseIds }
            .map { it.toExpenseSplit() }
    }

    // --- Mapping helpers ---

    private fun ResultRow.toExpense() = Expense(
        id = this[ExpensesTable.id],
        tripId = this[ExpensesTable.tripId],
        payerUserId = this[ExpensesTable.payerUserId],
        title = this[ExpensesTable.title],
        description = this[ExpensesTable.description],
        amount = this[ExpensesTable.amount],
        currency = this[ExpensesTable.currency],
        category = this[ExpensesTable.category],
        expenseDate = this[ExpensesTable.expenseDate],
        splitType = SplitType.valueOf(this[ExpensesTable.splitType]),
        createdBy = this[ExpensesTable.createdBy],
        createdAt = this[ExpensesTable.createdAt],
        updatedAt = this[ExpensesTable.updatedAt],
        version = this[ExpensesTable.version],
        deletedAt = this[ExpensesTable.deletedAt]
    )

    private fun ResultRow.toExpenseSplit() = ExpenseSplit(
        id = this[ExpenseSplitsTable.id],
        expenseId = this[ExpenseSplitsTable.expenseId],
        participantUserId = this[ExpenseSplitsTable.participantUserId],
        shareType = SplitType.valueOf(this[ExpenseSplitsTable.shareType]),
        value = this[ExpenseSplitsTable.value],
        amountInExpenseCurrency = this[ExpenseSplitsTable.amountInExpenseCurrency]
    )
}
