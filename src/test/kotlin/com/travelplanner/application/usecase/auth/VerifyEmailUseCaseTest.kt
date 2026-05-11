package com.travelplanner.application.usecase.auth

import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.User
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import io.mockk.coJustRun
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.assertFailsWith

class VerifyEmailUseCaseTest {

    private val secret = "unit-test-secret-key-at-least-32-characters!!"
    private val hasher = RefreshTokenHasher(secret)

    @Test
    fun `execute confirms email when token matches and not expired`() = runBlocking {
        val repo = mockk<UserRepository>()
        val raw = "opaque-token"
        val hash = hasher.hash(raw)
        val userId = UUID.randomUUID()
        val expires = Instant.now().plus(1, ChronoUnit.HOURS)
        val user = User(
            id = userId,
            email = "a@b.c",
            displayName = "A",
            passwordHash = "x",
            emailVerifiedAt = null,
            emailVerificationExpiresAt = expires,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        coEvery { repo.findByEmailVerificationTokenHash(hash) } returns user
        coJustRun { repo.confirmEmail(userId) }

        VerifyEmailUseCase(repo, hasher).execute(raw)

        coVerify(exactly = 1) { repo.confirmEmail(userId) }
    }

    @Test
    fun `execute throws when token unknown`() = runBlocking {
        val repo = mockk<UserRepository>()
        coEvery { repo.findByEmailVerificationTokenHash(any()) } returns null

        assertFailsWith<DomainException.InvalidOrExpiredVerificationToken> {
            VerifyEmailUseCase(repo, hasher).execute("nope")
        }
    }

    @Test
    fun `execute is no-op when already verified`() = runBlocking {
        val repo = mockk<UserRepository>()
        val raw = "opaque-token"
        val hash = hasher.hash(raw)
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            email = "a@b.c",
            displayName = "A",
            passwordHash = "x",
            emailVerifiedAt = Instant.now(),
            emailVerificationExpiresAt = Instant.now().plus(1, ChronoUnit.HOURS),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        coEvery { repo.findByEmailVerificationTokenHash(hash) } returns user

        VerifyEmailUseCase(repo, hasher).execute(raw)

        coVerify(exactly = 0) { repo.confirmEmail(any()) }
    }
}
