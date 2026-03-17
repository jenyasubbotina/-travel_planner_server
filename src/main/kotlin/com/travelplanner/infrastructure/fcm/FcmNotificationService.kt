package com.travelplanner.infrastructure.fcm

import com.travelplanner.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class FcmNotificationService(
    private val fcmClient: FcmClient,
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(FcmNotificationService::class.java)

    suspend fun notifyTripParticipants(
        tripParticipantUserIds: List<UUID>,
        excludeUserId: UUID?,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val recipientIds = if (excludeUserId != null) {
            tripParticipantUserIds.filter { it != excludeUserId }
        } else {
            tripParticipantUserIds
        }

        for (userId in recipientIds) {
            try {
                val devices = userRepository.findDevicesByUser(userId)
                for (device in devices) {
                    fcmClient.sendToDevice(
                        token = device.fcmToken,
                        title = title,
                        body = body,
                        data = data
                    )
                }
            } catch (e: Exception) {
                logger.error(
                    "Failed to send notification to userId={}: {}",
                    userId,
                    e.message,
                    e
                )
            }
        }
    }
}
