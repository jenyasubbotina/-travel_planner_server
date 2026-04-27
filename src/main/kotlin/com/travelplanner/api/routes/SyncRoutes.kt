package com.travelplanner.api.routes

import com.travelplanner.api.dto.response.AttachmentResponse
import com.travelplanner.api.dto.response.DeltaResponse
import com.travelplanner.api.dto.response.ExpenseResponse
import com.travelplanner.api.dto.response.ExpenseSplitResponse
import com.travelplanner.api.dto.response.ParticipantResponse
import com.travelplanner.api.dto.response.SnapshotResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.application.usecase.sync.GetDeltaSyncUseCase
import com.travelplanner.application.usecase.sync.GetSnapshotUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.model.Expense
import com.travelplanner.domain.model.ExpenseSplit
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
    val expenseResponses = expenses.map { expense ->
        val splits = expenseRepository.findSplitsByExpense(expense.id)
        expense.toSyncResponse(splits)
    }
    return SnapshotResponse(
        trip = trip.toResponse(),
        participants = participants.map { it.toSyncResponse() },
        itineraryPoints = itineraryPoints.map { it.toResponse() },
        expenses = expenseResponses,
        attachments = attachments.map { it.toSyncResponse() },
        cursor = cursor.timestamp.toString()
    )
}

private suspend fun SyncDelta.toResponse(expenseRepository: ExpenseRepository): DeltaResponse {
    val expenseResponses = expenses.map { expense ->
        val splits = expenseRepository.findSplitsByExpense(expense.id)
        expense.toSyncResponse(splits)
    }
    return DeltaResponse(
        trips = trips.map { it.toResponse() },
        participants = participants.map { it.toSyncResponse() },
        itineraryPoints = itineraryPoints.map { it.toResponse() },
        expenses = expenseResponses,
        attachments = attachments.map { it.toSyncResponse() },
        cursor = cursor.timestamp.toString()
    )
}

private fun TripParticipant.toSyncResponse() = ParticipantResponse(
    tripId = tripId.toString(),
    userId = userId.toString(),
    role = role.name,
    joinedAt = joinedAt.toString()
)

private fun Expense.toSyncResponse(splits: List<ExpenseSplit>) = ExpenseResponse(
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
    deletedAt = deletedAt?.toString()
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
    uploadedBy = uploadedBy.toString(),
    fileName = fileName,
    fileSize = fileSize,
    mimeType = mimeType,
    s3Key = s3Key,
    createdAt = createdAt.toString(),
    deletedAt = deletedAt?.toString(),
)
