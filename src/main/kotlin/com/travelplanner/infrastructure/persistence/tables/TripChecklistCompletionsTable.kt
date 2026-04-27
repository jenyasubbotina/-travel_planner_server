package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TripChecklistCompletionsTable : Table("trip_checklist_completions") {
    val itemId = uuid("item_id").references(
        TripChecklistItemsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val userId = uuid("user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val completedAt = timestamp("completed_at")

    override val primaryKey = PrimaryKey(itemId, userId)
}
