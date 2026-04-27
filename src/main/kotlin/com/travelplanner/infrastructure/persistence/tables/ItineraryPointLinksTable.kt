package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ItineraryPointLinksTable : Table("itinerary_point_links") {
    val id = uuid("id").autoGenerate()
    val pointId = uuid("point_id").references(
        ItineraryPointsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val title = varchar("title", 255)
    val url = text("url")
    val sortOrder = integer("sort_order").default(0)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
