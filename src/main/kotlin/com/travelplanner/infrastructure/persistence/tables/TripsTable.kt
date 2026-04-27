package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp
import java.math.BigDecimal

object TripsTable : Table("trips") {
    val id = uuid("id").autoGenerate()
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val startDate = date("start_date").nullable()
    val endDate = date("end_date").nullable()
    val baseCurrency = varchar("base_currency", 10).default("USD")
    val totalBudget = decimal("total_budget", 18, 2).default(BigDecimal.ZERO)
    val destination = varchar("destination", 255).default("")
    val imageUrl = text("image_url").nullable()
    val joinCode = varchar("join_code", 8).default("")
    val status = varchar("status", 20).default("ACTIVE")
    val createdBy = uuid("created_by").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(1)
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
