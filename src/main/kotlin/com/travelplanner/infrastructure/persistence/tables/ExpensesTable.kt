package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object ExpensesTable : Table("expenses") {
    val id = pgGeneratedUuid("id")
    val tripId = uuid("trip_id").references(
        TripsTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "expenses_trip_id_fkey"
    )
    val payerUserId = uuid("payer_user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.NO_ACTION,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "expenses_payer_user_id_fkey"
    )
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val amount = decimal("amount", 15, 2)
    val currency = varchar("currency", 10)
    val category = varchar("category", 100)
    val expenseDate = date("expense_date")
    val splitType = varchar("split_type", 20).default("EQUAL")
    val createdBy = uuid("created_by").references(
        UsersTable.id,
        onDelete = ReferenceOption.NO_ACTION,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "expenses_created_by_fkey"
    )
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    val version = long("version").default(1)
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
