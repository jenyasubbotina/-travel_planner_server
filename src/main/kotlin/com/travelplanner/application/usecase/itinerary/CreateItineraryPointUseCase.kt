package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.ItineraryPointStatus
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class CreateItineraryPointUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val title: String,
        val description: String? = null,
        val subtitle: String? = null,
        val type: String? = null,
        val date: LocalDate? = null,
        val dayIndex: Int = 0,
        val startTime: LocalTime? = null,
        val endTime: LocalTime? = null,
        val duration: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val address: String? = null,
        val cost: Double? = null,
        val actualCost: Double? = null,
        val status: ItineraryPointStatus = ItineraryPointStatus.NONE,
        val participantIds: List<UUID> = emptyList()
    )

    suspend fun execute(input: Input): ItineraryPoint = transactionRunner.runInTransaction {
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

        if (input.participantIds.isNotEmpty()) {
            val tripParticipantIds = participantRepository.getUserIdsForTrip(input.tripId).toSet()
            val invalid = input.participantIds.filterNot { it in tripParticipantIds }
            if (invalid.isNotEmpty()) {
                throw DomainException.ValidationError(
                    "participantIds contain users who are not in this trip: ${invalid.joinToString()}"
                )
            }
        }

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
            subtitle = input.subtitle?.trim(),
            type = input.type?.trim(),
            date = input.date,
            dayIndex = input.dayIndex,
            startTime = input.startTime,
            endTime = input.endTime,
            duration = input.duration?.trim(),
            latitude = input.latitude,
            longitude = input.longitude,
            address = input.address?.trim(),
            cost = input.cost,
            actualCost = input.actualCost,
            status = input.status,
            participantIds = input.participantIds,
            sortOrder = nextSortOrder,
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        val created = itineraryRepository.create(point)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ITINERARY_UPDATED",
                aggregateType = "TRIP",
                aggregateId = input.tripId,
                payload = buildJsonObject {
                    put("actorUserId", input.userId.toString())
                    put("pointId", created.id.toString())
                    put("change", "CREATED")
                }.toString(),
                createdAt = now
            )
        )

        created
    }
}
