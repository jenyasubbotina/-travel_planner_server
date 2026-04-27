package com.travelplanner.api.routes

import com.travelplanner.api.dto.response.AttachmentResponse
import com.travelplanner.api.dto.response.ChecklistItemResponse
import com.travelplanner.api.dto.response.DeltaResponse
import com.travelplanner.api.dto.response.ExpensePendingUpdateResponse
import com.travelplanner.api.dto.response.ExpenseResponse
import com.travelplanner.api.dto.response.ExpenseSplitResponse
import com.travelplanner.api.dto.response.JoinRequestUserResponse
import com.travelplanner.api.dto.response.ParticipantResponse
import com.travelplanner.api.dto.response.PointCommentResponse
import com.travelplanner.api.dto.response.PointLinkResponse
import com.travelplanner.api.dto.response.SnapshotResponse
import com.travelplanner.api.dto.response.toHistoryEntryResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.application.usecase.sync.GetDeltaSyncUseCase
import com.travelplanner.application.usecase.sync.GetSnapshotUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.model.ChecklistItem
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpensePendingUpdate
import com.travelplanner.domain.model.ExpenseSplit
import com.travelplanner.domain.model.ItineraryPointCommentWithAuthor
import com.travelplanner.domain.model.ItineraryPointLink
import com.travelplanner.domain.model.JoinRequestWithUser
import com.travelplanner.domain.model.SyncDelta
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripSnapshot
import com.travelplanner.domain.repository.ExpenseRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.time.Instant

fun Route.syncRoutes() {
    val getSnapshotUseCase by inject<GetSnapshotUseCase>()
    val getDeltaSyncUseCase by inject<GetDeltaSyncUseCase>()
    val expenseRepository by inject<ExpenseRepository>()

    authenticate("auth-jwt") {
        route("/api/v1/trips/{tripId}") {
            get("/snapshot") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val snapshot = getSnapshotUseCase.execute(tripId, userId)
                call.respond(HttpStatusCode.OK, snapshot.toResponse(expenseRepository))
            }

            get("/sync") {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val cursorStr = call.request.queryParameters["cursor"]
                    ?: throw DomainException.ValidationError("Missing cursor query parameter")
                val cursor = Instant.parse(cursorStr)
                val delta = getDeltaSyncUseCase.execute(
                    GetDeltaSyncUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        cursor = cursor
                    )
                )
                call.respond(HttpStatusCode.OK, delta.toResponse(expenseRepository))
            }
        }
    }
}

private suspend fun TripSnapshot.toResponse(expenseRepository: ExpenseRepository): SnapshotResponse {
    val pendingByExpense = expenseRepository.findPendingUpdatesByTrip(trip.id).associateBy { it.expenseId }
    val expenseResponses = expenses.map { expense ->
        val splits = expenseRepository.findSplitsByExpense(expense.id)
        val pending = pendingByExpense[expense.id]
        val baseSnapshot = pending?.let {
            expenseRepository.findHistoryAt(expense.id, it.baseVersion)?.snapshot
        }
        expense.toSyncResponse(splits, pending, baseSnapshot)
    }
    return SnapshotResponse(
        trip = trip.toResponse(),
        participants = participants.map { it.toSyncResponse() },
        itineraryPoints = itineraryPoints.map { it.toResponse() },
        expenses = expenseResponses,
        attachments = attachments.map { it.toSyncResponse() },
        checklistItems = checklistItems.map { it.toSyncResponse() },
        pendingJoinRequests = pendingJoinRequests.map { it.toSyncResponse() },
        historyEntries = historyEntries.map { it.toHistoryEntryResponse() },
        pointLinks = pointLinks.map { it.toSyncResponse() },
        pointComments = pointComments.map { it.toSyncResponse() },
        cursor = cursor.timestamp.toString(),
    )
}

