package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.validation.TripValidator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class UpdateTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository
) {

    data class Input(
        val tripId: UUID,
        val userId: UUID,
        val title: String? = null,
        val description: String? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val baseCurrency: String? = null,
        val status: TripStatus? = null,
        val expectedVersion: Long
    )

    suspend fun execute(input: Input): Trip {
        val trip = tripRepository.findById(input.tripId)
            ?: throw DomainException.TripNotFound(input.tripId)

        if (trip.deletedAt != null) {
            throw DomainException.TripNotFound(input.tripId)
        }

        val participant = participantRepository.findByTripAndUser(input.tripId, input.userId)
            ?: throw DomainException.AccessDenied("User is not a participant of this trip")

        if (!participant.role.canEdit()) {
            throw DomainException.InsufficientRole("EDITOR")
        }

        if (trip.version != input.expectedVersion) {
            throw DomainException.VersionConflict("Trip", input.tripId)
        }

        val newTitle = input.title ?: trip.title
        val newStartDate = input.startDate ?: trip.startDate
        val newEndDate = input.endDate ?: trip.endDate
        val newCurrency = input.baseCurrency ?: trip.baseCurrency

        if (input.title != null) TripValidator.validateTitle(newTitle)
        TripValidator.validateDates(newStartDate, newEndDate)
        if (input.baseCurrency != null) TripValidator.validateCurrency(newCurrency)

        val updated = trip.copy(
            title = newTitle.trim(),
            description = if (input.description != null) input.description.trim() else trip.description,
            startDate = newStartDate,
            endDate = newEndDate,
            baseCurrency = newCurrency.uppercase().trim(),
            status = input.status ?: trip.status,
            updatedAt = Instant.now(),
            version = trip.version + 1
        )

        return tripRepository.update(updated)
    }
}
