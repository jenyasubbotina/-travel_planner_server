package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object TripInvitationsTable : Table("trip_invitations") {
    val id = uuid("id").autoGenerate()
    val tripId = uuid("trip_id").references(TripsTable.id, onDelete = ReferenceOption.CASCADE)
    val email = varchar("email", 255)
    val invitedBy = uuid("invited_by").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 20).default("EDITOR")
    val status = varchar("status", 20).default("PENDING")
    val createdAt = timestamp("created_at")
    val resolvedAt = timestamp("resolved_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
