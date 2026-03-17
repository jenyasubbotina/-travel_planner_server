package com.travelplanner

import com.travelplanner.api.plugins.*
import com.travelplanner.api.routes.*
import com.travelplanner.infrastructure.config.AppConfig
import com.travelplanner.infrastructure.fcm.FcmClient
import com.travelplanner.infrastructure.fcm.OutboxProcessor
import com.travelplanner.infrastructure.persistence.DatabaseFactory
import com.travelplanner.infrastructure.di.appModule
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
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
    configureCORS()
    configureStatusPages()

    val jwtService by inject<com.travelplanner.infrastructure.auth.JwtService>()
    configureAuthentication(jwtService)

    // Initialize FCM
    val fcmClient by inject<FcmClient>()
    fcmClient.init()

    // Start outbox processor
    val outboxProcessor by inject<OutboxProcessor>()
    launch { outboxProcessor.start(this) }

    routing {
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
    }
}
