package com.travelplanner.application.service

import java.util.UUID

interface NotificationService {

    suspend fun notifyTripParticipants(
        tripParticipantUserIds: List<UUID>,
        excludeUserId: UUID?,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    )
}
