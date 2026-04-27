package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TripParticipantsTable : Table("trip_participants") {
    val tripId = uuid("trip_id").references(
        TripsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val userId = uuid("user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val role = varchar("role", 20).default("EDITOR")
    val joinedAt = timestamp("joined_at")

    override val primaryKey = PrimaryKey(tripId, userId)
}
