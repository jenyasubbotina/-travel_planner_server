package com.travelplanner.api.routes

import com.travelplanner.infrastructure.config.AdminConfig
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.AttachmentsTable
import com.travelplanner.infrastructure.persistence.tables.AuthRefreshTokensTable
import com.travelplanner.infrastructure.persistence.tables.DomainEventsTable
import com.travelplanner.infrastructure.persistence.tables.ExpenseSplitsTable
import com.travelplanner.infrastructure.persistence.tables.ExpensesTable
import com.travelplanner.infrastructure.persistence.tables.IdempotencyKeysTable
import com.travelplanner.infrastructure.persistence.tables.ItineraryPointsTable
import com.travelplanner.infrastructure.persistence.tables.TripInvitationsTable
import com.travelplanner.infrastructure.persistence.tables.TripParticipantsTable
import com.travelplanner.infrastructure.persistence.tables.TripsTable
import com.travelplanner.infrastructure.persistence.tables.UserDevicesTable
import com.travelplanner.infrastructure.persistence.tables.UsersTable
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

private val adminLog = LoggerFactory.getLogger("AdminRoutes")
private val serverStartedAt: Instant = Instant.now()

private val LOCALHOST_REMOTES = setOf(
    "127.0.0.1",
    "0:0:0:0:0:0:0:1",
    "::1",
    "localhost",
    "0.0.0.0",
)

@Serializable
private data class AdminUserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
private data class AdminTripDto(
    val id: String,
    val title: String,
    val description: String?,
    val startDate: String?,
    val endDate: String?,
    val status: String,
    val baseCurrency: String,
    val totalBudget: String,
    val destination: String,
    val imageUrl: String?,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val version: Long,
    val deletedAt: String?,
)

@Serializable
private data class AdminParticipantDto(
    val tripId: String,
    val userId: String,
    val role: String,
    val joinedAt: String,
)

@Serializable
private data class AdminItineraryPointDto(
    val id: String,
    val tripId: String,
    val title: String,
    val subtitle: String?,
    val type: String?,
    val date: String?,
    val dayIndex: Int,
    val startTime: String?,
    val endTime: String?,
    val status: String,
    val sortOrder: Int,
    val cost: Double?,
    val createdAt: String,
    val deletedAt: String?,
)

@Serializable
private data class AdminExpenseDto(
    val id: String,
    val tripId: String,
    val payerUserId: String,
    val title: String,
    val amount: String,
    val currency: String,
    val category: String,
    val expenseDate: String,
    val splitType: String,
    val createdBy: String,
    val createdAt: String,
    val deletedAt: String?,
)

@Serializable
private data class AdminExpenseSplitDto(
    val id: String,
    val expenseId: String,
    val participantUserId: String,
    val shareType: String,
    val value: String,
    val amountInExpenseCurrency: String,
)

@Serializable
private data class AdminAttachmentDto(
    val id: String,
    val tripId: String,
    val expenseId: String?,
    val uploadedBy: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val s3Key: String,
    val createdAt: String,
    val deletedAt: String?,
)

@Serializable
private data class AdminInvitationDto(
    val id: String,
    val tripId: String,
    val email: String,
    val invitedBy: String,
    val role: String,
    val status: String,
    val createdAt: String,
    val resolvedAt: String?,
)

@Serializable
private data class AdminDomainEventDto(
    val id: String,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val payload: String,
    val createdAt: String,
    val processedAt: String?,
    val retryCount: Int,
)

@Serializable
private data class ServerInfoDto(
    val startedAt: String,
    val uptimeSeconds: Long,
    val now: String,
    val lastWrites: Map<String, String?>,
    val mostRecentChange: String?,
)

@Serializable
private data class AdminTripDetailDto(
    val trip: AdminTripDto,
    val participants: List<AdminParticipantDto>,
    val itinerary: List<AdminItineraryPointDto>,
    val expenses: List<AdminExpenseDto>,
    val splits: List<AdminExpenseSplitDto>,
    val attachments: List<AdminAttachmentDto>,
    val invitations: List<AdminInvitationDto>,
)

@Serializable
private data class JvmMetricsDto(
    val memoryUsedBytes: Double,
    val memoryCommittedBytes: Double,
    val memoryMaxBytes: Double,
    val heapUsedBytes: Double,
    val heapMaxBytes: Double,
    val nonHeapUsedBytes: Double,
    val gcPauseCount: Long,
    val gcPauseTotalSeconds: Double,
    val gcPauseMaxSeconds: Double,
)

