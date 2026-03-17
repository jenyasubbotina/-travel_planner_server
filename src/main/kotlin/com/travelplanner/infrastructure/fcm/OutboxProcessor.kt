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

    private suspend fun processEvents() {
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

        val title: String
        val body: String
        val data = mutableMapOf(
            "eventType" to event.eventType,
            "aggregateType" to event.aggregateType,
            "aggregateId" to event.aggregateId.toString()
        )

        when (event.eventType) {
            "TRIP_CREATED" -> {
                title = "New Trip"
                body = "A new trip has been created: ${payload["tripTitle"]?.jsonPrimitive?.content ?: "Untitled"}"
            }

            "TRIP_UPDATED" -> {
                title = "Trip Updated"
                body = "Trip \"${payload["tripTitle"]?.jsonPrimitive?.content ?: ""}\" has been updated"
            }

            "PARTICIPANT_ADDED" -> {
                title = "New Participant"
                body = "${payload["participantName"]?.jsonPrimitive?.content ?: "Someone"} joined the trip"
            }

            "PARTICIPANT_REMOVED" -> {
                title = "Participant Left"
                body = "${payload["participantName"]?.jsonPrimitive?.content ?: "Someone"} left the trip"
            }

            "EXPENSE_CREATED" -> {
                title = "New Expense"
                body = "New expense added: ${payload["description"]?.jsonPrimitive?.content ?: ""}"
            }

            "EXPENSE_UPDATED" -> {
                title = "Expense Updated"
                body = "Expense updated: ${payload["description"]?.jsonPrimitive?.content ?: ""}"
            }

            "ITINERARY_UPDATED" -> {
                title = "Itinerary Updated"
                body = "The trip itinerary has been modified"
            }

            else -> {
                title = "Travel Planner"
                body = "You have a new update"
            }
        }

        val tripId = event.aggregateId
        val excludeUserId = payload["actorUserId"]?.jsonPrimitive?.content?.let {
            try {
                UUID.fromString(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        val participantUserIds = participantRepository.getUserIdsForTrip(tripId)

        fcmNotificationService.notifyTripParticipants(
            tripParticipantUserIds = participantUserIds,
            excludeUserId = excludeUserId,
            title = title,
            body = body,
            data = data
        )
    }
}
