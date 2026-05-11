package com.travelplanner.domain.repository

import com.travelplanner.domain.model.RefreshToken
import com.travelplanner.domain.model.User
import com.travelplanner.domain.model.UserDevice
import java.time.Instant
import java.util.UUID

interface UserRepository {
    suspend fun findById(id: UUID): User?
    suspend fun findByEmail(email: String): User?
    suspend fun findByEmailVerificationTokenHash(tokenHash: String): User?
    suspend fun create(user: User): User
    suspend fun update(user: User): User

    suspend fun setEmailVerificationToken(userId: UUID, tokenHash: String, expiresAt: Instant)
    suspend fun confirmEmail(userId: UUID)

    // Refresh tokens
    suspend fun saveRefreshToken(token: RefreshToken)
    suspend fun findRefreshTokenByHash(hash: String): RefreshToken?
    suspend fun deleteRefreshToken(hash: String)
    suspend fun deleteAllRefreshTokensForUser(userId: UUID)

    // Devices
    suspend fun findDevicesByUser(userId: UUID): List<UserDevice>
    suspend fun findDeviceById(id: UUID): UserDevice?
    suspend fun saveDevice(device: UserDevice): UserDevice
    suspend fun deleteDevice(id: UUID, userId: UUID): Boolean
}
