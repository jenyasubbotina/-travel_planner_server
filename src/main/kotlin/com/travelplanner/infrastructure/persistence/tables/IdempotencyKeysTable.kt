package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object IdempotencyKeysTable : Table("idempotency_keys") {
    val key = varchar("key", 255)
    val userId = uuid("user_id")
    val responseStatus = integer("response_status")
    val responseBody = text("response_body").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val expiresAt = timestamp("expires_at")

    override val primaryKey = PrimaryKey(key)
}
