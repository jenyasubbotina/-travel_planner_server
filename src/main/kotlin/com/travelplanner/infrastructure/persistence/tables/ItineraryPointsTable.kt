package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestamp

object ItineraryPointsTable : Table("itinerary_points") {
    val id = uuid("id").autoGenerate()
    val tripId = uuid("trip_id").references(TripsTable.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val subtitle = varchar("subtitle", 500).nullable()
    val type = varchar("type", 50).nullable()
    val date = date("date").nullable()
    val dayIndex = integer("day_index").default(0)
    val startTime = time("start_time").nullable()
    val endTime = time("end_time").nullable()
    val duration = varchar("duration", 50).nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val address = varchar("address", 500).nullable()
    val cost = double("cost").nullable()
    val actualCost = double("actual_cost").nullable()
    val status = varchar("status", 20).default("NONE")
    val sortOrder = integer("sort_order").default(0)
    val createdBy = uuid("created_by").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(1)
    val deletedAt = timestamp("deleted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object ItineraryPointParticipantsTable : Table("itinerary_point_participants") {
    val pointId = uuid("point_id").references(ItineraryPointsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(pointId, userId)
}