@Serializable
private data class ProcessMetricsDto(
    val cpuCount: Double?,
    val systemCpuUsage: Double?,
    val processCpuUsage: Double?,
    val systemLoadAverage1m: Double?,
)

@Serializable
private data class HttpRouteMetricsDto(
    val route: String,
    val method: String,
    val status: String,
    val count: Long,
    val totalSeconds: Double,
    val maxSeconds: Double,
)

@Serializable
private data class HttpMetricsDto(
    val totalRequests: Long,
    val totalDurationSeconds: Double,
    val activeRequests: Double,
    val byRoute: List<HttpRouteMetricsDto>,
)

@Serializable
private data class MetricsSummaryDto(
    val collectedAt: String,
    val jvm: JvmMetricsDto,
    val process: ProcessMetricsDto,
    val http: HttpMetricsDto,
)

// Returns true if from localhost
private suspend fun RoutingContext.requireLocalhost(cfg: AdminConfig): Boolean {
    if (!cfg.bindLocalhostOnly) return true
    val local = call.request.local
    if (local.remoteAddress in LOCALHOST_REMOTES || local.remoteHost in LOCALHOST_REMOTES) return true
    adminLog.warn(
        "Admin call rejected — remoteAddress='{}' remoteHost='{}'",
        local.remoteAddress,
        local.remoteHost,
    )
    call.respond(HttpStatusCode.NotFound)
    return false
}

