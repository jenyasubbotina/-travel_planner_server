package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class CreateItineraryPointUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val title: String,
        val description: String? = null,
        val type: String? = null,
        val date: LocalDate? = null,
        val startTime: LocalTime? = null,
        val endTime: LocalTime? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val address: String? = null
    )

    suspend fun execute(input: Input): ItineraryPoint {
        if (input.title.isBlank()) {
            throw DomainException.ValidationError("Itinerary point title cannot be blank")
        }

        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(input.tripId)
        }

        if (trip.status != TripStatus.ACTIVE) {
            throw DomainException.TripNotActive(input.tripId)
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        // Calculate next sort order
        val existingPoints = itineraryRepository.findByTrip(input.tripId)
        val maxSortOrder = existingPoints
            .filter { it.deletedAt == null }
            .maxOfOrNull { it.sortOrder } ?: 0
        val nextSortOrder = maxSortOrder + 1

        val now = Instant.now()
        val point = ItineraryPoint(
            id = UUID.randomUUID(),
            tripId = input.tripId,
            title = input.title.trim(),
            description = input.description?.trim(),
            type = input.type?.trim(),
            date = input.date,
            startTime = input.startTime,
            endTime = input.endTime,
            latitude = input.latitude,
            longitude = input.longitude,
            address = input.address?.trim(),
            sortOrder = nextSortOrder,
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        return itineraryRepository.create(point)
    }
}
