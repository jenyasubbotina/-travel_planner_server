package com.travelplanner.application.usecase.auth

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.RefreshToken
import com.travelplanner.domain.model.User
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.domain.validation.TripValidator
import com.travelplanner.infrastructure.auth.JwtService
import com.travelplanner.infrastructure.auth.PasswordHasher
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class RegisterUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    data class Input(val email: String, val displayName: String, val password: String)
    data class Output(val accessToken: String, val refreshToken: String, val user: User)

    suspend fun execute(input: Input): Output {
        TripValidator.validateEmail(input.email)
        if (input.password.length < 8) {
            throw DomainException.ValidationError("Password must be at least 8 characters")
        }
        if (input.displayName.isBlank()) {
            throw DomainException.ValidationError("Display name is required")
        }

        val existing = userRepository.findByEmail(input.email.lowercase().trim())
        if (existing != null) {
            throw DomainException.EmailAlreadyExists(input.email)
        }

        val now = Instant.now()
        val user = User(
            id = UUID.randomUUID(),
            email = input.email.lowercase().trim(),
            displayName = input.displayName.trim(),
            passwordHash = PasswordHasher.hash(input.password),
            createdAt = now,
            updatedAt = now
        )
        val created = userRepository.create(user)

        val accessToken = jwtService.generateAccessToken(created.id, created.email)
        val refreshTokenStr = jwtService.generateRefreshToken()
        val refreshToken = RefreshToken(
            id = UUID.randomUUID(),
            userId = created.id,
            tokenHash = PasswordHasher.hash(refreshTokenStr),
            expiresAt = now.plus(30, ChronoUnit.DAYS),
            createdAt = now
        )
        userRepository.saveRefreshToken(refreshToken)

        return Output(accessToken, refreshTokenStr, created)
    }
}
