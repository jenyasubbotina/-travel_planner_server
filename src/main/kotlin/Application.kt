package com.example

import com.example.history.ActionLogger
import com.example.models.*
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.util.UUID
import kotlin.time.toKotlinDuration


fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        })
    }
    install(CallLogging)
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15).toKotlinDuration()
        timeout = Duration.ofSeconds(15).toKotlinDuration()
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    configureDatabases()

    val tripRepository = TripRepository()

    val connectionManager = ConnectionManager()

    val actionLogger = ActionLogger(tripRepository, connectionManager)

    routing {
        staticFiles("/uploads", File("data/uploads"))

        post("/upload") {
            val multipart = call.receiveMultipart()
            var fileUrl: String? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val ext = File(part.originalFileName ?: "image.jpg").extension
                    val name = "${UUID.randomUUID()}.$ext"
                    val file = File("data/uploads/$name")
                    file.parentFile.mkdirs()

                    part.streamProvider().use { its ->
                        file.outputStream().buffered().use { out ->
                            its.copyTo(out)
                        }
                    }
                    fileUrl = "/uploads/$name"
                }
                part.dispose()
            }

            if (fileUrl != null) {
                call.respond(mapOf("url" to fileUrl))
            } else {
                call.respond(HttpStatusCode.BadRequest, "No image found")
            }
        }

        route("/trips") {

            get {
                val userId = call.request.headers["X-User-Id"]
                if (userId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, "Missing X-User-Id header")
                    return@get
                }
                call.respond(tripRepository.getUserTrips(userId))
            }

            post {
                val userId = call.request.headers["X-User-Id"]
                if (userId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.Unauthorized, "Missing X-User-Id header")
                    return@post
                }
                try {
                    val req = call.receive<CreateTripRequest>()
                    val secureReq = req.copy(ownerUserId = userId)
                    val created = tripRepository.createTrip(secureReq)

                    actionLogger.logTripCreated(created.id, userId, created)

                    call.respond(HttpStatusCode.Created, created)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown Error")
                }
            }

            post("/join") {
                val userIdHeader = call.request.headers["X-User-Id"]
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                try {
                    val req = call.receive<JoinByCodeRequest>()
                    val user = UserDto(userIdHeader, req.userName, "email@stub.com")

                    val joinedTrip = tripRepository.joinTripByCode(req.code, user)

                    if (joinedTrip != null) {
                        connectionManager.broadcastToTrip(
                            tripId = joinedTrip.id,
                            event = TripEvent.ParticipantJoined(joinedTrip.id, user),
                            tripRepository = tripRepository
                        )

                        actionLogger.logParticipantJoined(joinedTrip.id, userIdHeader, user)

                        call.respond(HttpStatusCode.OK, joinedTrip)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Invalid invite code")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown Error")
                }
            }

            post("/join-request") {
                val userIdHeader = call.request.headers["X-User-Id"]
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                try {
                    val req = call.receive<JoinByCodeRequest>()
                    val user = UserDto(userIdHeader, req.userName, "email@stub.com")

                    val requestedTrip = tripRepository.requestJoinByCode(req.code, user)
                    if (requestedTrip != null) {
                        connectionManager.broadcastToTrip(
                            tripId = requestedTrip.id,
                            event = TripEvent.JoinRequestReceived(requestedTrip.id, user),
                            tripRepository = tripRepository
                        )
                        call.respond(HttpStatusCode.OK, requestedTrip)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Invalid invite code")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown Error")
                }
            }

            route("/{tripId}") {

                get {
                    call.withTripAccess(tripRepository) { tripId, _ ->
                        val trip = tripRepository.getTrip(tripId)
                        if (trip != null) call.respond(trip) else call.respond(HttpStatusCode.NotFound)
                    }
                }

                get("/history") {
                    call.withTripAccess(tripRepository) { tripId, _ ->
                        call.respond(tripRepository.getHistoryLogs(tripId))
                    }
                }

                delete {
                    call.withTripAccess(tripRepository, allowArchived = true) { tripId, userId ->
                        val tripBeforeDelete = tripRepository.getTrip(tripId)
                        val participantsBeforeAction = tripRepository.getParticipants(tripId)

                        val result = tripRepository.leaveOrDeleteTrip(tripId, userId)
                        when (result) {
                            "DELETED" -> {
                                if (tripBeforeDelete != null) {
                                    actionLogger.logTripDeleted(tripId, userId, tripBeforeDelete)
                                }
                                connectionManager.broadcastToTrip(
                                    tripId = tripId,
                                    event = TripEvent.TripDeleted(tripId),
                                    tripRepository = tripRepository
                                )
                                call.respond(HttpStatusCode.OK, "Deleted")
                            }

                            "LEFT" -> {
                                connectionManager.broadcastToTrip(
                                    tripId = tripId,
                                    event = TripEvent.ParticipantLeft(tripId, userId),
                                    tripRepository = tripRepository
                                )
                                val leftUser = participantsBeforeAction?.find { it.id == userId }
                                    ?: UserDto(userId, "Unknown", "")
                                actionLogger.logParticipantLeft(tripId, userId, leftUser)

                                call.respond(HttpStatusCode.OK, "Left")
                            }

                            else -> call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }

                put("/budget") {
                    call.withTripOwner(tripRepository) { tripId, userId ->
                        try {
                            val req = call.receive<UpdateBudgetRequest>()

                            val oldTrip = tripRepository.getTrip(tripId)
                            if (oldTrip == null) {
                                call.respond(HttpStatusCode.NotFound)
                                return@withTripOwner
                            }

                            // 2. Perform the update
                            val updated = tripRepository.updateTripBudget(tripId, req.totalBudget)

                            if (updated != null) {
                                connectionManager.broadcastToTrip(
                                    tripId = tripId,
                                    event = TripEvent.TripUpdated(tripId, updated),
                                    tripRepository = tripRepository
                                )

                                // 3. Use ActionLogger with BOTH states
                                actionLogger.logTripUpdated(tripId, userId, oldTrip, updated, "BUDGET_UPDATED")

                                call.respond(HttpStatusCode.OK, updated)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid request")
                        }
                    }
                }

                put("/status") {
                    call.withTripOwner(tripRepository, allowArchived = true) { tripId, userId ->
                        val status = call.request.queryParameters["status"] ?: "PLANNED"

                        val oldTrip = tripRepository.getTrip(tripId)
                        if (oldTrip == null) {
                            call.respond(HttpStatusCode.NotFound)
                            return@withTripOwner
                        }

                        val updated = tripRepository.updateTripStatus(tripId, status)

                        if (updated != null) {
                            connectionManager.broadcastToTrip(
                                tripId = tripId,
                                event = TripEvent.TripUpdated(tripId, updated),
                                tripRepository = tripRepository
                            )

                            actionLogger.logTripUpdated(tripId, userId, oldTrip, updated, "STATUS_UPDATED: $status")

                            call.respond(HttpStatusCode.OK)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }

                post("/regenerate-code") {
                    call.withTripOwner(tripRepository) { tripId, ownerId ->
                        val newCode = tripRepository.regenerateJoinCode(tripId)
                        if (newCode != null) {
                            connectionManager.broadcastToTrip(
                                tripId = tripId,
                                event = TripEvent.CodeRegenerated(tripId, newCode),
                                tripRepository = tripRepository
                            )
                            actionLogger.logCodeRegenerated(tripId, ownerId, newCode)
                            call.respond(HttpStatusCode.OK, mapOf("newCode" to newCode))
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                }

                put("/files") {
                    call.withTripAccess(tripRepository) { tripId, userId ->
                        try {
                            @kotlinx.serialization.Serializable
                            data class UpdateFilesRequest(val filesJson: String)

                            val req = call.receive<UpdateFilesRequest>()

                            // Fetch old files before updating
                            val oldTrip = tripRepository.getTrip(tripId)
                            val oldFilesJson = oldTrip?.filesJson ?: "[]"

                            val updated = tripRepository.updateTripFilesJson(tripId, req.filesJson)

                            if (updated != null) {
                                connectionManager.broadcastToTrip(
                                    tripId = tripId,
                                    event = TripEvent.TripFilesUpdated(tripId, req.filesJson),
                                    tripRepository = tripRepository
                                )
                                actionLogger.logTripFilesUpdated(tripId, userId, oldFilesJson, req.filesJson)
                                call.respond(HttpStatusCode.OK, updated)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid request")
                        }
                    }
                }

                // 2. Participants
                route("/participants") {
                    get {
                        call.withTripAccess(tripRepository) { tripId, _ ->
                            call.respond(tripRepository.getParticipants(tripId))
                        }
                    }

                    post {
                        val tripId = call.parameters["tripId"]?.toLongOrNull()
                            ?: return@post call.respond(HttpStatusCode.BadRequest)

                        val userIdHeader = call.request.headers["X-User-Id"]
                            ?: return@post call.respond(HttpStatusCode.Unauthorized)

                        try {
                            val req = call.receive<JoinTripRequest>()
                            val userDto = UserDto(id = userIdHeader, name = req.userName, email = "email@stub.com")

                            // 1. Capture the boolean result!
                            val isNewJoin = tripRepository.joinTrip(tripId, userDto)

                            // 2. ONLY broadcast and log if it was an actual new join
                            if (isNewJoin) {
                                connectionManager.broadcastToTrip(
                                    tripId = tripId,
                                    event = TripEvent.ParticipantJoined(tripId, userDto),
                                    tripRepository = tripRepository
                                )
                                // Use ActionLogger
                                actionLogger.logParticipantJoined(tripId, userIdHeader, userDto)
                            }

                            call.respond(HttpStatusCode.OK)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            call.respond(HttpStatusCode.InternalServerError, e.message ?: "Unknown Error")
                        }
                    }
                }

                // 3. Join Requests
                route("/requests") {
                    get {
                        call.withTripOwner(tripRepository) { tripId, _ ->
                            call.respond(tripRepository.getPendingRequests(tripId))
                        }
                    }

                    post("/{targetUserId}/resolve") {
                        call.withTripOwner(tripRepository) { tripId, ownerId ->
                            val targetUserId = call.parameters["targetUserId"]
                                ?: return@withTripOwner call.respond(HttpStatusCode.BadRequest)
                            val approve = call.request.queryParameters["approve"]?.toBooleanStrictOrNull() ?: true

                            tripRepository.resolveJoinRequest(tripId, targetUserId, approve)

                            if (approve) {
                                val user = tripRepository.getParticipants(tripId).find { it.id == targetUserId }
                                if (user != null) {
                                    connectionManager.broadcastToTrip(
                                        tripId = tripId,
                                        event = TripEvent.ParticipantJoined(tripId, user),
                                        tripRepository = tripRepository
                                    )
                                    actionLogger.logParticipantJoined(tripId, ownerId, user)
                                }
                            }
                            connectionManager.broadcastToTrip(
                                tripId = tripId,
                                event = TripEvent.JoinRequestResolved(tripId, targetUserId, approve),
                                tripRepository = tripRepository
                            )
                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }

                // 4. Expenses
                route("/expenses") {
                    get {
                        call.withTripAccess(tripRepository) { tripId, _ ->
                            call.respond(tripRepository.getAllExpenses(tripId))
                        }
                    }
                    post {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val request = call.receive<CreateExpenseRequest>()

                            // PASS THE userId HERE!
                            val created = tripRepository.addExpense(tripId, userId, request)

                            connectionManager.broadcastToTrip(
                                tripId = tripId,
                                event = TripEvent.ExpenseAdded(tripId, created),
                                tripRepository = tripRepository
                            )
                            actionLogger.logExpenseAdded(tripId, userId, created)

                            call.respond(HttpStatusCode.Created, created)
                        }
                    }

                    put("/{expenseId}") {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val expenseId = call.parameters["expenseId"]
                                ?: return@withTripAccess call.respond(HttpStatusCode.BadRequest)
                            val request = call.receive<CreateExpenseRequest>()

                            // 1. Fetch current expense
                            val currentExpense = tripRepository.getExpenseById(tripId, expenseId)
                            if (currentExpense == null) return@withTripAccess call.respond(HttpStatusCode.NotFound)

                            // 2. CONFLICT LOGIC: Are you the creator?
                            if (currentExpense.creatorUserId == userId) {
                                // YES: Apply immediately
                                val updated = tripRepository.updateExpense(tripId, expenseId, request)
                                if (updated != null) {
                                    connectionManager.broadcastToTrip(
                                        tripId,
                                        TripEvent.ExpenseUpdated(tripId, updated),
                                        tripRepository
                                    )
                                    actionLogger.logExpenseUpdated(tripId, userId, currentExpense, updated)
                                    call.respond(HttpStatusCode.OK, updated)
                                }
                            } else {
                                // NO: Save as a Pending Update (Conflict)
                                val editorName =
                                    tripRepository.getParticipants(tripId).find { it.id == userId }?.name ?: "Unknown"
                                val pendingUpdate = PendingExpenseUpdateDto(
                                    editorUserId = userId,
                                    editorName = editorName,
                                    timestamp = System.currentTimeMillis(),
                                    proposedExpense = request
                                )

                                // Save pending json to DB
                                val updated = tripRepository.savePendingUpdate(tripId, expenseId, pendingUpdate)
                                if (updated != null) {
                                    connectionManager.broadcastToTrip(
                                        tripId,
                                        TripEvent.ExpenseUpdated(tripId, updated),
                                        tripRepository
                                    )
                                    call.respond(HttpStatusCode.OK, updated)
                                }
                            }
                        }
                    }

                    // NEW ROUTE: Resolve the conflict
                    post("/{expenseId}/resolve") {
                        call.withTripOwner(tripRepository) { tripId, userId ->
                            val expenseId = call.parameters["expenseId"] ?: return@withTripOwner call.respond(
                                HttpStatusCode.BadRequest
                            )
                            val accept = call.request.queryParameters["accept"]?.toBooleanStrictOrNull() ?: false

                            val currentExpense = tripRepository.getExpenseById(tripId, expenseId)
                            if (currentExpense?.pendingUpdate == null) return@withTripOwner call.respond(HttpStatusCode.BadRequest)

                            val resolvedExpense = if (accept) {
                                tripRepository.applyPendingUpdate(tripId, expenseId)
                            } else {
                                tripRepository.clearPendingUpdate(tripId, expenseId)
                            }

                            if (resolvedExpense != null) {
                                connectionManager.broadcastToTrip(
                                    tripId,
                                    TripEvent.ExpenseUpdated(tripId, resolvedExpense),
                                    tripRepository
                                )
                                if (accept) {
                                    actionLogger.logExpenseUpdated(tripId, userId, currentExpense, resolvedExpense)
                                }
                                // 🟢 CHANGE THIS LINE: Return the resolvedExpense object instead of just OK!
                                call.respond(HttpStatusCode.OK, resolvedExpense)
                            }
                        }
                    }

                    delete("/{expenseId}") {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val expenseId = call.parameters["expenseId"]
                                ?: return@withTripAccess call.respond(HttpStatusCode.BadRequest)

                            // 1. Fetch the full expense BEFORE deleting for rich history logging
                            val expenseToDelete = tripRepository.getExpenseById(tripId, expenseId)
                                ?: return@withTripAccess call.respond(HttpStatusCode.NotFound)

                            // 2. Perform the deletion
                            if (tripRepository.deleteExpense(tripId, expenseId)) {

                                connectionManager.broadcastToTrip(
                                    tripId = tripId,
                                    event = TripEvent.ExpenseDeleted(tripId, expenseId),
                                    tripRepository = tripRepository
                                )

                                // 3. Log with full expense data for detailed history
                                actionLogger.logExpenseDeleted(tripId, userId, expenseToDelete)

                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                    }
                }

                // 5. Events
                route("/events") {
                    get {
                        call.withTripAccess(tripRepository) { tripId, _ ->
                            call.respond(tripRepository.getAllEvents(tripId))
                        }
                    }
                    post {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val request = call.receive<EventDto>()
                            val newEvent = request.copy(
                                id = request.id.ifBlank { UUID.randomUUID().toString() },
                                tripId = tripId
                            )
                            tripRepository.addEvent(tripId, newEvent)

                            connectionManager.broadcastToTrip(
                                tripId = tripId,
                                event = TripEvent.EventAdded(tripId, newEvent),
                                tripRepository = tripRepository
                            )
                            actionLogger.logEventAdded(tripId, userId, newEvent)

                            call.respond(HttpStatusCode.Created, newEvent)
                        }
                    }

                    put("/{eventId}") {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val eventId = call.parameters["eventId"]
                                ?: return@withTripAccess call.respond(HttpStatusCode.BadRequest)
                            val request = call.receive<EventDto>()

                            // 1. Fetch the OLD state BEFORE updating
                            val oldEvent = tripRepository.getAllEvents(tripId).find { it.id == eventId }

                            val updated = tripRepository.updateEvent(tripId, request.copy(id = eventId))

                            if (oldEvent != null && updated != null) {
                                connectionManager.broadcastToTrip(
                                    tripId = tripId,
                                    event = TripEvent.EventUpdated(tripId, updated),
                                    tripRepository = tripRepository
                                )
                                actionLogger.logEventUpdated(tripId, userId, oldEvent, updated)

                                call.respond(HttpStatusCode.OK, updated)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                    }

                    delete("/{eventId}") {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val eventId = call.parameters["eventId"]
                                ?: return@withTripAccess call.respond(HttpStatusCode.BadRequest)

                            val eventToDelete = tripRepository.getAllEvents(tripId).find { it.id == eventId }
                                ?: return@withTripAccess call.respond(HttpStatusCode.NotFound)

                            tripRepository.deleteEvent(tripId, eventId)

                            connectionManager.broadcastToTrip(
                                tripId = tripId,
                                event = TripEvent.EventDeleted(tripId, eventId),
                                tripRepository = tripRepository
                            )
                            actionLogger.logEventDeleted(tripId, userId, eventToDelete)

                            call.respond(HttpStatusCode.OK)
                        }
                    }
                }
                route("/checklist") {
                    get {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            call.respond(tripRepository.getChecklist(tripId, userId))
                        }
                    }
                    post {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val req = call.receive<CreateChecklistItemRequest>()
                            val created = tripRepository.addChecklistItem(tripId, userId, req)

                            connectionManager.broadcastToTrip(
                                tripId, TripEvent.ChecklistUpdated(tripId, created), tripRepository
                            )
                            actionLogger.logChecklistAdded(tripId, userId, created)

                            call.respond(HttpStatusCode.Created, created)
                        }
                    }


                    put("/{itemId}/toggle") {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val itemId = call.parameters["itemId"]
                                ?: return@withTripAccess call.respond(HttpStatusCode.BadRequest)

                            val oldItem = tripRepository.getChecklistItemById(itemId)

                            val updated = tripRepository.toggleChecklistCompletion(tripId, itemId, userId)

                            if (oldItem != null && updated != null) {
                                connectionManager.broadcastToTrip(
                                    tripId, TripEvent.ChecklistUpdated(tripId, updated), tripRepository
                                )
                                actionLogger.logChecklistUpdated(tripId, userId, oldItem, updated)

                                call.respond(HttpStatusCode.OK, updated)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                    }

                    delete("/{itemId}") {
                        call.withTripAccess(tripRepository) { tripId, userId ->
                            val itemId = call.parameters["itemId"]
                                ?: return@withTripAccess call.respond(HttpStatusCode.BadRequest)

                            val itemToDelete = tripRepository.getChecklistItemById(itemId)
                                ?: return@withTripAccess call.respond(HttpStatusCode.NotFound)

                            if (tripRepository.deleteChecklistItem(tripId, itemId, userId)) {
                                connectionManager.broadcastToTrip(
                                    tripId, TripEvent.ChecklistDeleted(tripId, itemId), tripRepository
                                )

                                actionLogger.logChecklistDeleted(tripId, userId, itemToDelete)

                                call.respond(HttpStatusCode.OK)
                            } else {
                                call.respond(HttpStatusCode.NotFound)
                            }
                        }
                    }
                }
            }
        }

        webSocket("/ws/events") {
            val userId = call.request.headers["X-User-Id"]
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing X-User-Id"))
                return@webSocket
            }

            connectionManager.onConnect(userId, this)
            try {
                for (frame in incoming) {
                }
            } finally {
                connectionManager.onDisconnect(userId, this)
            }
        }
    }
}