package com.travelplanner.api.dto.response

import com.travelplanner.domain.model.DomainEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private val payloadJson = Json { ignoreUnknownKeys = true }

internal fun DomainEvent.toHistoryEntryResponse(): HistoryEntryResponse {
    val parsed = runCatching { payloadJson.decodeFromString<JsonObject>(payload) }.getOrNull()
    val actorUserId = parsed?.get("actorUserId")?.jsonPrimitive?.content ?: ""
    val actionType = parsed?.get("actionType")?.jsonPrimitive?.content
        ?: parsed?.get("change")?.jsonPrimitive?.content
        ?: eventType
    val entityType = parsed?.get("entityType")?.jsonPrimitive?.content ?: aggregateType
    val entityId = parsed?.get("entityId")?.jsonPrimitive?.content ?: aggregateId.toString()
    return HistoryEntryResponse(
        id = id.toString(),
        tripId = aggregateId.toString(),
        userId = actorUserId,
        actionType = actionType,
        entityType = entityType,
        entityId = entityId,
        details = payload,
        timestamp = createdAt.toEpochMilli(),
    )
}
