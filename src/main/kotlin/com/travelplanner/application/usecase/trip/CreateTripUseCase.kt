package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.model.TripParticipant
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.model.TripStatus
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.validation.TripValidator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CreateTripUseCase(
    private val tripRepository: TripRepository,
    private val participantRepository: ParticipantRepository
) {

    data class Input(
        val title: String,
        val description: String? = null,
        val startDate: LocalDate? = null,
        val endDate: LocalDate? = null,
        val baseCurrency: String = "USD",
        val userId: UUID
    )

    suspend fun execute(input: Input): Trip {
        TripValidator.validateTitle(input.title)
        TripValidator.validateDates(input.startDate, input.endDate)
        TripValidator.validateCurrency(input.baseCurrency)

        val now = Instant.now()
        val trip = Trip(
            id = UUID.randomUUID(),
            title = input.title.trim(),
            description = input.description?.trim(),
            startDate = input.startDate,
            endDate = input.endDate,
            baseCurrency = input.baseCurrency.uppercase().trim(),
            status = TripStatus.ACTIVE,
            createdBy = input.userId,
            createdAt = now,
            updatedAt = now,
            version = 1
        )

        val created = tripRepository.create(trip)

        val participant = TripParticipant(
            tripId = created.id,
            userId = input.userId,
            role = TripRole.OWNER,
            joinedAt = now
        )
        participantRepository.add(participant)

        return created
    }
}
