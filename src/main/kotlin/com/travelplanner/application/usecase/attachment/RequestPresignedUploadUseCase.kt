package com.travelplanner.application.usecase.attachment

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.infrastructure.s3.S3StorageService
import java.util.UUID

class RequestPresignedUploadUseCase(
    private val participantRepository: ParticipantRepository,
    private val s3StorageService: S3StorageService
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val fileName: String,
        val contentType: String,
        val fileSize: Long
    )

    data class Output(val presignedUrl: String, val s3Key: String)

    companion object {
        private const val MAX_FILE_SIZE = 50L * 1024 * 1024 // 50 MB
        private val ALLOWED_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )
    }

    suspend fun execute(input: Input): Output {
        if (!participantRepository.isParticipant(input.tripId, input.userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        if (input.fileName.isBlank()) {
            throw DomainException.ValidationError("File name is required")
        }

        if (input.fileSize <= 0) {
            throw DomainException.ValidationError("File size must be positive")
        }

        if (input.fileSize > MAX_FILE_SIZE) {
            throw DomainException.ValidationError("File size exceeds maximum of 50MB")
        }

        if (input.contentType !in ALLOWED_MIME_TYPES) {
            throw DomainException.ValidationError("Unsupported file type: ${input.contentType}")
        }

        val fileId = UUID.randomUUID()
        val s3Key = "trips/${input.tripId}/attachments/$fileId/${input.fileName}"

        val presignedUrl = s3StorageService.generatePresignedUploadUrl(
            key = s3Key,
            contentType = input.contentType
        )

        return Output(presignedUrl = presignedUrl, s3Key = s3Key)
    }
}
