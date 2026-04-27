package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object AttachmentsTable : Table("attachments") {
    val id = uuid("id").autoGenerate()
    val tripId = uuid("trip_id").references(
        TripsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val expenseId = uuid("expense_id").references(
        ExpensesTable.id,
        onDelete = ReferenceOption.SET_NULL
    ).nullable()
    val uploadedBy = uuid("uploaded_by").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val fileName = varchar("file_name", 255)
    val fileSize = long("file_size")
    val mimeType = varchar("mime_type", 100)
    val s3Key = varchar("s3_key", 500)
    val createdAt = timestamp("created_at")
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
