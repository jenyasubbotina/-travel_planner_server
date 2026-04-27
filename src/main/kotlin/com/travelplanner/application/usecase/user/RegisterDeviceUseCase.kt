package com.travelplanner.application.usecase.user

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.UserDevice
import com.travelplanner.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class RegisterDeviceUseCase(
    private val userRepository: UserRepository
) {

    private val logger = LoggerFactory.getLogger(RegisterDeviceUseCase::class.java)

    data class Input(val userId: UUID, val fcmToken: String, val deviceName: String? = null)

    suspend fun execute(input: Input): UserDevice {
        if (input.fcmToken.isBlank()) {
            throw DomainException.ValidationError("FCM token is required")
        }

        val device = UserDevice(
            id = UUID.randomUUID(),
            userId = input.userId,
            fcmToken = input.fcmToken,
            deviceName = input.deviceName,
            createdAt = Instant.now()
        )

        val saved = userRepository.saveDevice(device)
        logger.info(
            "Device registered: userId={} deviceId={} deviceName={} tokenSuffix={}",
            input.userId,
            saved.id,
            input.deviceName,
            input.fcmToken.takeLast(8)
        )
        return saved
    }
}
