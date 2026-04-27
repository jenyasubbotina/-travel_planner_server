package com.travelplanner.infrastructure.fcm

import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ParticipantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.util.UUID

class OutboxProcessor(
    private val domainEventRepository: DomainEventRepository,
    private val fcmNotificationService: FcmNotificationService,
    private val participantRepository: ParticipantRepository
) {

    private val logger = LoggerFactory.getLogger(OutboxProcessor::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val POLL_INTERVAL_MS = 5000L
        private const val MAX_RETRIES = 5
    }

    fun start(scope: CoroutineScope) {
        scope.launch {
            logger.info("OutboxProcessor started — polling every {}ms", POLL_INTERVAL_MS)
            while (isActive) {
                try {
                    processEvents()
                } catch (e: Exception) {
                    logger.error("Outbox processing error", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    internal suspend fun processEvents() {
        val events = domainEventRepository.findUnprocessed()
        for (event in events) {
            if (event.retryCount >= MAX_RETRIES) {
                logger.warn(
                    "Event {} exceeded max retries ({}), skipping",
                    event.id,
                    MAX_RETRIES
                )
                domainEventRepository.markProcessed(event.id)
                continue
            }

            try {
                dispatchNotification(event)
                domainEventRepository.markProcessed(event.id)
                logger.debug("Processed event: id={}, type={}", event.id, event.eventType)
            } catch (e: Exception) {
                logger.error(
                    "Failed to process event: id={}, type={}, retry={}",
                    event.id,
                    event.eventType,
                    event.retryCount,
                    e
                )
                domainEventRepository.incrementRetry(event.id)
            }
        }
    }

    private suspend fun dispatchNotification(event: DomainEvent) {
        val payload = json.decodeFromString<JsonObject>(event.payload)

        val data = mutableMapOf(
            "eventType" to event.eventType,
            "aggregateType" to event.aggregateType,
            "aggregateId" to event.aggregateId.toString()
        )

        val excludeUserId = payload["actorUserId"]?.jsonPrimitive?.content?.toUuidOrNull()

        when (event.eventType) {
            "INVITATION_CREATED" -> {
                val title = "Trip Invitation"
                val body = "You've been invited to a trip"
                val context = payload["context"]?.jsonObject

                payload["entityId"]?.jsonPrimitive?.content?.let { data["invitationId"] = it }
                data["tripId"] = event.aggregateId.toString()
                context?.get("tripTitle")?.jsonPrimitive?.content?.let { data["tripTitle"] = it }

                val inviteeUserId = context?.get("inviteeUserId")?.jsonPrimitive?.content?.toUuidOrNull()
                if (inviteeUserId != null) {
                    fcmNotificationService.notifyUser(
                        userId = inviteeUserId,
                        title = title,
                        body = body,
                        data = data
                    )
                } else {
                    logger.info(
                        "Skipping push for INVITATION_CREATED id={} — invitee email has no registered user",
                        event.id
                    )
                }
                return
            }
        }

        val title: String
        val body: String

        // HistoryPayload.build nests fields inside "entity" / "context" / "new" / "old".
        // Top-level fields are limited to actorUserId, entityType, entityId, actionType, schemaVersion.
        val entity = payload["entity"]?.jsonObject
        val context = payload["context"]?.jsonObject
        val new = payload["new"]?.jsonObject
        val old = payload["old"]?.jsonObject

        when (event.eventType) {
            "TRIP_CREATED" -> {
                title = "New Trip"
                val tripTitle = entity?.get("title")?.jsonPrimitive?.content ?: "Untitled"
                body = "A new trip has been created: $tripTitle"
            }

            "TRIP_UPDATED" -> {
                title = "Trip Updated"
                // diff only includes title when it actually changed; fall back to old, then a generic message.
                val tripTitle = new?.get("title")?.jsonPrimitive?.content
                    ?: old?.get("title")?.jsonPrimitive?.content
                body = if (!tripTitle.isNullOrBlank()) {
                    "Trip \"$tripTitle\" has been updated"
                } else {
                    "Trip has been updated"
                }
            }

            "PARTICIPANT_ADDED" -> {
                title = "New Participant"
                val name = context?.get("participantName")?.jsonPrimitive?.content ?: "Someone"
                body = "$name joined the trip"
            }

            "PARTICIPANT_REMOVED" -> {
                title = "Participant Left"
                val name = context?.get("participantName")?.jsonPrimitive?.content ?: "Someone"
                body = "$name left the trip"
            }

            "PARTICIPANT_UPDATED" -> {
                title = "Role Updated"
                val name = context?.get("participantName")?.jsonPrimitive?.content ?: "Someone"
                val role = context?.get("newRole")?.jsonPrimitive?.content ?: "updated"
                body = "$name is now $role"
            }

            "EXPENSE_CREATED" -> {
                title = "New Expense"
                val expenseTitle = entity?.get("title")?.jsonPrimitive?.content ?: ""
                body = "New expense added: $expenseTitle"
            }

            "EXPENSE_UPDATED" -> {
                title = "Expense Updated"
                val expenseTitle = new?.get("title")?.jsonPrimitive?.content
                    ?: old?.get("title")?.jsonPrimitive?.content
                    ?: ""
                body = "Expense updated: $expenseTitle"
            }

            "ITINERARY_POINT_CREATED" -> {
                title = "Itinerary Updated"
                val pointTitle = entity?.get("title")?.jsonPrimitive?.content ?: "a new stop"
                body = "Added: $pointTitle"
            }

            "ITINERARY_POINT_UPDATED" -> {
                title = "Itinerary Updated"
                val pointTitle = new?.get("title")?.jsonPrimitive?.content
                    ?: old?.get("title")?.jsonPrimitive?.content
                body = if (pointTitle != null) {
                    "Updated: $pointTitle"
                } else {
                    "An itinerary stop was updated"
                }
            }

            "ITINERARY_POINT_DELETED" -> {
                title = "Itinerary Updated"
                val pointTitle = entity?.get("title")?.jsonPrimitive?.content ?: "a stop"
                body = "Removed: $pointTitle"
            }

            "ITINERARY_REORDERED" -> {
                title = "Itinerary Updated"
                body = "The trip itinerary has been reordered"
            }

            "ATTACHMENT_CREATED" -> {
                title = "New Attachment"
                val name = entity?.get("fileName")?.jsonPrimitive?.content ?: "a file"
                body = "New file uploaded: $name"
                payload["entityId"]?.jsonPrimitive?.content?.let { data["attachmentId"] = it }
            }

            "ATTACHMENT_DELETED" -> {
                title = "Attachment Removed"
                val name = entity?.get("fileName")?.jsonPrimitive?.content ?: "a file"
                body = "File removed: $name"
                payload["entityId"]?.jsonPrimitive?.content?.let { data["attachmentId"] = it }
            }

            else -> {
                title = "Travel Planner"
                body = "You have a new update"
            }
        }

        val tripId = event.aggregateId
        val participantUserIds = participantRepository.getUserIdsForTrip(tripId)

        fcmNotificationService.notifyTripParticipants(
            tripParticipantUserIds = participantUserIds,
            excludeUserId = excludeUserId,
            title = title,
            body = body,
            data = data
        )
    }

    private fun String.toUuidOrNull(): UUID? = try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        null
    }
}
