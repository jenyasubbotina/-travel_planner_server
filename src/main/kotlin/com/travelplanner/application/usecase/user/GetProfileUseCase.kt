package com.travelplanner.application.usecase.user

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.User
import com.travelplanner.domain.repository.UserRepository
import java.util.UUID

class GetProfileUseCase(
    private val userRepository: UserRepository
) {

    suspend fun execute(userId: UUID): User {
        return userRepository.findById(userId)
            ?: throw DomainException.UserNotFound(userId)
    }
}
