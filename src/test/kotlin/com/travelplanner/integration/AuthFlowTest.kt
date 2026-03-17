package com.travelplanner.integration

import com.travelplanner.api.dto.ErrorResponse
import com.travelplanner.api.dto.request.LoginRequest
import com.travelplanner.api.dto.request.RefreshTokenRequest
import com.travelplanner.api.dto.request.RegisterRequest
import com.travelplanner.api.dto.response.AuthResponse
import com.travelplanner.application.usecase.auth.LoginUseCase
import com.travelplanner.application.usecase.auth.RefreshTokenUseCase
import com.travelplanner.application.usecase.auth.RegisterUseCase
import com.travelplanner.domain.exception.DomainException
import com.travelplanner.domain.model.User
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.coVerify
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthFlowTest : BaseIntegrationTest() {

    private val testUserId = UUID.randomUUID()
    private val testEmail = "alice@example.com"
    private val testDisplayName = "Alice"
    private val testPassword = "securePassword123"
    private val testAccessToken = "mock-access-token"
    private val testRefreshToken = "mock-refresh-token"

    private val testUser = User(
        id = testUserId,
        email = testEmail,
        displayName = testDisplayName,
        passwordHash = "hashed",
        createdAt = Instant.parse("2025-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    private val registerUseCase = io.mockk.mockk<RegisterUseCase>()
    private val loginUseCase = io.mockk.mockk<LoginUseCase>()
    private val refreshTokenUseCase = io.mockk.mockk<RefreshTokenUseCase>()

    override fun additionalKoinModules(): Module = module {
        single { registerUseCase }
        single { loginUseCase }
        single { refreshTokenUseCase }
    }

    @Test
    fun `register returns 201 with auth response`() = testApp { client ->
        coEvery { registerUseCase.execute(any()) } returns RegisterUseCase.Output(
            accessToken = testAccessToken,
            refreshToken = testRefreshToken,
            user = testUser
        )

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(testEmail, testDisplayName, testPassword))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<AuthResponse>()
        assertEquals(testAccessToken, body.accessToken)
        assertEquals(testRefreshToken, body.refreshToken)
        assertEquals(testUserId.toString(), body.user.id)
        assertEquals(testEmail, body.user.email)
        assertEquals(testDisplayName, body.user.displayName)
    }

    @Test
    fun `register with existing email returns 409 Conflict`() = testApp { client ->
        coEvery { registerUseCase.execute(any()) } throws DomainException.EmailAlreadyExists(testEmail)

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(testEmail, testDisplayName, testPassword))
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val body = response.body<ErrorResponse>()
        assertEquals("EMAIL_ALREADY_EXISTS", body.code)
    }

    @Test
    fun `register with short password returns 422`() = testApp { client ->
        coEvery { registerUseCase.execute(any()) } throws DomainException.ValidationError("Password must be at least 8 characters")

        val response = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(testEmail, testDisplayName, "short"))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.body<ErrorResponse>()
        assertEquals("VALIDATION_ERROR", body.code)
    }

    @Test
    fun `login returns 200 with auth response`() = testApp { client ->
        coEvery { loginUseCase.execute(any()) } returns LoginUseCase.Output(
            accessToken = testAccessToken,
            refreshToken = testRefreshToken,
            user = testUser
        )

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, testPassword))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuthResponse>()
        assertEquals(testAccessToken, body.accessToken)
        assertEquals(testRefreshToken, body.refreshToken)
        assertNotNull(body.user)
    }

    @Test
    fun `login with invalid credentials returns 401`() = testApp { client ->
        coEvery { loginUseCase.execute(any()) } throws DomainException.InvalidCredentials()

        val response = client.post("/api/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(testEmail, "wrongpassword"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.body<ErrorResponse>()
        assertEquals("INVALID_CREDENTIALS", body.code)
    }

    @Test
    fun `refresh token returns new tokens`() = testApp { client ->
        coEvery { refreshTokenUseCase.execute(any()) } returns RefreshTokenUseCase.Output(
            accessToken = "new-access-token",
            refreshToken = "new-refresh-token"
        )

        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(testRefreshToken))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `refresh with invalid token returns 401`() = testApp { client ->
        coEvery { refreshTokenUseCase.execute(any()) } throws DomainException.InvalidRefreshToken()

        val response = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest("invalid-token"))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.body<ErrorResponse>()
        assertEquals("INVALID_REFRESH_TOKEN", body.code)
    }

    @Test
    fun `logout requires authentication`() = testApp { client ->
        val response = client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `logout with valid token returns 200`() = testApp { client ->
        val token = generateTestToken(testUserId, testEmail)

        val response = client.post("/api/v1/auth/logout") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
