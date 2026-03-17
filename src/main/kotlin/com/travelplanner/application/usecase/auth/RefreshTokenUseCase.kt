package com.travelplanner.application.usecase.auth

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.RefreshToken
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.auth.JwtService
import com.travelplanner.infrastructure.auth.PasswordHasher
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class RefreshTokenUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    data class Input(val refreshToken: String)
    data class Output(val accessToken: String, val refreshToken: String)

    suspend fun execute(input: Input): Output {
        val tokenHash = PasswordHasher.hash(input.refreshToken)
        val storedToken = userRepository.findRefreshTokenByHash(tokenHash)
            ?: throw DomainException.InvalidRefreshToken()

        val now = Instant.now()
        if (storedToken.expiresAt.isBefore(now)) {
            userRepository.deleteRefreshToken(tokenHash)
            throw DomainException.TokenExpired()
        }

        val user = userRepository.findById(storedToken.userId)
            ?: throw DomainException.InvalidRefreshToken()

        // Delete old refresh token
        userRepository.deleteRefreshToken(tokenHash)

        // Generate new tokens
        val newAccessToken = jwtService.generateAccessToken(user.id, user.email)
        val newRefreshTokenStr = jwtService.generateRefreshToken()
        val newRefreshToken = RefreshToken(
            id = UUID.randomUUID(),
            userId = user.id,
            tokenHash = PasswordHasher.hash(newRefreshTokenStr),
            expiresAt = now.plus(30, ChronoUnit.DAYS),
            createdAt = now
        )
        userRepository.saveRefreshToken(newRefreshToken)

        return Output(newAccessToken, newRefreshTokenStr)
    }
}
