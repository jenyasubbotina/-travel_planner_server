package com.example.models

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object TripsTable : Table("trips") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 255)
    val destination = varchar("destination", 255)

    val startDate = long("start_date")
    val endDate = long("end_date")

    val totalBudget = double("total_budget")
    val description = text("description").nullable()
    val ownerUserId = varchar("owner_user_id", 100)
    val joinCode = varchar("join_code", 10)
    val status = varchar("status", 50).default("ACTIVE")

    val currency = varchar("currency", 10).default("¥")
    val imageUrl = varchar("image_url", 500).nullable()
    val filesJson = text("files_json").default("[]")

    override val primaryKey = PrimaryKey(id)
}

object TripParticipantsTable : Table("trip_participants") {
    val tripId = reference("trip_id", TripsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 100)
    val isPending = bool("is_pending").default(false)

    val name = varchar("name", 255)
    val email = varchar("email", 255)

    override val primaryKey = PrimaryKey(tripId, userId)
}

object ExpensesTable : Table("expenses") {
    val id = varchar("id", 36)
    val tripId = reference("trip_id", TripsTable.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val amount = double("amount")
    val category = varchar("category", 100)
    val payerUserId = varchar("payer_user_id", 100)
    val date = long("date")
    val splitsJson = text("splits_json")

    val creatorUserId = varchar("creator_user_id", 100)
    val pendingUpdateJson = text("pending_update_json").nullable()

    val imageUrl = varchar("image_url", 500).nullable()

    override val primaryKey = PrimaryKey(id)
}

object EventsTable : Table("events") {
    val id = varchar("id", 36)
    val tripId = reference("trip_id", TripsTable.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val eventDataJson = text("event_data_json")

    override val primaryKey = PrimaryKey(id)
}

object HistoryLogsTable : Table("history_logs") {
    val id = varchar("id", 36)
    val tripId = reference("trip_id", TripsTable.id, onDelete = ReferenceOption.CASCADE)
    val userId = varchar("user_id", 100)
    val actionType = varchar("action_type", 50)
    val entityType = varchar("entity_type", 50)
    val entityId = varchar("entity_id", 100)
    val details = text("details")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

object ChecklistTable : Table("checklist") {
    val id = varchar("id", 50)
    val tripId = long("tripId").references(TripsTable.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val isGroup = bool("isGroup").default(false)
    val ownerUserId = varchar("ownerUserId", 50)
    val completedByJson = text("completedByJson").default("[]")
    override val primaryKey = PrimaryKey(id)
}

fun Application.configureDatabases() {
    val dbFile = File("data/db.sqlite")
    if (!dbFile.parentFile.exists()) {
        dbFile.parentFile.mkdirs()
    }

    val database = Database.connect(
        url = "jdbc:sqlite:${dbFile.absolutePath}",
        driver = "org.sqlite.JDBC"
    )

    transaction(database) {
        SchemaUtils.create(
            TripsTable,
            TripParticipantsTable,
            ExpensesTable,
            EventsTable,
            HistoryLogsTable,
            ChecklistTable
        )
    }
}