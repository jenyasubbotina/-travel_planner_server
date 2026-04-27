package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TripChecklistItemsTable : Table("trip_checklist_items") {
    val id = uuid("id").autoGenerate()
    val tripId = uuid("trip_id").references(
        TripsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val title = varchar("title", 500)
    val isGroup = bool("is_group").default(false)
    val ownerUserId = uuid("owner_user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
