# Architecture

This document describes the architectural design of the Travel Planner backend, including the layer structure, package organization, data flow, and key design decisions.

## Clean Architecture Overview

The project follows a **Clean Architecture** (also known as Hexagonal / Ports-and-Adapters) approach organized into four concentric layers. Dependencies always point inward -- outer layers know about inner layers, but inner layers never reference outer layers.

```
+-------------------------------------------------------------------+
|                         api (routes, DTOs, plugins)                |
|  +-------------------------------------------------------------+  |
|  |                  application (use cases, services)           |  |
|  |  +-------------------------------------------------------+  |  |
|  |  |               domain (models, repositories, validation)|  |  |
|  |  +-------------------------------------------------------+  |  |
|  +-------------------------------------------------------------+  |
|                   infrastructure (DB, Redis, S3, FCM)              |
+-------------------------------------------------------------------+
```

### Layer Responsibilities

| Layer             | Package                         | Responsibility                                                  |
|-------------------|----------------------------------|-----------------------------------------------------------------|
| **Domain**        | `com.travelplanner.domain`       | Pure business models, repository interfaces (ports), validation rules, domain exceptions. Zero framework dependencies. |
| **Application**   | `com.travelplanner.application`  | Use cases that orchestrate domain logic. Each use case is a single class with an `execute` method. Cross-cutting services (caching, notification triggers). |
| **API**           | `com.travelplanner.api`          | HTTP route definitions, request/response DTOs, Ktor plugins (auth, CORS, serialization, error handling), middleware for auth and access control. |
| **Infrastructure**| `com.travelplanner.infrastructure`| Concrete implementations of domain ports: Exposed repositories, Redis cache, S3 storage, FCM push, JWT/password services, Koin DI wiring, database initialization. |

## Package Structure

```
com.travelplanner/
|-- Application.kt                          # Entry point
|
|-- api/
|   |-- dto/
|   |   |-- ErrorResponse.kt               # Shared error envelope
|   |   |-- request/Requests.kt            # All inbound DTOs
|   |   |-- response/Responses.kt          # All outbound DTOs
|   |-- middleware/
|   |   |-- AuthMiddleware.kt              # Extract userId from JWT principal
|   |   |-- TripAccessMiddleware.kt        # Trip participant & role guards
|   |-- plugins/
|   |   |-- Authentication.kt             # JWT auth configuration
|   |   |-- CORSConfig.kt                 # CORS policy
|   |   |-- CallLogging.kt                # Request/response logging
|   |   |-- ContentNegotiation.kt         # JSON serialization setup
|   |   |-- StatusPages.kt                # DomainException -> HTTP mapping
|   |-- routes/
|       |-- AnalyticsRoutes.kt
|       |-- AttachmentRoutes.kt
|       |-- AuthRoutes.kt
|       |-- ExpenseRoutes.kt
|       |-- HealthRoutes.kt
|       |-- ItineraryRoutes.kt
|       |-- ParticipantRoutes.kt
|       |-- SyncRoutes.kt
|       |-- TripRoutes.kt
|       |-- UserRoutes.kt
|
|-- application/
|   |-- service/
|   |   |-- CacheService.kt               # Cache abstraction
|   |   |-- NotificationService.kt        # Notification trigger abstraction
|   |-- usecase/
|       |-- analytics/                     # CalculateBalances, CalculateSettlements, GetStatistics
|       |-- attachment/                    # CreateAttachment, DeleteAttachment, RequestPresignedUpload
|       |-- auth/                          # Register, Login, RefreshToken, Logout
|       |-- expense/                       # Create, Update, Delete, ListExpenses
|       |-- itinerary/                     # Create, Update, Delete, ReorderItineraryPoint
|       |-- participant/                   # Invite, Accept, Remove, ChangeRole
|       |-- sync/                          # GetSnapshot, GetDeltaSync
|       |-- trip/                          # Create, Update, Delete, Get, List, Archive
|       |-- user/                          # GetProfile, RegisterDevice, RemoveDevice
|
|-- domain/
|   |-- exception/DomainException.kt       # Sealed class hierarchy
|   |-- model/                             # Data classes: Trip, User, Expense, etc.
|   |-- repository/                        # Interfaces (ports): TripRepository, etc.
|   |-- validation/                        # ExpenseSplitValidator, TripValidator
|
|-- infrastructure/
    |-- auth/
    |   |-- JwtService.kt                  # Token generation and verification
    |   |-- PasswordHasher.kt              # BCrypt hashing
    |-- config/AppConfig.kt                # Typed config loaded from HOCON
    |-- di/AppModule.kt                    # Koin module wiring all dependencies
    |-- fcm/
    |   |-- FcmClient.kt                   # Firebase Admin SDK wrapper
    |   |-- FcmNotificationService.kt      # Sends FCM messages to trip participants
    |   |-- OutboxProcessor.kt             # Polls domain_events, dispatches notifications
    |-- persistence/
    |   |-- DatabaseFactory.kt             # HikariCP pool + Flyway migrations
    |   |-- repository/                    # ExposedXxxRepository implementations
    |   |-- tables/                        # Exposed Table objects
    |-- redis/
    |   |-- RedisCacheService.kt           # Redis-backed cache
    |   |-- RedisFactory.kt                # Lettuce client setup
    |-- s3/
        |-- S3ClientFactory.kt             # AWS SDK S3 client
        |-- S3StorageService.kt            # Presigned URL generation
```

