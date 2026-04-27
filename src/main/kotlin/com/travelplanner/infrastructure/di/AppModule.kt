package com.travelplanner.infrastructure.di

import com.travelplanner.application.usecase.auth.LoginUseCase
import com.travelplanner.application.usecase.auth.LogoutUseCase
import com.travelplanner.application.usecase.auth.RefreshTokenUseCase
import com.travelplanner.application.usecase.auth.RegisterUseCase
import com.travelplanner.application.usecase.user.GetProfileUseCase
import com.travelplanner.application.usecase.user.RegisterDeviceUseCase
import com.travelplanner.application.usecase.user.RemoveDeviceUseCase
import com.travelplanner.application.usecase.trip.ArchiveTripUseCase
import com.travelplanner.application.usecase.trip.CreateTripUseCase
import com.travelplanner.application.usecase.trip.DeleteTripUseCase
import com.travelplanner.application.usecase.trip.GetTripUseCase
import com.travelplanner.application.usecase.trip.ListUserTripsUseCase
import com.travelplanner.application.usecase.trip.UpdateTripUseCase
import com.travelplanner.application.usecase.participant.AcceptInvitationUseCase
import com.travelplanner.application.usecase.participant.ChangeRoleUseCase
import com.travelplanner.application.usecase.participant.InviteParticipantUseCase
import com.travelplanner.application.usecase.participant.RemoveParticipantUseCase
import com.travelplanner.application.usecase.itinerary.CreateItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.DeleteItineraryPointUseCase
import com.travelplanner.application.usecase.itinerary.ReorderItineraryUseCase
import com.travelplanner.application.usecase.itinerary.UpdateItineraryPointUseCase
import com.travelplanner.application.usecase.expense.CreateExpenseUseCase
import com.travelplanner.application.usecase.expense.DeleteExpenseUseCase
import com.travelplanner.application.usecase.expense.ListExpensesUseCase
import com.travelplanner.application.usecase.expense.UpdateExpenseUseCase
import com.travelplanner.application.usecase.analytics.CalculateBalancesUseCase
import com.travelplanner.application.usecase.analytics.CalculateSettlementsUseCase
import com.travelplanner.application.usecase.analytics.GetStatisticsUseCase
import com.travelplanner.application.usecase.attachment.CreateAttachmentUseCase
import com.travelplanner.application.usecase.attachment.DeleteAttachmentUseCase
import com.travelplanner.application.usecase.attachment.RequestPresignedDownloadUseCase
import com.travelplanner.application.usecase.attachment.RequestPresignedUploadUseCase
import com.travelplanner.application.usecase.sync.GetDeltaSyncUseCase
import com.travelplanner.application.usecase.sync.GetSnapshotUseCase
import com.travelplanner.domain.repository.AttachmentRepository
import com.travelplanner.domain.repository.DomainEventRepository
import com.travelplanner.domain.repository.ExpenseRepository
import com.travelplanner.domain.repository.IdempotencyRepository
import com.travelplanner.domain.repository.ItineraryRepository
import com.travelplanner.domain.repository.ParticipantRepository
import com.travelplanner.domain.repository.SyncRepository
import com.travelplanner.domain.repository.TransactionRunner
import com.travelplanner.domain.repository.TripRepository
import com.travelplanner.domain.repository.UserRepository
import com.travelplanner.infrastructure.auth.JwtService
import com.travelplanner.infrastructure.auth.RefreshTokenHasher
import com.travelplanner.infrastructure.config.AppConfig
import com.travelplanner.infrastructure.fcm.FcmClient
import com.travelplanner.infrastructure.fcm.FcmNotificationService
import com.travelplanner.infrastructure.fcm.OutboxProcessor
import com.travelplanner.infrastructure.persistence.ExposedTransactionRunner
import com.travelplanner.infrastructure.persistence.repository.ExposedAttachmentRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedDomainEventRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedExpenseRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedIdempotencyRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedItineraryRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedParticipantRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedTripRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedSyncRepository
import com.travelplanner.infrastructure.persistence.repository.ExposedUserRepository
import com.travelplanner.infrastructure.redis.RedisCacheService
import com.travelplanner.infrastructure.redis.RedisFactory
import com.travelplanner.infrastructure.s3.S3ClientFactory
import com.travelplanner.infrastructure.s3.S3StorageService
import org.koin.dsl.module

