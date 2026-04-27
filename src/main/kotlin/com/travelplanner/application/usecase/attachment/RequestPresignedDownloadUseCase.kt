package com.travelplanner.application.usecase.attachment

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.infrastructure.s3.S3StorageService
import java.util.UUID

class RequestPresignedDownloadUseCase(
    private val participantRepository: ParticipantRepository,
    private val s3StorageService: S3StorageService
) {

    data class Input(val s3Key: String, val userId: UUID)
    data class Output(val url: String, val expiresInSeconds: Long)

    suspend fun execute(input: Input): Output {
        if (input.s3Key.isBlank()) {
            throw DomainException.ValidationError("s3Key is required")
        }

        val tripId = extractTripId(input.s3Key)
            ?: throw DomainException.ValidationError("s3Key does not match expected layout 'trips/{tripId}/...'")

        if (!participantRepository.isParticipant(tripId, input.userId)) {
            throw DomainException.AccessDenied("User is not a participant of this trip")
        }

        val expirationMinutes = 15L
        val url = s3StorageService.generatePresignedDownloadUrl(
            key = input.s3Key,
            expirationMinutes = expirationMinutes
        )
        return Output(url = url, expiresInSeconds = expirationMinutes * 60)
    }

    private fun extractTripId(s3Key: String): UUID? {
        val parts = s3Key.split('/')
        if (parts.size < 2 || parts[0] != "trips") return null
        return runCatching { UUID.fromString(parts[1]) }.getOrNull()
    }
}