## Data Flow

A typical authenticated request flows through the system as follows:

```
Client
  |
  v
HTTP Request (Ktor/Netty)
  |
  v
Ktor Plugins (call logging, content negotiation, CORS)
  |
  v
Authentication Plugin (validates JWT, attaches JWTPrincipal)
  |
  v
Route Handler (e.g., TripRoutes.kt)
  |-- Extracts userId from principal via AuthMiddleware
  |-- Deserializes request body into DTO
  |-- Calls Use Case
  |
  v
Use Case (e.g., CreateTripUseCase)
  |-- Validates business rules
  |-- Calls Repository interface methods
  |-- Optionally saves DomainEvents (outbox)
  |
  v
Repository Implementation (e.g., ExposedTripRepository)
  |-- Executes SQL via Exposed DSL inside dbQuery {}
  |-- Maps DB rows to domain models
  |
  v
PostgreSQL
```

Error flow:

```
DomainException thrown at any layer
  |
  v
StatusPages plugin catches it
  |-- Maps exception to HTTP status code + ErrorResponse
  |
  v
JSON response returned to client
```

## Key Design Decisions

### Single Gradle Module

The project uses a single module rather than a multi-module build. For a backend of this size (one bounded context, one team), a single module keeps the build fast and avoids the overhead of inter-module dependency management. Layer separation is enforced by package conventions.

### Exposed ORM

JetBrains Exposed was chosen over JPA/Hibernate for the following reasons:

- **Kotlin-native** -- seamless integration with coroutines, null safety, and data classes
- **Type-safe SQL DSL** -- queries are validated at compile time
- **Lightweight** -- no reflection-based entity proxying, no lazy-loading surprises
- **Explicit transactions** -- `newSuspendedTransaction` makes transaction boundaries visible

### Flyway Migrations

Schema evolution is managed with versioned Flyway migrations (`V1__` through `V6__`). Migrations run automatically at startup before the application accepts traffic. This ensures the database schema is always consistent with the application code.

### Koin for Dependency Injection

Koin was selected because:

- First-class Ktor integration via `koin-ktor`
- Lightweight, no annotation processing or code generation
- Simple DSL-based module definition
- All wiring lives in a single `AppModule.kt` file

### Coroutine-Based I/O

All database and I/O operations are suspending functions using `Dispatchers.IO` inside Exposed transactions. This allows Ktor to handle many concurrent requests without blocking Netty's event loop threads.

### Sealed Exception Hierarchy

All domain errors extend a sealed `DomainException` class. This provides:

