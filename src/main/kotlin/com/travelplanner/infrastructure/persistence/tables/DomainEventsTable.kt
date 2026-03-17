package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object DomainEventsTable : Table("domain_events") {
    val id = uuid("id").autoGenerate()
    val eventType = varchar("event_type", 100)
    val aggregateType = varchar("aggregate_type", 50)
    val aggregateId = uuid("aggregate_id")
    val payload = text("payload")
    val createdAt = timestamp("created_at")
    val processedAt = timestamp("processed_at").nullable()
    val retryCount = integer("retry_count").default(0)

    override val primaryKey = PrimaryKey(id)
}
