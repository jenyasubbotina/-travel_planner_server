package com.travelplanner.integration

import com.travelplanner.api.dto.request.RefreshTokenRequest
import com.travelplanner.api.dto.request.RegisterRequest
import com.travelplanner.api.dto.response.AuthResponse
import com.travelplanner.application.usecase.auth.LoginUseCase
import com.travelplanner.application.usecase.auth.LogoutUseCase
import com.travelplanner.application.usecase.auth.RefreshTokenUseCase
import com.travelplanner.application.usecase.auth.RegisterUseCase
import com.travelplanner.domain.model.RefreshToken
import com.travelplanner.domain.model.User
import com.travelplanner.domain.model.UserDevice
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull


class AuthRefreshFlowIntegrationTest : BaseIntegrationTest() {

    private val refreshTokenHasher = RefreshTokenHasher(TEST_JWT_CONFIG.secret)
    private val userRepository: UserRepository = InMemoryUserRepository()

    override fun additionalKoinModules(): Module = module {
        single { refreshTokenHasher }
        single { userRepository }
        single { RegisterUseCase(get(), get(), get()) }
        single { LoginUseCase(get(), get(), get()) }
        single { RefreshTokenUseCase(get(), get(), get()) }
        single { LogoutUseCase(get(), get()) }
        single { jwtService }
    }

    @Test
    fun `register, then refresh, returns new tokens (regression for bcrypt-salt bug)`() = testApp { client ->
        val email = "alice+${UUID.randomUUID()}@example.com"

        val reg = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email, "Alice", "securePassword123"))
        }
        assertEquals(HttpStatusCode.Created, reg.status)
        val regBody = reg.body<AuthResponse>()
        val originalRefresh = regBody.refreshToken
        assertNotNull(originalRefresh)

        val ref1 = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(originalRefresh))
        }
        assertEquals(HttpStatusCode.OK, ref1.status)
        val ref1Body = ref1.body<Map<String, String>>()
        val newRefresh = ref1Body["refreshToken"]!!
        assertNotEquals(originalRefresh, newRefresh, "refresh token must be rotated")

        val replayOld = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(originalRefresh))
        }
        assertEquals(HttpStatusCode.Unauthorized, replayOld.status)

        val ref2 = client.post("/api/v1/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(newRefresh))
        }
        assertEquals(HttpStatusCode.OK, ref2.status)
    }
}


private class InMemoryUserRepository : UserRepository {

    private val usersById = ConcurrentHashMap<UUID, User>()
    private val usersByEmail = ConcurrentHashMap<String, UUID>()
    private val refreshByHash = ConcurrentHashMap<String, RefreshToken>()

    override suspend fun findById(id: UUID): User? = usersById[id]

    override suspend fun findByEmail(email: String): User? =
        usersByEmail[email]?.let { usersById[it] }

    override suspend fun create(user: User): User {
        usersById[user.id] = user
        usersByEmail[user.email] = user.id
        return user
    }

    override suspend fun update(user: User): User {
        usersById[user.id] = user
        return user
    }

    override suspend fun saveRefreshToken(token: RefreshToken) {
        refreshByHash[token.tokenHash] = token
    }

    override suspend fun findRefreshTokenByHash(hash: String): RefreshToken? =
        refreshByHash[hash]

    override suspend fun deleteRefreshToken(hash: String) {
        refreshByHash.remove(hash)
    }

    override suspend fun deleteAllRefreshTokensForUser(userId: UUID) {
        refreshByHash.values.removeAll { it.userId == userId }
    }

    override suspend fun findDevicesByUser(userId: UUID): List<UserDevice> =
        emptyList()

    override suspend fun findDeviceById(id: UUID): UserDevice? = null

    override suspend fun saveDevice(device: UserDevice): UserDevice = device

    override suspend fun deleteDevice(id: UUID, userId: UUID): Boolean = false
}
