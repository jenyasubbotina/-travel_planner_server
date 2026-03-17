package com.travelplanner.infrastructure.config

import io.ktor.server.config.ApplicationConfig

data class AppConfig(
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val redis: RedisConfig,
    val s3: S3Config,
    val fcm: FcmConfig
) {
    companion object {
        fun load(config: ApplicationConfig): AppConfig {
            return AppConfig(
                database = DatabaseConfig(
                    url = config.property("database.url").getString(),
                    user = config.property("database.user").getString(),
                    password = config.property("database.password").getString(),
                    maxPoolSize = config.propertyOrNull("database.maxPoolSize")
                        ?.getString()?.toIntOrNull() ?: 10
                ),
                jwt = JwtConfig(
                    secret = config.property("jwt.secret").getString(),
                    issuer = config.property("jwt.issuer").getString(),
                    audience = config.property("jwt.audience").getString(),
                    accessTokenExpiryMinutes = config.propertyOrNull("jwt.accessTokenExpiryMinutes")
                        ?.getString()?.toLongOrNull() ?: 30L,
                    refreshTokenExpiryDays = config.propertyOrNull("jwt.refreshTokenExpiryDays")
                        ?.getString()?.toLongOrNull() ?: 30L
                ),
                redis = RedisConfig(
                    host = config.property("redis.host").getString(),
                    port = config.propertyOrNull("redis.port")
                        ?.getString()?.toIntOrNull() ?: 6379
                ),
                s3 = S3Config(
                    endpoint = config.property("s3.endpoint").getString(),
                    accessKey = config.property("s3.accessKey").getString(),
                    secretKey = config.property("s3.secretKey").getString(),
                    bucket = config.property("s3.bucket").getString(),
                    region = config.property("s3.region").getString()
                ),
                fcm = FcmConfig(
                    serviceAccountPath = config.propertyOrNull("fcm.serviceAccountPath")
                        ?.getString() ?: ""
                )
            )
        }
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val maxPoolSize: Int = 10
)

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTokenExpiryMinutes: Long = 30,
    val refreshTokenExpiryDays: Long = 30
)

data class RedisConfig(
    val host: String,
    val port: Int = 6379
)

data class S3Config(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String
)

data class FcmConfig(
    val serviceAccountPath: String
)
