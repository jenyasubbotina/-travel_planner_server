package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object ExpenseSplitsTable : Table("expense_splits") {
    val id = pgGeneratedUuid("id")
    val expenseId = uuid("expense_id").references(
        ExpensesTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "expense_splits_expense_id_fkey"
    )
    val participantUserId = uuid("participant_user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.NO_ACTION,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "expense_splits_participant_user_id_fkey"
    )
    val shareType = varchar("share_type", 20)
    val value = decimal("value", 15, 4)
    val amountInExpenseCurrency = decimal("amount_in_expense_currency", 15, 2)

    override val primaryKey = PrimaryKey(id)
}
