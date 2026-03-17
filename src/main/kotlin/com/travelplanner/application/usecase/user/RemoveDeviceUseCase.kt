package com.travelplanner.application.usecase.user

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.UserRepository
import java.util.UUID

class RemoveDeviceUseCase(
    private val userRepository: UserRepository
) {

    data class Input(val deviceId: UUID, val userId: UUID)

    suspend fun execute(input: Input) {
        val deleted = userRepository.deleteDevice(input.deviceId, input.userId)
        if (!deleted) {
            throw DomainException.DeviceNotFound(input.deviceId)
        }
    }
}
