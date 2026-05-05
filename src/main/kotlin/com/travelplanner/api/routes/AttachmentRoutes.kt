package com.travelplanner.api.routes

import com.travelplanner.api.dto.request.CreateAttachmentRequest
import com.travelplanner.api.dto.request.PresignDownloadRequest
import com.travelplanner.api.dto.request.PresignUploadRequest
import com.travelplanner.api.dto.response.AttachmentResponse
import com.travelplanner.api.dto.response.PresignedDownloadResponse
import com.travelplanner.api.dto.response.PresignedUploadResponse
import com.travelplanner.api.middleware.currentUserId
import com.travelplanner.api.middleware.requireTripParticipant
import com.travelplanner.api.middleware.tripIdParam
import com.travelplanner.api.middleware.uuidParam
import com.travelplanner.application.usecase.attachment.CreateAttachmentUseCase
import com.travelplanner.application.usecase.attachment.DeleteAttachmentUseCase
import com.travelplanner.application.usecase.attachment.RequestPresignedDownloadUseCase
import com.travelplanner.application.usecase.attachment.RequestPresignedUploadUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Attachment
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.domain.repository.ParticipantRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.attachmentRoutes() {
    val requestPresignedUploadUseCase by inject<RequestPresignedUploadUseCase>()
    val requestPresignedDownloadUseCase by inject<RequestPresignedDownloadUseCase>()
    val createAttachmentUseCase by inject<CreateAttachmentUseCase>()
    val deleteAttachmentUseCase by inject<DeleteAttachmentUseCase>()
    val attachmentRepository by inject<AttachmentRepository>()
    val participantRepository by inject<ParticipantRepository>()

    authenticate("auth-jwt") {
        route("/api/v1/attachments") {
            post("/presign") {
                val userId = currentUserId()
                val req = call.receive<PresignUploadRequest>()
                val result = requestPresignedUploadUseCase.execute(
                    RequestPresignedUploadUseCase.Input(
                        tripId = UUID.fromString(req.tripId),
                        userId = userId,
                        fileName = req.fileName,
                        contentType = req.contentType,
                        fileSize = req.fileSize,
                        attachmentId = req.attachmentId?.let(UUID::fromString),
                    )
                )
                call.respond(
                    HttpStatusCode.OK,
                    PresignedUploadResponse(
                        uploadUrl = result.presignedUrl,
                        s3Key = result.s3Key,
                        attachmentId = result.attachmentId.toString(),
                    )
                )
            }

            post("/presign-download") {
                val userId = currentUserId()
                val req = call.receive<PresignDownloadRequest>()
                val result = requestPresignedDownloadUseCase.execute(
                    RequestPresignedDownloadUseCase.Input(
                        s3Key = req.s3Key,
                        userId = userId
                    )
                )
                call.respond(
                    HttpStatusCode.OK,
                    PresignedDownloadResponse(
                        url = result.url,
                        expiresInSeconds = result.expiresInSeconds
                    )
                )
            }

            delete("/{attachmentId}") {
                val userId = currentUserId()
                val attachmentId = uuidParam("attachmentId")
                deleteAttachmentUseCase.execute(
                    DeleteAttachmentUseCase.Input(
                        attachmentId = attachmentId,
                        userId = userId
                    )
                )
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/api/v1/trips/{tripId}/attachments") {
            get {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val scope = call.request.queryParameters["scope"] ?: "trip"
                if (scope !in setOf("trip", "all")) {
                    throw DomainException.ValidationError("Invalid scope; must be 'trip' or 'all'")
                }
                requireTripParticipant(participantRepository, tripId, userId)
                val attachments = attachmentRepository.findByTrip(
                    tripId = tripId,
                    includeExpenseAttachments = scope == "all"
                )
                call.respond(HttpStatusCode.OK, attachments.map { it.toResponse() })
            }

            post {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val req = call.receive<CreateAttachmentRequest>()
                val attachment = createAttachmentUseCase.execute(
                    CreateAttachmentUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        expenseId = null,
                        fileName = req.fileName,
                        fileSize = req.fileSize,
                        mimeType = req.mimeType,
                        s3Key = req.s3Key,
                        id = req.id?.let(UUID::fromString),
                    )
                )
                call.respond(HttpStatusCode.Created, attachment.toResponse())
            }
        }

        route("/api/v1/trips/{tripId}/expenses/{expenseId}/attachments") {
            post {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val expenseId = uuidParam("expenseId")
                val req = call.receive<CreateAttachmentRequest>()
                val attachment = createAttachmentUseCase.execute(
                    CreateAttachmentUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        expenseId = expenseId,
                        fileName = req.fileName,
                        fileSize = req.fileSize,
                        mimeType = req.mimeType,
                        s3Key = req.s3Key,
                        id = req.id?.let(UUID::fromString),
                    )
                )
                call.respond(HttpStatusCode.Created, attachment.toResponse())
            }
        }

        route("/api/v1/trips/{tripId}/itinerary/{pointId}/attachments") {
            post {
                val tripId = tripIdParam()
                val userId = currentUserId()
                val pointId = uuidParam("pointId")
                val req = call.receive<CreateAttachmentRequest>()
                val attachment = createAttachmentUseCase.execute(
                    CreateAttachmentUseCase.Input(
                        tripId = tripId,
                        userId = userId,
                        pointId = pointId,
                        fileName = req.fileName,
                        fileSize = req.fileSize,
                        mimeType = req.mimeType,
                        s3Key = req.s3Key,
                        id = req.id?.let(UUID::fromString),
                    )
                )
                call.respond(HttpStatusCode.Created, attachment.toResponse())
            }
        }
    }
}

private fun Attachment.toResponse() = AttachmentResponse(
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
