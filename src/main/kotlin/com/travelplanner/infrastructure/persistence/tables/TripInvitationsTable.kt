package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object TripInvitationsTable : Table("trip_invitations") {
    val id = pgGeneratedUuid("id")
    val tripId = uuid("trip_id").references(
        TripsTable.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "trip_invitations_trip_id_fkey"
    )
    val email = varchar("email", 255)
    val invitedBy = uuid("invited_by").references(
        UsersTable.id,
        onDelete = ReferenceOption.NO_ACTION,
        onUpdate = ReferenceOption.NO_ACTION,
        fkName = "trip_invitations_invited_by_fkey"
    )
    val role = varchar("role", 20).default("EDITOR")
    val status = varchar("status", 20).default("PENDING")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val resolvedAt = timestamp("resolved_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
