package com.travelplanner

import com.travelplanner.api.plugins.*
import com.travelplanner.api.routes.*
import com.travelplanner.domain.repository.IdempotencyRepository
import com.travelplanner.infrastructure.config.AppConfig
import com.travelplanner.infrastructure.fcm.FcmClient
import com.travelplanner.infrastructure.fcm.OutboxProcessor
import com.travelplanner.infrastructure.persistence.DatabaseFactory
import com.travelplanner.infrastructure.s3.S3StorageService
import com.travelplanner.infrastructure.di.appModule
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.doublereceive.DoubleReceive
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val appConfig = AppConfig.load(environment.config)

    install(Koin) {
        slf4jLogger()
        modules(
            module { single { appConfig } },
            appModule
        )
    }

    DatabaseFactory.init(appConfig.database)

    configureCallLogging()
    configureContentNegotiation()
    install(DoubleReceive)
    configureCORS()
    configureStatusPages()
    val metricsRegistry = configureMetrics()

    val jwtService by inject<com.travelplanner.infrastructure.auth.JwtService>()
    configureAuthentication(jwtService)

    // Initialize FCM
    val fcmClient by inject<FcmClient>()
    fcmClient.init()

    // Ensure the S3/MinIO bucket exists
    val s3Storage by inject<S3StorageService>()
    launch { s3Storage.ensureBucketExists() }

    // Start outbox processor
    val outboxProcessor by inject<OutboxProcessor>()
    launch { outboxProcessor.start(this) }

    val idempotencyRepository by inject<IdempotencyRepository>()
    val idempotencyCleanupLog = LoggerFactory.getLogger("IdempotencyCleanup")
    launch {
        while (true) {
            try {
                idempotencyRepository.cleanExpired()
            } catch (e: Exception) {
                idempotencyCleanupLog.warn("cleanExpired failed: {}", e.message)
            }
            delay(60 * 60 * 1000L)
        }
    }

    log.info(
        "Admin debug page: enabled={} bindLocalhostOnly={}",
        appConfig.admin.enabled,
        appConfig.admin.bindLocalhostOnly,
    )

    routing {
        install(Idempotency) {
            repository = idempotencyRepository
        }

        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        healthRoutes()
        authRoutes()
        userRoutes()
        tripRoutes()
        participantRoutes()
        itineraryRoutes()
        expenseRoutes()
        analyticsRoutes()
        attachmentRoutes()
        syncRoutes()
        checklistRoutes()
        historyRoutes()
        joinCodeRoutes()

        if (appConfig.admin.enabled) {
            adminRoutes(appConfig.admin, metricsRegistry)
        }
    }
}
