package com.travelplanner.infrastructure.persistence.repository

import com.travelplanner.domain.model.RefreshToken
import com.travelplanner.domain.model.User
import com.travelplanner.domain.model.UserDevice
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.persistence.DatabaseFactory.dbQuery
import com.travelplanner.infrastructure.persistence.tables.AuthRefreshTokensTable
import com.travelplanner.infrastructure.persistence.tables.UserDevicesTable
import com.travelplanner.infrastructure.persistence.tables.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID

class ExposedUserRepository : UserRepository {

    override suspend fun findById(id: UUID): User? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun findByEmail(email: String): User? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun create(user: User): User = dbQuery {
        UsersTable.insert {
            it[id] = user.id
            it[email] = user.email
            it[displayName] = user.displayName
            it[passwordHash] = user.passwordHash
            it[avatarUrl] = user.avatarUrl
            it[createdAt] = user.createdAt
            it[updatedAt] = user.updatedAt
        }
        user
    }

    override suspend fun update(user: User): User = dbQuery {
        val now = Instant.now()
        UsersTable.update({ UsersTable.id eq user.id }) {
            it[email] = user.email
            it[displayName] = user.displayName
            it[passwordHash] = user.passwordHash
            it[avatarUrl] = user.avatarUrl
            it[updatedAt] = now
        }
        user.copy(updatedAt = now)
    }

    // --- Refresh tokens ---

    override suspend fun saveRefreshToken(token: RefreshToken): Unit = dbQuery {
        AuthRefreshTokensTable.insert {
            it[id] = token.id
            it[userId] = token.userId
            it[tokenHash] = token.tokenHash
            it[expiresAt] = token.expiresAt
            it[createdAt] = token.createdAt
        }
    }

    override suspend fun findRefreshTokenByHash(hash: String): RefreshToken? = dbQuery {
        AuthRefreshTokensTable.selectAll()
            .where { AuthRefreshTokensTable.tokenHash eq hash }
            .singleOrNull()
            ?.toRefreshToken()
    }

    override suspend fun deleteRefreshToken(hash: String): Unit = dbQuery {
        AuthRefreshTokensTable.deleteWhere { tokenHash eq hash }
    }

    override suspend fun deleteAllRefreshTokensForUser(userId: UUID): Unit = dbQuery {
        AuthRefreshTokensTable.deleteWhere { AuthRefreshTokensTable.userId eq userId }
    }

    // --- Devices ---

    override suspend fun findDevicesByUser(userId: UUID): List<UserDevice> = dbQuery {
        UserDevicesTable.selectAll()
            .where { UserDevicesTable.userId eq userId }
            .map { it.toUserDevice() }
    }

    override suspend fun findDeviceById(id: UUID): UserDevice? = dbQuery {
        UserDevicesTable.selectAll()
            .where { UserDevicesTable.id eq id }
            .singleOrNull()
            ?.toUserDevice()
    }

    override suspend fun saveDevice(device: UserDevice): UserDevice = dbQuery {
        UserDevicesTable.insert {
            it[id] = device.id
            it[userId] = device.userId
            it[fcmToken] = device.fcmToken
            it[deviceName] = device.deviceName
            it[createdAt] = device.createdAt
        }
        device
    }

    override suspend fun deleteDevice(id: UUID, userId: UUID): Boolean = dbQuery {
        val deletedCount = UserDevicesTable.deleteWhere {
            (UserDevicesTable.id eq id) and (UserDevicesTable.userId eq userId)
        }
        deletedCount > 0
    }

    // --- Mapping helpers ---

    private fun ResultRow.toUser() = User(
        id = this[UsersTable.id],
        email = this[UsersTable.email],
        displayName = this[UsersTable.displayName],
        passwordHash = this[UsersTable.passwordHash],
        avatarUrl = this[UsersTable.avatarUrl],
        createdAt = this[UsersTable.createdAt],
        updatedAt = this[UsersTable.updatedAt]
    )

    private fun ResultRow.toRefreshToken() = RefreshToken(
        id = this[AuthRefreshTokensTable.id],
        userId = this[AuthRefreshTokensTable.userId],
        tokenHash = this[AuthRefreshTokensTable.tokenHash],
        expiresAt = this[AuthRefreshTokensTable.expiresAt],
        createdAt = this[AuthRefreshTokensTable.createdAt]
    )

    private fun ResultRow.toUserDevice() = UserDevice(
        id = this[UserDevicesTable.id],
        userId = this[UserDevicesTable.userId],
        fcmToken = this[UserDevicesTable.fcmToken],
        deviceName = this[UserDevicesTable.deviceName],
        createdAt = this[UserDevicesTable.createdAt]
    )
}