- **Exhaustive handling** in `StatusPages` -- the compiler ensures every exception subtype is mapped to an HTTP status
- **Structured error codes** -- each exception carries a machine-readable `code` field
- **Clean separation** -- domain logic throws domain exceptions; the API layer translates them to HTTP responses

## Dependency Injection with Koin

All dependencies are wired in `infrastructure/di/AppModule.kt`. The module is organized into sections:

1. **Infrastructure services** -- JwtService, RedisFactory, S3ClientFactory, FcmClient
2. **Repositories** -- Each domain repository interface is bound to its Exposed implementation
3. **Use cases** -- Each use case is registered as a singleton, injected with its required repositories and services

Routes obtain dependencies via Koin's `inject<T>()` delegate:

```kotlin
fun Route.tripRoutes() {
    val createTripUseCase by inject<CreateTripUseCase>()
    // ...
}
```

## Error Handling Strategy

Errors are handled at three levels:

### 1. Domain Validation

Domain validators (e.g., `ExpenseSplitValidator`) and use cases throw typed `DomainException` subtypes when business rules are violated.

### 2. StatusPages Plugin

The Ktor `StatusPages` plugin maps every `DomainException` subtype to a specific HTTP status code:

| Exception Type           | HTTP Status           |
|--------------------------|-----------------------|
| `EmailAlreadyExists`     | 409 Conflict          |
| `InvalidCredentials`     | 401 Unauthorized      |
| `InvalidRefreshToken`    | 401 Unauthorized      |
| `TokenExpired`           | 401 Unauthorized      |
| `*NotFound`              | 404 Not Found         |
| `AccessDenied`           | 403 Forbidden         |
| `InsufficientRole`       | 403 Forbidden         |
| `ValidationError`        | 422 Unprocessable     |
| `InvalidSplitSum`        | 422 Unprocessable     |
| `ParticipantNotInTrip`   | 422 Unprocessable     |
| `VersionConflict`        | 409 Conflict          |
| `IdempotencyConflict`    | 409 Conflict          |
| `TripNotActive`          | 422 Unprocessable     |

### 3. Catch-All

Any unhandled `Throwable` is logged and returned as a `500 Internal Server Error` with a generic message (to avoid leaking implementation details).

All error responses share a consistent JSON envelope:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Human-readable description",
  "details": null,
  "traceId": null
}
```

## Authentication Flow

### Registration

1. Client sends email, displayName, password to `POST /api/v1/auth/register`
2. `RegisterUseCase` validates uniqueness, hashes password with BCrypt
3. Creates user record, generates JWT access token and refresh token
4. Returns `AuthResponse` with both tokens and user profile

### Login

1. Client sends email, password to `POST /api/v1/auth/login`
2. `LoginUseCase` verifies credentials against stored BCrypt hash
3. Generates new access/refresh token pair
4. Returns `AuthResponse`

### Token Refresh

1. Client sends expired access token's refresh token to `POST /api/v1/auth/refresh`
2. `RefreshTokenUseCase` validates the refresh token hash against the database
3. Issues a new access token and a new refresh token (rotating refresh tokens)

### Authenticated Requests

1. Client includes `Authorization: Bearer <access_token>` header
2. Ktor's JWT auth plugin verifies the token signature, issuer, audience, and expiry
3. The `JWTPrincipal` is attached to the call
4. Route handlers extract the user ID via `currentUserId()` middleware

### Logout

1. Authenticated client calls `POST /api/v1/auth/logout`
2. `LogoutUseCase` invalidates all refresh tokens for the user

### Token Configuration

| Parameter                | Default | Env Variable                       |
|--------------------------|---------|------------------------------------|
| Access token expiry      | 30 min  | `JWT_ACCESS_TOKEN_EXPIRY_MINUTES`  |
| Refresh token expiry     | 30 days | `JWT_REFRESH_TOKEN_EXPIRY_DAYS`    |
| Signing algorithm        | HS256   | *(not configurable)*               |
