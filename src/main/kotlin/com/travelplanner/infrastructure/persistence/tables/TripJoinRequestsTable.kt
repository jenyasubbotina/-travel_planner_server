package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TripJoinRequestsTable : Table("trip_join_requests") {
    val id = uuid("id").autoGenerate()
    val tripId = uuid("trip_id").references(
        TripsTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val requesterUserId = uuid("requester_user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val status = varchar("status", 20).default("PENDING")
    val createdAt = timestamp("created_at")
    val resolvedAt = timestamp("resolved_at").nullable()
    val resolvedBy = uuid("resolved_by").references(
        UsersTable.id,
        onDelete = ReferenceOption.SET_NULL
    ).nullable()

    override val primaryKey = PrimaryKey(id)
}