fun Route.adminRoutes(cfg: AdminConfig, metricsRegistry: PrometheusMeterRegistry) {
    adminLog.info("Admin routes registered — bindLocalhostOnly={}", cfg.bindLocalhostOnly)

    staticResources("/admin", "admin") {
        default("index.html")
    }

    route("/admin/api") {
        get("/stats") {
            if (!requireLocalhost(cfg)) return@get
                val stats = dbQuery {
                    mapOf(
                        "users" to UsersTable.selectAll().count(),
                        "trips" to TripsTable.selectAll().count(),
                        "trips_active" to TripsTable.selectAll().where { TripsTable.deletedAt.isNull() }.count(),
                        "trips_deleted" to TripsTable.selectAll().where { TripsTable.deletedAt.isNotNull() }.count(),
                        "participants" to TripParticipantsTable.selectAll().count(),
                        "expenses" to ExpensesTable.selectAll().count(),
                        "expense_splits" to ExpenseSplitsTable.selectAll().count(),
                        "itinerary_points" to ItineraryPointsTable.selectAll().count(),
                        "attachments" to AttachmentsTable.selectAll().count(),
                        "invitations" to TripInvitationsTable.selectAll().count(),
                        "user_devices" to UserDevicesTable.selectAll().count(),
                        "refresh_tokens" to AuthRefreshTokensTable.selectAll().count(),
                        "domain_events" to DomainEventsTable.selectAll().count(),
                        "domain_events_unprocessed" to DomainEventsTable.selectAll()
                            .where { DomainEventsTable.processedAt.isNull() }.count(),
                        "idempotency_keys" to IdempotencyKeysTable.selectAll().count(),
                    )
                }
                call.respond(HttpStatusCode.OK, stats)
            }

            get("/server-info") {
                if (!requireLocalhost(cfg)) return@get
                val now = Instant.now()
                val info = dbQuery {
                    fun lastTimestamp(table: Table, col: Column<Instant>): Instant? =
                        table.selectAll().orderBy(col, SortOrder.DESC).limit(1)
                            .firstOrNull()?.get(col)

                    val writes = linkedMapOf(
                        "users" to lastTimestamp(UsersTable, UsersTable.updatedAt),
                        "trips" to lastTimestamp(TripsTable, TripsTable.updatedAt),
                        "itinerary_points" to lastTimestamp(ItineraryPointsTable, ItineraryPointsTable.updatedAt),
                        "expenses" to lastTimestamp(ExpensesTable, ExpensesTable.updatedAt),
                        "attachments" to lastTimestamp(AttachmentsTable, AttachmentsTable.createdAt),
                        "domain_events" to lastTimestamp(DomainEventsTable, DomainEventsTable.createdAt),
                    )

                    ServerInfoDto(
                        startedAt = serverStartedAt.toString(),
                        uptimeSeconds = java.time.Duration.between(serverStartedAt, now).seconds,
                        now = now.toString(),
                        lastWrites = writes.mapValues { it.value?.toString() },
                        mostRecentChange = writes.values.filterNotNull().maxOrNull()?.toString(),
                    )
                }
                call.respond(HttpStatusCode.OK, info)
            }

            get("/users") {
                if (!requireLocalhost(cfg)) return@get
                val users = dbQuery {
                    UsersTable.selectAll()
                        .orderBy(UsersTable.createdAt, SortOrder.DESC)
                        .limit(500)
                        .map { it.toUserDto() }
                }
                call.respond(HttpStatusCode.OK, users)
            }

            get("/trips") {
                if (!requireLocalhost(cfg)) return@get
                val trips = dbQuery {
                    TripsTable.selectAll()
                        .orderBy(TripsTable.createdAt, SortOrder.DESC)
                        .limit(500)
                        .map { it.toTripDto() }
                }
                call.respond(HttpStatusCode.OK, trips)
            }

            get("/trips/{id}") {
                if (!requireLocalhost(cfg)) return@get
                val tripId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (tripId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid trip id"))
                    return@get
                }

                val detail = dbQuery {
                    val tripRow = TripsTable.selectAll()
                        .where { TripsTable.id eq tripId }
                        .singleOrNull() ?: return@dbQuery null

                    val participants = TripParticipantsTable.selectAll()
                        .where { TripParticipantsTable.tripId eq tripId }
                        .map { it.toParticipantDto() }

                    val itinerary = ItineraryPointsTable.selectAll()
                        .where { ItineraryPointsTable.tripId eq tripId }
                        .orderBy(ItineraryPointsTable.dayIndex, SortOrder.ASC)
                        .orderBy(ItineraryPointsTable.sortOrder, SortOrder.ASC)
                        .map { it.toItineraryPointDto() }

                    val expenseRows = ExpensesTable.selectAll()
                        .where { ExpensesTable.tripId eq tripId }
                        .orderBy(ExpensesTable.expenseDate, SortOrder.DESC)
                        .toList()
                    val expenses = expenseRows.map { it.toExpenseDto() }
                    val expenseIds = expenseRows.map { it[ExpensesTable.id] }

                    val splits = if (expenseIds.isNotEmpty()) {
                        ExpenseSplitsTable.selectAll()
                            .where { ExpenseSplitsTable.expenseId inList expenseIds }
                            .map { it.toSplitDto() }
                    } else emptyList()

                    val attachments = AttachmentsTable.selectAll()
                        .where { AttachmentsTable.tripId eq tripId }
                        .orderBy(AttachmentsTable.createdAt, SortOrder.DESC)
                        .map { it.toAttachmentDto() }

                    val invitations = TripInvitationsTable.selectAll()
                        .where { TripInvitationsTable.tripId eq tripId }
                        .orderBy(TripInvitationsTable.createdAt, SortOrder.DESC)
                        .map { it.toInvitationDto() }

                    AdminTripDetailDto(
                        trip = tripRow.toTripDto(),
                        participants = participants,
                        itinerary = itinerary,
                        expenses = expenses,
                        splits = splits,
                        attachments = attachments,
                        invitations = invitations,
                    )
                }

                if (detail == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "trip not found"))
                } else {
                    call.respond(HttpStatusCode.OK, detail)
                }
            }

            get("/expenses") {
                if (!requireLocalhost(cfg)) return@get
                val expenses = dbQuery {
                    ExpensesTable.selectAll()
                        .orderBy(ExpensesTable.createdAt, SortOrder.DESC)
                        .limit(200)
                        .map { it.toExpenseDto() }
                }
                call.respond(HttpStatusCode.OK, expenses)
            }

            get("/attachments") {
                if (!requireLocalhost(cfg)) return@get
                val attachments = dbQuery {
                    AttachmentsTable.selectAll()
                        .orderBy(AttachmentsTable.createdAt, SortOrder.DESC)
                        .limit(200)
                        .map { it.toAttachmentDto() }
                }
                call.respond(HttpStatusCode.OK, attachments)
            }

        get("/domain-events") {
            if (!requireLocalhost(cfg)) return@get
            val events = dbQuery {
                DomainEventsTable.selectAll()
                    .orderBy(DomainEventsTable.createdAt, SortOrder.DESC)
                    .limit(100)
                    .map { it.toDomainEventDto() }
            }
            call.respond(HttpStatusCode.OK, events)
        }

        get("/metrics") {
            if (!requireLocalhost(cfg)) return@get
            call.respond(HttpStatusCode.OK, buildMetricsSummary(metricsRegistry))
        }

        get("/metrics/prometheus") {
            if (!requireLocalhost(cfg)) return@get
            call.respondText(
                metricsRegistry.scrape(),
                ContentType.parse("text/plain; version=0.0.4; charset=utf-8"),
            )
        }
    }
}

