package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class UpdateItineraryPointUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository
) {

    data class Input(
        val pointId: UUID,
        val userId: UUID,
        val title: String? = null,
        val description: String? = null,
        val type: String? = null,
        val date: LocalDate? = null,
        val startTime: LocalTime? = null,
        val endTime: LocalTime? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val address: String? = null,
        val expectedVersion: Long
    )

    suspend fun execute(input: Input): ItineraryPoint {
        val point = itineraryRepository.findById(input.pointId)
            ?: throw DomainException.ItineraryPointNotFound(input.pointId)

        if (point.deletedAt != null) {
            throw DomainException.ItineraryPointNotFound(input.pointId)
        }

        val participant = participantRepository.findByTripAndUser(point.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        if (point.version != input.expectedVersion) {
            throw DomainException.VersionConflict("ItineraryPoint", input.pointId)
        }

        if (input.title != null && input.title.isBlank()) {
            throw DomainException.ValidationError("Itinerary point title cannot be blank")
        }

        val updated = point.copy(
            title = input.title?.trim() ?: point.title,
            description = if (input.description != null) input.description.trim() else point.description,
            type = if (input.type != null) input.type.trim() else point.type,
            date = input.date ?: point.date,
            startTime = input.startTime ?: point.startTime,
            endTime = input.endTime ?: point.endTime,
            latitude = input.latitude ?: point.latitude,
            longitude = input.longitude ?: point.longitude,
            address = if (input.address != null) input.address.trim() else point.address,
            updatedAt = Instant.now(),
            version = point.version + 1
        )

        return itineraryRepository.update(updated)
    }
}
