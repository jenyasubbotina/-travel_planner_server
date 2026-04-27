package com.travelplanner.application.usecase.auth

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.RefreshToken
import com.travelplanner.domain.model.User
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.domain.validation.TripValidator
import com.travelplanner.infrastructure.auth.JwtService
import com.travelplanner.infrastructure.auth.PasswordHasher
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class LoginUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val refreshTokenHasher: RefreshTokenHasher
) {

    data class Input(val email: String, val password: String)
    data class Output(val accessToken: String, val refreshToken: String, val user: User)

    suspend fun execute(input: Input): Output {
        TripValidator.validateEmail(input.email)

        val user = userRepository.findByEmail(input.email.lowercase().trim())
            ?: throw DomainException.InvalidCredentials()

        if (!PasswordHasher.verify(input.password, user.passwordHash)) {
            throw DomainException.InvalidCredentials()
        }

        val now = Instant.now()
        val accessToken = jwtService.generateAccessToken(user.id, user.email)
        val refreshTokenStr = jwtService.generateRefreshToken()
        val refreshToken = RefreshToken(
            id = UUID.randomUUID(),
            userId = user.id,
            tokenHash = refreshTokenHasher.hash(refreshTokenStr),
            expiresAt = now.plus(30, ChronoUnit.DAYS),
            createdAt = now
        )
        userRepository.saveRefreshToken(refreshToken)

        return Output(accessToken, refreshTokenStr, user)
    }
}
