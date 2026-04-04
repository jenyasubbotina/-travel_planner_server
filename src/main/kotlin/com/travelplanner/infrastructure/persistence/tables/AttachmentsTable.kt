package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object AttachmentsTable : Table("attachments") {
    val id = pgGeneratedUuid("id")
    val tripId = uuid("trip_id").references(
        TripsTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "attachments_trip_id_fkey"
    )
    val expenseId = uuid("expense_id").references(
        ExpensesTable.id,
        onDelete = ReferenceOption.SET_NULL,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "attachments_expense_id_fkey"
    ).nullable()
    val uploadedBy = uuid("uploaded_by").references(
        UsersTable.id,
        onDelete = ReferenceOption.NO_ACTION,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "attachments_uploaded_by_fkey"
    )
    val fileName = varchar("file_name", 255)
    val fileSize = long("file_size")
    val mimeType = varchar("mime_type", 100)
    val s3Key = varchar("s3_key", 500)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
