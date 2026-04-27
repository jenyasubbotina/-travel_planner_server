package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ItineraryPointCommentsTable : Table("itinerary_point_comments") {
    val id = uuid("id").autoGenerate()
    val pointId = uuid("point_id").references(
        ItineraryPointsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val authorUserId = uuid("author_user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val text = text("text")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
