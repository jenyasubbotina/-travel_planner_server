package com.travelplanner.integration

import com.travelplanner.api.plugins.configureAuthentication
import com.travelplanner.api.plugins.configureContentNegotiation
import com.travelplanner.api.plugins.configureStatusPages
import com.travelplanner.api.routes.analyticsRoutes
import com.travelplanner.api.routes.attachmentRoutes
import com.travelplanner.api.routes.authRoutes
import com.travelplanner.api.routes.expenseRoutes
import com.travelplanner.api.routes.healthRoutes
import com.travelplanner.api.routes.itineraryRoutes
import com.travelplanner.api.routes.participantRoutes
import com.travelplanner.api.routes.syncRoutes
import com.travelplanner.api.routes.tripRoutes
import com.travelplanner.api.routes.userRoutes
import com.travelplanner.application.usecase.analytics.CalculateBalancesUseCase
import com.travelplanner.application.usecase.analytics.CalculateSettlementsUseCase
import com.travelplanner.application.usecase.analytics.GetStatisticsUseCase
import com.travelplanner.application.usecase.attachment.CreateAttachmentUseCase
import com.travelplanner.application.usecase.attachment.DeleteAttachmentUseCase
import com.travelplanner.application.usecase.attachment.RequestPresignedUploadUseCase
import com.travelplanner.application.usecase.auth.LoginUseCase
import com.travelplanner.application.usecase.auth.LogoutUseCase
import com.travelplanner.application.usecase.auth.RefreshTokenUseCase
import com.travelplanner.application.usecase.auth.RegisterUseCase
import com.travelplanner.application.usecase.expense.CreateExpenseUseCase
import com.travelplanner.application.usecase.expense.DeleteExpenseUseCase
import com.travelplanner.application.usecase.expense.ListExpensesUseCase
import com.travelplanner.application.usecase.expense.UpdateExpenseUseCase
import com.travelplanner.application.usecase.itinerary.CreateItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.DeleteItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.ReorderItineraryUseCase
import com.travelplanner.application.usecase.itinerary.UpdateItineraryPointUseCase
import com.travelplanner.application.usecase.participant.AcceptInvitationUseCase
import com.travelplanner.application.usecase.participant.ChangeRoleUseCase
import com.travelplanner.application.usecase.participant.InviteParticipantUseCase
import com.travelplanner.application.usecase.participant.RemoveParticipantUseCase
import com.travelplanner.application.usecase.sync.GetDeltaSyncUseCase
import com.travelplanner.application.usecase.sync.GetSnapshotUseCase
import com.travelplanner.application.usecase.trip.ArchiveTripUseCase
import com.travelplanner.application.usecase.trip.CreateTripUseCase
import com.travelplanner.application.usecase.trip.DeleteTripUseCase
import com.travelplanner.application.usecase.trip.GetTripUseCase
import com.travelplanner.application.usecase.trip.ListUserTripsUseCase
import com.travelplanner.application.usecase.trip.UpdateTripUseCase
import com.travelplanner.application.usecase.user.GetProfileUseCase
import com.travelplanner.application.usecase.user.RegisterDeviceUseCase
import com.travelplanner.application.usecase.user.RemoveDeviceUseCase
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.auth.JwtService
import com.travelplanner.infrastructure.config.JwtConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * Base class for integration tests.
 *
 * Sets up a Ktor test application with mocked use cases / repositories injected via Koin.
 * This allows testing the full HTTP pipeline (routing, serialization, status pages,
 * authentication) without requiring an actual database.
 *
 * Sub-classes can override [additionalKoinModules] to provide specific mock behaviour.
 */
abstract class BaseIntegrationTest {

    companion object {
        val TEST_JWT_CONFIG = JwtConfig(
            secret = "test-secret-key-at-least-32-characters-long!!",
            issuer = "test-issuer",
            audience = "test-audience",
            accessTokenExpiryMinutes = 30,
            refreshTokenExpiryDays = 30
        )
        val jwtService = JwtService(TEST_JWT_CONFIG)
    }