private fun ResultRow.toUserDto() = AdminUserDto(
    id = this[UsersTable.id].toString(),
    email = this[UsersTable.email],
    displayName = this[UsersTable.displayName],
    avatarUrl = this[UsersTable.avatarUrl],
    createdAt = this[UsersTable.createdAt].toString(),
    updatedAt = this[UsersTable.updatedAt].toString(),
)

private fun ResultRow.toTripDto() = AdminTripDto(
    id = this[TripsTable.id].toString(),
    title = this[TripsTable.title],
    description = this[TripsTable.description],
    startDate = this[TripsTable.startDate]?.toString(),
    endDate = this[TripsTable.endDate]?.toString(),
    status = this[TripsTable.status],
    baseCurrency = this[TripsTable.baseCurrency],
    totalBudget = this[TripsTable.totalBudget].toPlainString(),
    destination = this[TripsTable.destination],
    imageUrl = this[TripsTable.imageUrl],
    createdBy = this[TripsTable.createdBy].toString(),
    createdAt = this[TripsTable.createdAt].toString(),
    updatedAt = this[TripsTable.updatedAt].toString(),
    version = this[TripsTable.version],
    deletedAt = this[TripsTable.deletedAt]?.toString(),
)

private fun ResultRow.toParticipantDto() = AdminParticipantDto(
    tripId = this[TripParticipantsTable.tripId].toString(),
    userId = this[TripParticipantsTable.userId].toString(),
    role = this[TripParticipantsTable.role],
    joinedAt = this[TripParticipantsTable.joinedAt].toString(),
)

private fun ResultRow.toItineraryPointDto() = AdminItineraryPointDto(
    id = this[ItineraryPointsTable.id].toString(),
    tripId = this[ItineraryPointsTable.tripId].toString(),
    title = this[ItineraryPointsTable.title],
    subtitle = this[ItineraryPointsTable.subtitle],
    type = this[ItineraryPointsTable.type],
    date = this[ItineraryPointsTable.date]?.toString(),
    dayIndex = this[ItineraryPointsTable.dayIndex],
    startTime = this[ItineraryPointsTable.startTime]?.toString(),
    endTime = this[ItineraryPointsTable.endTime]?.toString(),
    status = this[ItineraryPointsTable.status],
    sortOrder = this[ItineraryPointsTable.sortOrder],
    cost = this[ItineraryPointsTable.cost],
    createdAt = this[ItineraryPointsTable.createdAt].toString(),
    deletedAt = this[ItineraryPointsTable.deletedAt]?.toString(),
)

private fun ResultRow.toExpenseDto() = AdminExpenseDto(
    id = this[ExpensesTable.id].toString(),
    tripId = this[ExpensesTable.tripId].toString(),
    payerUserId = this[ExpensesTable.payerUserId].toString(),
    title = this[ExpensesTable.title],
    amount = this[ExpensesTable.amount].toPlainString(),
    currency = this[ExpensesTable.currency],
    category = this[ExpensesTable.category],
    expenseDate = this[ExpensesTable.expenseDate].toString(),
    splitType = this[ExpensesTable.splitType],
    createdBy = this[ExpensesTable.createdBy].toString(),
    createdAt = this[ExpensesTable.createdAt].toString(),
    deletedAt = this[ExpensesTable.deletedAt]?.toString(),
)

private fun ResultRow.toSplitDto() = AdminExpenseSplitDto(
    id = this[ExpenseSplitsTable.id].toString(),
    expenseId = this[ExpenseSplitsTable.expenseId].toString(),
    participantUserId = this[ExpenseSplitsTable.participantUserId].toString(),
    shareType = this[ExpenseSplitsTable.shareType],
    value = this[ExpenseSplitsTable.value].toPlainString(),
    amountInExpenseCurrency = this[ExpenseSplitsTable.amountInExpenseCurrency].toPlainString(),
)

private fun ResultRow.toAttachmentDto() = AdminAttachmentDto(
    id = this[AttachmentsTable.id].toString(),
    tripId = this[AttachmentsTable.tripId].toString(),
    expenseId = this[AttachmentsTable.expenseId]?.toString(),
    uploadedBy = this[AttachmentsTable.uploadedBy].toString(),
    fileName = this[AttachmentsTable.fileName],
    fileSize = this[AttachmentsTable.fileSize],
    mimeType = this[AttachmentsTable.mimeType],
    s3Key = this[AttachmentsTable.s3Key],
    createdAt = this[AttachmentsTable.createdAt].toString(),
    deletedAt = this[AttachmentsTable.deletedAt]?.toString(),
)

