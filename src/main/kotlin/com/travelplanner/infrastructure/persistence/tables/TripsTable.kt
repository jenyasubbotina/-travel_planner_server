package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object TripsTable : Table("trips") {
    val id = uuid("id").autoGenerate()
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val startDate = date("start_date").nullable()
    val endDate = date("end_date").nullable()
    val baseCurrency = varchar("base_currency", 10).default("USD")
    val status = varchar("status", 20).default("ACTIVE")
    val createdBy = uuid("created_by").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(1)
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
