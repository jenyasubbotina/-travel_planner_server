package com.travelplanner.application.usecase.auth

import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import java.util.UUID

class LogoutUseCase(
    private val userRepository: UserRepository,
    private val refreshTokenHasher: RefreshTokenHasher
) {

    data class Input(val userId: UUID, val refreshToken: String? = null)

    suspend fun execute(input: Input) {
        if (input.refreshToken != null) {
            val tokenHash = refreshTokenHasher.hash(input.refreshToken)
            userRepository.deleteRefreshToken(tokenHash)
        } else {
            userRepository.deleteAllRefreshTokensForUser(input.userId)
        }
    }
}
