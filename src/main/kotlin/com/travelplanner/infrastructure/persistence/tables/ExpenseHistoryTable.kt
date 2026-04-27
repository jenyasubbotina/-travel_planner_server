package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ExpenseHistoryTable : Table("expense_history") {
    val expenseId = uuid("expense_id").references(
        ExpensesTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val version = long("version")
    val snapshot = text("snapshot")
    val editedByUserId = uuid("edited_by_user_id").references(UsersTable.id)
    val editedAt = timestamp("edited_at")

    override val primaryKey = PrimaryKey(expenseId, version)
}
