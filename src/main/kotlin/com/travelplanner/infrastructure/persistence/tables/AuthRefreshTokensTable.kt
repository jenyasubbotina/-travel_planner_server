package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object AuthRefreshTokensTable : Table("auth_refresh_tokens") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 255).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
