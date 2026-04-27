package com.travelplanner.application.usecase.itinerary

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.DomainEvent
import com.travelplanner.domain.model.ItineraryPoint
import com.travelplanner.domain.model.ItineraryPointStatus
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TransactionRunner
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class UpdateItineraryPointUseCase(
    private val participantRepository: ParticipantRepository,
    private val itineraryRepository: ItineraryRepository,
    private val domainEventRepository: DomainEventRepository,
    private val transactionRunner: TransactionRunner
) {

    data class Input(
        val pointId: UUID,
        val userId: UUID,
        val title: String? = null,
        val description: String? = null,
        val subtitle: String? = null,
        val type: String? = null,
        val date: LocalDate? = null,
        val dayIndex: Int? = null,
        val startTime: LocalTime? = null,
        val endTime: LocalTime? = null,
        val duration: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val address: String? = null,
        val cost: Double? = null,
        val actualCost: Double? = null,
        val status: ItineraryPointStatus? = null,
        val participantIds: List<UUID>? = null,
        val expectedVersion: Long
    )

    suspend fun execute(input: Input): ItineraryPoint = transactionRunner.runInTransaction {
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

        if (input.participantIds != null && input.participantIds.isNotEmpty()) {
            val tripParticipantIds = participantRepository.getUserIdsForTrip(point.tripId).toSet()
            val invalid = input.participantIds.filterNot { it in tripParticipantIds }
            if (invalid.isNotEmpty()) {
                throw DomainException.ValidationError(
                    "participantIds contain users who are not in this trip: ${invalid.joinToString()}"
                )
            }
        }

        val updated = point.copy(
            title = input.title?.trim() ?: point.title,
            description = if (input.description != null) input.description.trim() else point.description,
            subtitle = if (input.subtitle != null) input.subtitle.trim() else point.subtitle,
            type = if (input.type != null) input.type.trim() else point.type,
            date = input.date ?: point.date,
            dayIndex = input.dayIndex ?: point.dayIndex,
            startTime = input.startTime ?: point.startTime,
            endTime = input.endTime ?: point.endTime,
            duration = if (input.duration != null) input.duration.trim() else point.duration,
            latitude = input.latitude ?: point.latitude,
            longitude = input.longitude ?: point.longitude,
            address = if (input.address != null) input.address.trim() else point.address,
            cost = input.cost ?: point.cost,
            actualCost = input.actualCost ?: point.actualCost,
            status = input.status ?: point.status,
            participantIds = input.participantIds ?: point.participantIds
        )

        val saved = itineraryRepository.update(updated)

        domainEventRepository.save(
            DomainEvent(
                id = UUID.randomUUID(),
                eventType = "ITINERARY_UPDATED",
                aggregateType = "TRIP",
                aggregateId = saved.tripId,
                payload = buildJsonObject {
                    put("actorUserId", input.userId.toString())
                    put("pointId", saved.id.toString())
                    put("change", "UPDATED")
                }.toString(),
                createdAt = Instant.now()
            )
        )

        saved
    }
}
