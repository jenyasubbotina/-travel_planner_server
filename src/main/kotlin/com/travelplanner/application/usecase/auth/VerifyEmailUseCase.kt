package com.travelplanner.application.usecase.auth

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import java.time.Instant

class VerifyEmailUseCase(
    private val userRepository: UserRepository,
    private val refreshTokenHasher: RefreshTokenHasher
) {

    suspend fun execute(rawToken: String) {
        val token = rawToken.trim()
        if (token.isEmpty()) {
            throw DomainException.InvalidOrExpiredVerificationToken()
        }
        val hash = refreshTokenHasher.hash(token)
        val user = userRepository.findByEmailVerificationTokenHash(hash)
            ?: throw DomainException.InvalidOrExpiredVerificationToken()

        if (user.emailVerifiedAt != null) {
            return
        }
        val expiresAt = user.emailVerificationExpiresAt
            ?: throw DomainException.InvalidOrExpiredVerificationToken()
        if (Instant.now().isAfter(expiresAt)) {
            throw DomainException.InvalidOrExpiredVerificationToken()
        }
        userRepository.confirmEmail(user.id)
    }
}