val appModule = module {

    // ──────────────────────────────────────────────
    // Infrastructure — Auth
    // ──────────────────────────────────────────────
    single { JwtService(get<AppConfig>().jwt) }
    single { RefreshTokenHasher(get<AppConfig>().jwt.secret) }

    // ──────────────────────────────────────────────
    // Infrastructure — Redis
    // ──────────────────────────────────────────────
    single { RedisFactory(get<AppConfig>().redis) }
    single { RedisCacheService(get()) }

    // ──────────────────────────────────────────────
    // Infrastructure — S3
    // ──────────────────────────────────────────────
    single { S3ClientFactory(get<AppConfig>().s3) }
    single {
        val factory = get<S3ClientFactory>()
        S3StorageService(factory.createClient(), factory.createPresignClient(), get<AppConfig>().s3)
    }

    // ──────────────────────────────────────────────
    // Infrastructure — FCM
    // ──────────────────────────────────────────────
    single { FcmClient(get<AppConfig>().fcm) }
    single { FcmNotificationService(get(), get()) }
    single { OutboxProcessor(get(), get(), get()) }

    // ──────────────────────────────────────────────
    // Infrastructure — Transaction Runner
    // ──────────────────────────────────────────────
    single<TransactionRunner> { ExposedTransactionRunner() }

    // ──────────────────────────────────────────────
    // Repositories
    // ──────────────────────────────────────────────
    single<UserRepository> { ExposedUserRepository() }
    single<TripRepository> { ExposedTripRepository() }
    single<ParticipantRepository> { ExposedParticipantRepository() }
    single<ItineraryRepository> { ExposedItineraryRepository() }
    single<ExpenseRepository> { ExposedExpenseRepository() }
    single<AttachmentRepository> { ExposedAttachmentRepository() }
    single<DomainEventRepository> { ExposedDomainEventRepository() }
    single<IdempotencyRepository> { ExposedIdempotencyRepository() }
    single<SyncRepository> { ExposedSyncRepository() }

    // ──────────────────────────────────────────────
    // Use Cases — Auth
    // ──────────────────────────────────────────────
    single { RegisterUseCase(get(), get(), get()) }
    single { LoginUseCase(get(), get(), get()) }
    single { RefreshTokenUseCase(get(), get(), get()) }
    single { LogoutUseCase(get(), get()) }

    // ──────────────────────────────────────────────
    // Use Cases — User
    // ──────────────────────────────────────────────
    single { GetProfileUseCase(get()) }
    single { RegisterDeviceUseCase(get()) }
    single { RemoveDeviceUseCase(get()) }

    // ──────────────────────────────────────────────
    // Use Cases — Trip
    // ──────────────────────────────────────────────
    single { CreateTripUseCase(get(), get(), get(), get()) }
    single { UpdateTripUseCase(get(), get(), get(), get()) }
    single { GetTripUseCase(get(), get()) }
    single { ListUserTripsUseCase(get()) }
    single { ArchiveTripUseCase(get(), get(), get(), get()) }
    single { DeleteTripUseCase(get(), get(), get(), get()) }

    // ──────────────────────────────────────────────
    // Use Cases — Participant
    // ──────────────────────────────────────────────
    single { InviteParticipantUseCase(get(), get(), get(), get(), get()) }
    single { AcceptInvitationUseCase(get(), get(), get(), get()) }
    single { RemoveParticipantUseCase(get(), get(), get(), get(), get()) }
    single { ChangeRoleUseCase(get(), get(), get(), get(), get()) }

    // ──────────────────────────────────────────────
    // Use Cases — Itinerary
    // ──────────────────────────────────────────────
    single { CreateItineraryPointUseCase(get(), get(), get(), get(), get()) }
    single { UpdateItineraryPointUseCase(get(), get(), get(), get()) }
    single { DeleteItineraryPointUseCase(get(), get(), get(), get()) }
    single { ReorderItineraryUseCase(get(), get(), get(), get()) }

    // ──────────────────────────────────────────────
    // Use Cases — Expense
    // ──────────────────────────────────────────────
    single { CreateExpenseUseCase(get(), get(), get(), get(), get()) }
    single {
        UpdateExpenseUseCase(
            participantRepository = get<ParticipantRepository>(),
            expenseRepository = get<ExpenseRepository>(),
            domainEventRepository = get<DomainEventRepository>(),
            transactionRunner = get<TransactionRunner>()
        )
    }
    single { DeleteExpenseUseCase(get(), get(), get(), get()) }
    single { ListExpensesUseCase(get(), get()) }

    // ──────────────────────────────────────────────
    // Use Cases — Analytics
    // ──────────────────────────────────────────────
    single { CalculateBalancesUseCase(get(), get()) }
    single { CalculateSettlementsUseCase(get(), get(), get()) }
    single { GetStatisticsUseCase(get(), get(), get()) }

    // ──────────────────────────────────────────────
    // Use Cases — Attachment
    // ──────────────────────────────────────────────
    single { RequestPresignedUploadUseCase(get(), get()) }
    single { RequestPresignedDownloadUseCase(get(), get()) }
    single { CreateAttachmentUseCase(get(), get(), get(), get()) }
    single { DeleteAttachmentUseCase(get(), get(), get()) }

    // ──────────────────────────────────────────────
    // Use Cases — Sync
    // ──────────────────────────────────────────────
    single { GetSnapshotUseCase(get(), get()) }
    single { GetDeltaSyncUseCase(get(), get()) }
}