    /** Override in subclasses to supply additional Koin modules with specific mock bindings. */
    open fun additionalKoinModules(): org.koin.core.module.Module = module { }

    /** Configures the Ktor Application with Koin DI, plugins and routes. */
    private fun Application.testModule() {
        install(Koin) {
            slf4jLogger()
            modules(defaultMockModule(), additionalKoinModules())
        }

        configureContentNegotiation()
        configureStatusPages()
        configureAuthentication(jwtService)

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

    protected fun testApp(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application {
            testModule()
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        block(client)
    }

    /**
     * Generates a valid JWT access token for the given [userId] and [email].
     * Use this in tests to set the Authorization header.
     */
    protected fun generateTestToken(userId: java.util.UUID, email: String = "test@example.com"): String {
        return jwtService.generateAccessToken(userId, email)
    }

    /**
     * Default Koin module that provides relaxed MockK instances of every dependency
     * that the route layer expects. Each mock is relaxed so that un-stubbed calls
     * return sensible defaults instead of throwing.
     */
    private fun defaultMockModule() = module {
        // Use cases - Auth
        single { mockk<RegisterUseCase>(relaxed = true) }
        single { mockk<LoginUseCase>(relaxed = true) }
        single { mockk<RefreshTokenUseCase>(relaxed = true) }
        single { mockk<LogoutUseCase>(relaxed = true) }

        // Use cases - User
        single { mockk<GetProfileUseCase>(relaxed = true) }
        single { mockk<RegisterDeviceUseCase>(relaxed = true) }
        single { mockk<RemoveDeviceUseCase>(relaxed = true) }

        // Use cases - Trip
        single { mockk<CreateTripUseCase>(relaxed = true) }
        single { mockk<UpdateTripUseCase>(relaxed = true) }
        single { mockk<GetTripUseCase>(relaxed = true) }
        single { mockk<ListUserTripsUseCase>(relaxed = true) }
        single { mockk<ArchiveTripUseCase>(relaxed = true) }
        single { mockk<DeleteTripUseCase>(relaxed = true) }

        // Use cases - Participant
        single { mockk<InviteParticipantUseCase>(relaxed = true) }
        single { mockk<AcceptInvitationUseCase>(relaxed = true) }
        single { mockk<RemoveParticipantUseCase>(relaxed = true) }
        single { mockk<ChangeRoleUseCase>(relaxed = true) }

        // Use cases - Itinerary
        single { mockk<CreateItineraryPointUseCase>(relaxed = true) }
        single { mockk<UpdateItineraryPointUseCase>(relaxed = true) }
        single { mockk<DeleteItineraryPointUseCase>(relaxed = true) }
        single { mockk<ReorderItineraryUseCase>(relaxed = true) }

        // Use cases - Expense
        single { mockk<CreateExpenseUseCase>(relaxed = true) }
        single { mockk<UpdateExpenseUseCase>(relaxed = true) }
        single { mockk<DeleteExpenseUseCase>(relaxed = true) }
        single { mockk<ListExpensesUseCase>(relaxed = true) }

        // Use cases - Analytics
        single { mockk<CalculateBalancesUseCase>(relaxed = true) }
        single { mockk<CalculateSettlementsUseCase>(relaxed = true) }
        single { mockk<GetStatisticsUseCase>(relaxed = true) }

        // Use cases - Attachment
        single { mockk<RequestPresignedUploadUseCase>(relaxed = true) }
        single { mockk<CreateAttachmentUseCase>(relaxed = true) }
        single { mockk<DeleteAttachmentUseCase>(relaxed = true) }

        // Use cases - Sync
        single { mockk<GetSnapshotUseCase>(relaxed = true) }
        single { mockk<GetDeltaSyncUseCase>(relaxed = true) }

        // Repositories (needed by some route handlers directly)
        single { mockk<ParticipantRepository>(relaxed = true) }
        single { mockk<UserRepository>(relaxed = true) }
        single { mockk<ExpenseRepository>(relaxed = true) }
        single { mockk<ItineraryRepository>(relaxed = true) }
    }
}