private suspend fun SyncDelta.toResponse(expenseRepository: ExpenseRepository): DeltaResponse {
    val tripIdForPending = trips.firstOrNull()?.id ?: expenses.firstOrNull()?.tripId
    val pendingByExpense = if (tripIdForPending != null) {
        expenseRepository.findPendingUpdatesByTrip(tripIdForPending).associateBy { it.expenseId }
    } else emptyMap()
    val expenseResponses = expenses.map { expense ->
        val splits = expenseRepository.findSplitsByExpense(expense.id)
        val pending = pendingByExpense[expense.id]
        val baseSnapshot = pending?.let {
            expenseRepository.findHistoryAt(expense.id, it.baseVersion)?.snapshot
        }
        expense.toSyncResponse(splits, pending, baseSnapshot)
    }
    return DeltaResponse(
        trips = trips.map { it.toResponse() },
        participants = participants.map { it.toSyncResponse() },
        itineraryPoints = itineraryPoints.map { it.toResponse() },
        expenses = expenseResponses,
        attachments = attachments.map { it.toSyncResponse() },
        checklistItems = checklistItems.map { it.toSyncResponse() },
        pendingJoinRequests = pendingJoinRequests.map { it.toSyncResponse() },
        historyEntries = historyEntries.map { it.toHistoryEntryResponse() },
        pointLinks = pointLinks.map { it.toSyncResponse() },
        pointComments = pointComments.map { it.toSyncResponse() },
        cursor = cursor.timestamp.toString(),
    )
}

private fun TripParticipant.toSyncResponse() = ParticipantResponse(
    tripId = tripId.toString(),
    userId = userId.toString(),
    role = role.name,
    joinedAt = joinedAt.toString()
)

private fun Expense.toSyncResponse(
    splits: List<ExpenseSplit>,
    pending: ExpensePendingUpdate?,
    baseSnapshot: String? = null,
) = ExpenseResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    payerUserId = payerUserId.toString(),
    title = title,
    description = description,
    amount = amount.toPlainString(),
    currency = currency,
    category = category,
    expenseDate = expenseDate.toString(),
    splitType = splitType.name,
    splits = splits.map { it.toSyncResponse() },
    createdBy = createdBy.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    version = version,
    deletedAt = deletedAt?.toString(),
    pendingUpdate = pending?.let {
        ExpensePendingUpdateResponse(
            expenseId = it.expenseId.toString(),
            proposedByUserId = it.proposedByUserId.toString(),
            proposedAt = it.proposedAt.toString(),
            baseVersion = it.baseVersion,
            payload = it.payload,
            baseSnapshot = baseSnapshot,
        )
    },
)

private fun ExpenseSplit.toSyncResponse() = ExpenseSplitResponse(
    id = id.toString(),
    participantUserId = participantUserId.toString(),
    shareType = shareType.name,
    value = value.toPlainString(),
    amountInExpenseCurrency = amountInExpenseCurrency.toPlainString()
)

private fun Attachment.toSyncResponse() = AttachmentResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    expenseId = expenseId?.toString(),
    pointId = pointId?.toString(),
    uploadedBy = uploadedBy.toString(),
    fileName = fileName,
    fileSize = fileSize,
    mimeType = mimeType,
    s3Key = s3Key,
    createdAt = createdAt.toString(),
    deletedAt = deletedAt?.toString(),
)

private fun ChecklistItem.toSyncResponse() = ChecklistItemResponse(
    id = id.toString(),
    tripId = tripId.toString(),
    title = title,
    isGroup = isGroup,
    ownerUserId = ownerUserId.toString(),
    completedBy = completedBy.map { it.toString() },
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private fun JoinRequestWithUser.toSyncResponse() = JoinRequestUserResponse(
    userId = request.requesterUserId.toString(),
    displayName = displayName,
    email = email,
)

private fun ItineraryPointLink.toSyncResponse() = PointLinkResponse(
    id = id.toString(),
    pointId = pointId.toString(),
    title = title,
    url = url,
    sortOrder = sortOrder,
    createdAt = createdAt.toString(),
)

private fun ItineraryPointCommentWithAuthor.toSyncResponse() = PointCommentResponse(
    id = comment.id.toString(),
    pointId = comment.pointId.toString(),
    authorUserId = comment.authorUserId.toString(),
    authorDisplayName = authorDisplayName,
    text = comment.text,
    createdAt = comment.createdAt.toString(),
)
