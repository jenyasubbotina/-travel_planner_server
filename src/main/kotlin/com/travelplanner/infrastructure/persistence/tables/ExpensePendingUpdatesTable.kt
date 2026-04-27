package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ExpensePendingUpdatesTable : Table("expense_pending_updates") {
    val expenseId = uuid("expense_id").references(
        ExpensesTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val proposedByUserId = uuid("proposed_by_user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val proposedAt = timestamp("proposed_at")
    val baseVersion = long("base_version")
    val payload = text("payload")

    override val primaryKey = PrimaryKey(expenseId)
}
