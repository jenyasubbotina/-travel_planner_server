package com.travelplanner.application.usecase.trip

import com.travelplanner.domain.model.Trip
import com.travelplanner.domain.repository.TripRepository
import java.util.UUID

class ListUserTripsUseCase(
    private val tripRepository: TripRepository
) {

    suspend fun execute(userId: UUID): List<Trip> {
        return tripRepository.findByUser(userId)
    }
}
