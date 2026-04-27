package com.travelplanner.infrastructure.persistence.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object UserDevicesTable : Table("user_devices") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(
        UsersTable.id,
        onDelete = ReferenceOption.CASCADE
    )
    val fcmToken = varchar("fcm_token", 500)
    val deviceName = varchar("device_name", 255).nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