private fun ResultRow.toInvitationDto() = AdminInvitationDto(
    id = this[TripInvitationsTable.id].toString(),
    tripId = this[TripInvitationsTable.tripId].toString(),
    email = this[TripInvitationsTable.email],
    invitedBy = this[TripInvitationsTable.invitedBy].toString(),
    role = this[TripInvitationsTable.role],
    status = this[TripInvitationsTable.status],
    createdAt = this[TripInvitationsTable.createdAt].toString(),
    resolvedAt = this[TripInvitationsTable.resolvedAt]?.toString(),
)

private fun ResultRow.toDomainEventDto() = AdminDomainEventDto(
    id = this[DomainEventsTable.id].toString(),
    eventType = this[DomainEventsTable.eventType],
    aggregateType = this[DomainEventsTable.aggregateType],
    aggregateId = this[DomainEventsTable.aggregateId].toString(),
    payload = this[DomainEventsTable.payload],
    createdAt = this[DomainEventsTable.createdAt].toString(),
    processedAt = this[DomainEventsTable.processedAt]?.toString(),
    retryCount = this[DomainEventsTable.retryCount],
)

private fun buildMetricsSummary(registry: PrometheusMeterRegistry): MetricsSummaryDto {
    fun gaugeSum(name: String, vararg tags: Pair<String, String>): Double {
        val search = tags.fold(registry.find(name)) { s, (k, v) -> s.tag(k, v) }
        return search.gauges().sumOf { g -> g.value().takeIf { it.isFinite() } ?: 0.0 }
    }

    fun gaugeOrNull(name: String): Double? {
        val v = registry.find(name).gauge()?.value() ?: return null
        return v.takeIf { it.isFinite() }
    }

    val httpTimers = registry.find("ktor.http.server.requests").timers()
    val gcTimers = registry.find("jvm.gc.pause").timers()

    val jvm = JvmMetricsDto(
        memoryUsedBytes = gaugeSum("jvm.memory.used"),
        memoryCommittedBytes = gaugeSum("jvm.memory.committed"),
        memoryMaxBytes = gaugeSum("jvm.memory.max"),
        heapUsedBytes = gaugeSum("jvm.memory.used", "area" to "heap"),
        heapMaxBytes = gaugeSum("jvm.memory.max", "area" to "heap"),
        nonHeapUsedBytes = gaugeSum("jvm.memory.used", "area" to "nonheap"),
        gcPauseCount = gcTimers.sumOf { it.count() },
        gcPauseTotalSeconds = gcTimers.sumOf { it.totalTime(TimeUnit.SECONDS) },
        gcPauseMaxSeconds = gcTimers.maxOfOrNull { it.max(TimeUnit.SECONDS) } ?: 0.0,
    )

    val process = ProcessMetricsDto(
        cpuCount = gaugeOrNull("system.cpu.count"),
        systemCpuUsage = gaugeOrNull("system.cpu.usage"),
        processCpuUsage = gaugeOrNull("process.cpu.usage"),
        systemLoadAverage1m = gaugeOrNull("system.load.average.1m"),
    )

    val byRoute = httpTimers
        .map { timer ->
            HttpRouteMetricsDto(
                route = timer.id.getTag("route") ?: timer.id.getTag("address") ?: "(unknown)",
                method = timer.id.getTag("method") ?: "?",
                status = timer.id.getTag("status") ?: "?",
                count = timer.count(),
                totalSeconds = timer.totalTime(TimeUnit.SECONDS),
                maxSeconds = timer.max(TimeUnit.SECONDS),
            )
        }
        .sortedByDescending { it.count }

    val activeRequests = registry.find("ktor.http.server.requests.active")
        .gauges()
        .sumOf { g -> g.value().takeIf { it.isFinite() } ?: 0.0 }

    val http = HttpMetricsDto(
        totalRequests = httpTimers.sumOf { it.count() },
        totalDurationSeconds = httpTimers.sumOf { it.totalTime(TimeUnit.SECONDS) },
        activeRequests = activeRequests,
        byRoute = byRoute,
    )

    return MetricsSummaryDto(
        collectedAt = Instant.now().toString(),
        jvm = jvm,
        process = process,
        http = http,
    )
}
