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

        logger.info(
            "notifyTripParticipants: tripParticipants={} excludeUserId={} -> recipients={}",
            tripParticipantUserIds.size,
            excludeUserId,
            recipientIds.size
        )

        for (userId in recipientIds) {
            notifyUser(userId, title, body, data)
        }
    }

    /**
     * Loads the user's devices and sends to each. Per-device permanent failures (FCM
     * UNREGISTERED / INVALID_ARGUMENT / SENDER_ID_MISMATCH) trigger a row prune so we
     * stop pestering dead tokens — these accumulate when a user reinstalls the app or
     * an FCM token rotates without the client cleanly logging out of the previous one.
     * Transient FCM errors are logged-and-swallowed; the device row is preserved.
     */
    suspend fun notifyUser(
        userId: UUID,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val devices = userRepository.findDevicesByUser(userId)
        logger.info("notifyUser: userId={} devices={}", userId, devices.size)
        for (device in devices) {
            val result = fcmClient.sendToDevice(
                token = device.fcmToken,
                title = title,
                body = body,
                data = data
            )
            if (result is FcmSendResult.StaleToken) {
                val deleted = userRepository.deleteDevice(device.id, userId)
                logger.info(
                    "Pruned stale device deviceId={} userId={} deleted={} tokenSuffix={}",
                    device.id, userId, deleted, device.fcmToken.takeLast(8)
                )
            }
        }
    }
}
