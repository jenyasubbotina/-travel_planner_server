package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UsersTable : Table("users") {
    val id = uuid("id").autoGenerate()
    val email = varchar("email", 255).uniqueIndex()
    val displayName = varchar("display_name", 255)
    val passwordHash = varchar("password_hash", 255)
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val emailVerifiedAt = timestamp("email_verified_at").nullable()
    val emailVerificationTokenHash = varchar("email_verification_token_hash", 64).nullable()
    val emailVerificationExpiresAt = timestamp("email_verification_expires_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}
