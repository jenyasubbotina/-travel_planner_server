package com.travelplanner.domain.event

import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.model.ChecklistItem
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.Trip
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

object HistoryPayload {

    const val SCHEMA_VERSION = 2

    object EntityType {
        const val TRIP = "TRIP"
        const val EXPENSE = "EXPENSE"
        const val EVENT = "EVENT"
        const val CHECKLIST_ITEM = "CHECKLIST_ITEM"
        const val ATTACHMENT = "ATTACHMENT"
        const val PARTICIPANT = "PARTICIPANT"
        const val JOIN_REQUEST = "JOIN_REQUEST"
        const val LINK = "LINK"
        const val COMMENT = "COMMENT"
    }

    object ActionType {
        const val CREATE = "CREATE"
        const val UPDATE = "UPDATE"
        const val DELETE = "DELETE"
        const val ARCHIVE = "ARCHIVE"
        const val COMPLETE = "COMPLETE"
        const val UNCOMPLETE = "UNCOMPLETE"
        const val REORDER_ITINERARY = "REORDER_ITINERARY"
        const val REGENERATE_JOIN_CODE = "REGENERATE_JOIN_CODE"
        const val INVITE = "INVITE"
        const val JOIN = "JOIN"
        const val CHANGE_ROLE = "CHANGE_ROLE"
        const val REQUEST_JOIN = "REQUEST_JOIN"
        const val APPROVE_JOIN = "APPROVE_JOIN"
        const val DENY_JOIN = "DENY_JOIN"
        const val STORE_PENDING_UPDATE = "STORE_PENDING_UPDATE"
        const val REJECT_PENDING_UPDATE = "REJECT_PENDING_UPDATE"
        const val MERGE_PENDING_UPDATE = "MERGE_PENDING_UPDATE"
        const val REVERT_PENDING_UPDATE = "REVERT_PENDING_UPDATE"
    }

    fun build(
        actorUserId: UUID,
        entityType: String,
        entityId: UUID,
        actionType: String,
        entity: JsonObject? = null,
        old: JsonObject? = null,
        new: JsonObject? = null,
        context: JsonObject? = null,
    ): String = buildJsonObject {
        put("actorUserId", actorUserId.toString())
        put("entityType", entityType)
        put("entityId", entityId.toString())
        put("actionType", actionType)
        put("schemaVersion", SCHEMA_VERSION)
        if (entity != null) put("entity", entity)
        if (old != null) put("old", old)
        if (new != null) put("new", new)
        if (context != null) put("context", context)
    }.toString()

    fun diff(old: JsonObject, new: JsonObject): Pair<JsonObject, JsonObject>? {
        val keys = old.keys + new.keys
        val differing = keys.filter { old[it] != new[it] }
        if (differing.isEmpty()) return null
        val oldFiltered = buildJsonObject {
            differing.forEach { k -> old[k]?.let { put(k, it) } }
        }
        val newFiltered = buildJsonObject {
            differing.forEach { k -> new[k]?.let { put(k, it) } }
        }
        return oldFiltered to newFiltered
    }

    fun tripSnapshot(trip: Trip): JsonObject = buildJsonObject {
        put("title", trip.title)
        trip.description?.let { put("description", it) }
        trip.startDate?.let { put("startDate", it.toString()) }
        trip.endDate?.let { put("endDate", it.toString()) }
        put("baseCurrency", trip.baseCurrency)
        put("totalBudget", trip.totalBudget.toPlainString())
        put("destination", trip.destination)
        trip.imageUrl?.let { put("imageUrl", it) }
        put("status", trip.status.name)
    }

    fun expenseSnapshot(expense: Expense, splits: List<ExpenseSplit>? = null): JsonObject = buildJsonObject {
        put("title", expense.title)
        expense.description?.let { put("description", it) }
        put("amount", expense.amount.toPlainString())
        put("currency", expense.currency)
        put("category", expense.category)
        put("expenseDate", expense.expenseDate.toString())
        put("splitType", expense.splitType.name)
        put("payerUserId", expense.payerUserId.toString())
        if (splits != null) {
            put("splits", buildJsonArray {
                splits.forEach { s ->
                    add(buildJsonObject {
                        put("participantUserId", s.participantUserId.toString())
                        put("shareType", s.shareType.name)
                        put("value", s.value.toPlainString())
                        put("amountInExpenseCurrency", s.amountInExpenseCurrency.toPlainString())
                    })
                }
            })
        }
    }

    fun eventSnapshot(point: ItineraryPoint): JsonObject = buildJsonObject {
        put("title", point.title)
        point.description?.let { put("description", it) }
        point.subtitle?.let { put("subtitle", it) }
        point.type?.let { put("type", it) }
        point.category?.let { put("category", it) }
        point.date?.let { put("date", it.toString()) }
        put("dayIndex", point.dayIndex)
        point.startTime?.let { put("startTime", it.toString()) }
        point.endTime?.let { put("endTime", it.toString()) }
        point.duration?.let { put("duration", it) }
        point.latitude?.let { put("latitude", it) }
        point.longitude?.let { put("longitude", it) }
        point.address?.let { put("address", it) }
        point.cost?.let { put("cost", it) }
        point.actualCost?.let { put("actualCost", it) }
        put("status", point.status.name)
        if (point.participantIds.isNotEmpty()) {
            put("participantIds", buildJsonArray {
                point.participantIds.forEach { add(it.toString()) }
            })
        }
    }

    fun checklistItemSnapshot(item: ChecklistItem): JsonObject = buildJsonObject {
        put("title", item.title)
        put("isGroup", item.isGroup)
    }

    fun attachmentSnapshot(attachment: Attachment): JsonObject = buildJsonObject {
        put("fileName", attachment.fileName)
        put("mimeType", attachment.mimeType)
        put("fileSize", attachment.fileSize)
        attachment.expenseId?.let { put("expenseId", it.toString()) }
        attachment.pointId?.let { put("pointId", it.toString()) }
    }
}
