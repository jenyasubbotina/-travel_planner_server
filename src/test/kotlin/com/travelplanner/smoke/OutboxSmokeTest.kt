package com.travelplanner.smoke

import com.travelplanner.application.usecase.auth.RegisterUseCase
import com.travelplanner.application.usecase.itinerary.CreateItineraryPointUseCase
import com.travelplanner.application.usecase.participant.InviteParticipantUseCase
import com.travelplanner.application.usecase.trip.CreateTripUseCase
import com.travelplanner.domain.model.TripRole
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.infrastructure.auth.JwtService
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import com.travelplanner.infrastructure.config.DatabaseConfig
import com.travelplanner.infrastructure.config.FcmConfig
import com.travelplanner.infrastructure.config.JwtConfig
import com.travelplanner.infrastructure.fcm.FcmClient
import com.travelplanner.infrastructure.fcm.FcmNotificationService
import com.travelplanner.infrastructure.fcm.OutboxProcessor
import com.travelplanner.infrastructure.persistence.DatabaseFactory
import com.travelplanner.infrastructure.persistence.ExposedTransactionRunner
import com.travelplanner.infrastructure.persistence.repository.ExposedDomainEventRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedItineraryRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedParticipantRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedTripRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedUserRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Verifies the outbox pipeline end-to-end against an in-process PostgreSQL instance
 * (zonky embedded-postgres) — no Docker, no permanent install, no external services.
 *
 * Flyway migrations run automatically against the embedded DB; the test cluster is
 * created in a temp directory and torn down when the JVM exits.
 *
 * Asserts:
 *  - CreateTripUseCase writes a TRIP_CREATED row in the same transaction as the aggregate.
 *  - InviteParticipantUseCase writes INVITATION_CREATED with the invitee's user id.
 *  - CreateItineraryPointUseCase writes ITINERARY_POINT_CREATED.
 *  - OutboxProcessor.processEvents marks events as processed without throwing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxSmokeTest {

    private lateinit var postgres: EmbeddedPostgres
    private lateinit var domainEventRepository: DomainEventRepository
    private lateinit var registerUseCase: RegisterUseCase
    private lateinit var createTripUseCase: CreateTripUseCase
    private lateinit var inviteParticipantUseCase: InviteParticipantUseCase
    private lateinit var createItineraryPointUseCase: CreateItineraryPointUseCase
    private lateinit var outboxProcessor: OutboxProcessor

    @BeforeAll
    fun setup() {
        postgres = EmbeddedPostgres.builder().start()

        val config = DatabaseConfig(
            url = postgres.getJdbcUrl("postgres", "postgres"),
            user = "postgres",
            password = "postgres",
            maxPoolSize = 4
        )
        DatabaseFactory.init(config)

        val userRepository = ExposedUserRepository()
        val tripRepository = ExposedTripRepository()
        val participantRepository = ExposedParticipantRepository()
        val itineraryRepository = ExposedItineraryRepository()
        domainEventRepository = ExposedDomainEventRepository()
        val transactionRunner = ExposedTransactionRunner()

        val jwtSecret = "smoke-test-secret-key-at-least-32-characters!!"
        val jwtService = JwtService(
            JwtConfig(
                secret = jwtSecret,
                issuer = "smoke",
                audience = "smoke",
                accessTokenExpiryMinutes = 30,
                refreshTokenExpiryDays = 30
            )
        )
        val refreshTokenHasher = RefreshTokenHasher(jwtSecret)

        registerUseCase = RegisterUseCase(userRepository, jwtService, refreshTokenHasher)
        createTripUseCase = CreateTripUseCase(
            tripRepository = tripRepository,
            participantRepository = participantRepository,
            domainEventRepository = domainEventRepository,
            transactionRunner = transactionRunner
        )
        inviteParticipantUseCase = InviteParticipantUseCase(
            tripRepository = tripRepository,
            participantRepository = participantRepository,
            userRepository = userRepository,
            domainEventRepository = domainEventRepository,
            transactionRunner = transactionRunner
        )
        createItineraryPointUseCase = CreateItineraryPointUseCase(
            tripRepository = tripRepository,
            participantRepository = participantRepository,
            itineraryRepository = itineraryRepository,
            domainEventRepository = domainEventRepository,
            transactionRunner = transactionRunner
        )

        // FCM disabled — sendToDevice becomes a no-op so the outbox runs cleanly.
        val fcmClient = FcmClient(FcmConfig(serviceAccountPath = ""))
        fcmClient.init()
        val fcmNotificationService = FcmNotificationService(fcmClient, userRepository)
        outboxProcessor = OutboxProcessor(
            domainEventRepository = domainEventRepository,
            fcmNotificationService = fcmNotificationService,
            participantRepository = participantRepository
        )
    }

    @AfterAll
    fun teardown() {
        postgres.close()
    }

    @Test
    fun `outbox wiring is live for trip, invitation and itinerary mutations`() = runBlocking {
        val owner = registerUseCase.execute(
            RegisterUseCase.Input(
                email = "owner@smoke.test",
                displayName = "Owner",
                password = "password123"
            )
        ).user

        val invitee = registerUseCase.execute(
            RegisterUseCase.Input(
                email = "invitee@smoke.test",
                displayName = "Invitee",
                password = "password123"
            )
        ).user

        // 1) Create a trip — expect TRIP_CREATED
        val trip = createTripUseCase.execute(
            CreateTripUseCase.Input(
                title = "Smoke Trip",
                baseCurrency = "USD",
                totalBudget = BigDecimal("250.00"),
                destination = "Tokyo",
                userId = owner.id
            )
        )

        var events = domainEventRepository.findUnprocessed()
        assertEquals(1, events.size, "Expected TRIP_CREATED after CreateTripUseCase")
        val tripCreated = events[0]
        assertEquals("TRIP_CREATED", tripCreated.eventType)
        assertEquals("TRIP", tripCreated.aggregateType)
        assertEquals(trip.id, tripCreated.aggregateId)

        val tripPayload = Json.parseToJsonElement(tripCreated.payload).jsonObject
        assertEquals(owner.id.toString(), tripPayload["actorUserId"]?.jsonPrimitive?.content)
        val tripEntity = tripPayload["entity"]?.jsonObject
        assertEquals(
            "Smoke Trip",
            tripEntity?.get("title")?.jsonPrimitive?.content,
            "TRIP_CREATED must expose title at payload.entity.title (HistoryPayload.tripSnapshot) so OutboxProcessor can render real names instead of \"Untitled\""
        )

        // Drain the processor so we can isolate the next event.
        outboxProcessor.processEvents()
        assertTrue(
            domainEventRepository.findUnprocessed().isEmpty(),
            "OutboxProcessor should have marked TRIP_CREATED as processed"
        )

        // 2) Invite a known user — expect INVITATION_CREATED with inviteeUserId
        val invitation = inviteParticipantUseCase.execute(
            InviteParticipantUseCase.Input(
                tripId = trip.id,
                inviterUserId = owner.id,
                email = invitee.email,
                role = TripRole.EDITOR
            )
        )
        events = domainEventRepository.findUnprocessed()
        assertEquals(1, events.size, "Expected INVITATION_CREATED after InviteParticipantUseCase")
        val invitationEvent = events[0]
        assertEquals("INVITATION_CREATED", invitationEvent.eventType)
        val invitationPayload = Json.parseToJsonElement(invitationEvent.payload).jsonObject
        assertEquals(
            invitation.id.toString(),
            invitationPayload["entityId"]?.jsonPrimitive?.content,
            "INVITATION_CREATED must carry the invitation id at top-level entityId — that's what OutboxProcessor reads into FCM data.invitationId"
        )
        val invitationContext = invitationPayload["context"]?.jsonObject
        assertEquals(
            invitee.id.toString(),
            invitationContext?.get("inviteeUserId")?.jsonPrimitive?.content,
            "Payload must include inviteeUserId in context for registered users so the processor can push to their devices"
        )

        outboxProcessor.processEvents()
        assertTrue(domainEventRepository.findUnprocessed().isEmpty())

        // 3) Create an itinerary point — expect ITINERARY_POINT_CREATED keyed by tripId
        val point = createItineraryPointUseCase.execute(
            CreateItineraryPointUseCase.Input(
                tripId = trip.id,
                userId = owner.id,
                title = "Senso-ji"
            )
        )
        events = domainEventRepository.findUnprocessed()
        assertEquals(1, events.size, "Expected ITINERARY_POINT_CREATED after CreateItineraryPointUseCase")
        val itineraryEvent = events[0]
        assertEquals("ITINERARY_POINT_CREATED", itineraryEvent.eventType)
        assertEquals(
            trip.id,
            itineraryEvent.aggregateId,
            "Itinerary events must use tripId as aggregateId so OutboxProcessor resolves participants"
        )
        val itineraryPayload = Json.parseToJsonElement(itineraryEvent.payload).jsonObject
        assertEquals(
            point.id.toString(),
            itineraryPayload["entityId"]?.jsonPrimitive?.content,
            "ITINERARY_POINT_CREATED must carry the point id at top-level entityId"
        )
        val itineraryEntity = itineraryPayload["entity"]?.jsonObject
        assertEquals(
            "Senso-ji",
            itineraryEntity?.get("title")?.jsonPrimitive?.content,
            "ITINERARY_POINT_CREATED must include the point title at payload.entity.title (HistoryPayload.eventSnapshot)"
        )

        outboxProcessor.processEvents()
        assertTrue(domainEventRepository.findUnprocessed().isEmpty())

        assertNotNull(trip, "Smoke pipeline is healthy")
    }
}
