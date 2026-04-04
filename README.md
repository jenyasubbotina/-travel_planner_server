# Travel Planner Server

A collaborative travel planning backend built with Kotlin and Ktor. Travel Planner enables groups of users to co-create trips, manage shared itineraries, track and split expenses, and stay synchronized across devices with offline-first capabilities and push notifications.

## Дипломный проект

**Тема:** бэкенд для мобильного/веб-приложения **Travel Planner** — совместное планирование поездок с друзьями: маршруты и точки на карте (itinerary), общие расходы и доли участников, приглашения в поездку, роли, вложения в S3, офлайн-синхронизация и push-уведомления (FCM). Сервер реализует REST API, хранит пользователей и доменные данные в PostgreSQL, выдаёт JWT, интегрируется с Redis и объектным хранилищем.

В репозиторий добавлены и задокументированы следующие элементы:

| Направление | Что сделано |
|-------------|-------------|
| **Проверка схемы БД при сборке** | Интеграционный тест [`ExposedSchemaMatchesFlywayTest`](src/test/kotlin/com/travelplanner/schema/ExposedSchemaMatchesFlywayTest.kt): поднимается PostgreSQL (Testcontainers), накатываются миграции Flyway, Exposed сравнивает фактическую схему с описанием таблиц в коде. Несоответствие даёт падение `./gradlew test`. Если Docker недоступен, тест **пропускается** (`disabledWithoutDocker = true`), чтобы локальная сборка не ломалась; в **CI на GitHub** Docker есть, проверка выполняется. |
| **CI/CD** | [`.github/workflows/ci.yml`](.github/workflows/ci.yml): на push/PR в `main` или `master` — тесты, fat JAR, **артефакт `travel-planner-server-jar`** (`*-all.jar`, запуск: `java -jar …`). При **push** в `main`/`master` дополнительно **сборка и push Docker-образа** в **GitHub Container Registry** (`ghcr.io/<владелец>/<репозиторий>:latest` и тег по SHA). В PR образ только собирается (проверка `Dockerfile`), без публикации. |
| **Метрики** | Плагин Micrometer для Ktor, реестр Prometheus, эндпоинт **`GET /metrics`** (формат scrape Prometheus). JVM-память, GC, процессор, HTTP-метрики Ktor. |
| **Kubernetes** | Каталог [`k8s/`](k8s/): Namespace, **StatefulSet PostgreSQL**, **Deployment** приложения (2 реплики) и **Service**, Redis и MinIO, **Ingress** (класс `nginx`) как точка входа (балансировка на стороне контроллера), **DaemonSet Fluent Bit** + RBAC для сбора логов контейнеров с узлов кластера (вывод в stdout Fluent Bit; дальше можно подключить Loki/Elastic). |
| **JUnit Platform** | В `testRuntimeOnly` добавлен `junit-platform-launcher` (совместимая с JUnit 5 версия платформы), чтобы Gradle 9 стабильно запускал тесты. |

**OpenAPI / Swagger:** спецификация в [`src/main/resources/openapi/documentation.yaml`](src/main/resources/openapi/documentation.yaml), UI: **`/swagger`**. В спецификацию добавлен маршрут **`/metrics`**.

## Tech Stack

| Component             | Technology                                |
|-----------------------|-------------------------------------------|
| Language              | Kotlin 2.3                                |
| Framework             | Ktor 3.4 (Netty engine)                   |
| Database              | PostgreSQL 16                             |
| ORM                   | Exposed 0.61                              |
| Migrations            | Flyway 10.15                              |
| Dependency Injection  | Koin 4.1                                  |
| Authentication        | JWT (HMAC-SHA256) via ktor-auth-jwt       |
| Caching               | Redis 7 (Lettuce client)                  |
| Object Storage        | S3-compatible (AWS SDK Kotlin / MinIO)    |
| Push Notifications    | Firebase Cloud Messaging (FCM)            |
| Serialization         | kotlinx.serialization (JSON)              |
| Connection Pooling    | HikariCP 5.1                              |
| Testing               | JUnit 5, MockK, Testcontainers            |
| Metrics               | Micrometer, Prometheus registry           |
| CI                    | GitHub Actions                            |

## Prerequisites

- **JDK 21** (Eclipse Temurin recommended)
- **Docker** and **Docker Compose** (for infrastructure services)
- **Gradle 8.8+** (included via Gradle Wrapper)

## Quick Start

### Option 1: Docker Compose (recommended)

Start everything -- the application server, PostgreSQL, Redis, and MinIO -- with a single command:

```bash
docker-compose up --build
```

The server will be available at `http://localhost:8080`.

### Option 2: Local Development

1. **Start infrastructure services only:**

   ```bash
   docker-compose up postgres redis minio
   ```

2. **Run the application:**

   ```bash
   ./gradlew run
   ```

   The server starts on `http://localhost:8080`.

### Verify the server is running

```bash
curl http://localhost:8080/health/live
```

Expected response:

```json
{
  "status": "UP",
  "timestamp": "2026-04-04T12:00:00.000Z"
}
```

### Metrics (Prometheus)

```bash
curl -s http://localhost:8080/metrics | head
```

### Swagger UI

Open [http://localhost:8080/swagger](http://localhost:8080/swagger) in a browser.

## Kubernetes (обзор)

1. Создать образ приложения: `docker build -t travel-planner-server:local .`  
2. Для minikube: `minikube image load travel-planner-server:local`  
3. Установить [Ingress NGINX](https://kubernetes.github.io/ingress-nginx/deploy/) при необходимости.  
4. Применить манифесты по порядку (после правки секретов в [`k8s/secrets.yaml`](k8s/secrets.yaml)):

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/postgres.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/minio.yaml
# дождаться готовности postgres-0
kubectl apply -f k8s/app.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/fluent-bit.yaml
```

Добавьте `travel-planner.local` в `/etc/hosts` на IP Ingress-контроллера. Формат логов на нодах зависит от container runtime (CRI / Docker); при необходимости скорректируйте парсер во [`k8s/fluent-bit.yaml`](k8s/fluent-bit.yaml).

## Running Migrations

Flyway migrations run **automatically** on application startup. Migration files are located in:

```
src/main/resources/db/migration/
```

Current migrations:

| Version | Description                   |
|---------|-------------------------------|
| V1      | Initial schema (users, trips, participants, invitations, auth tokens, devices) |
| V2      | Add itinerary points          |
| V3      | Add expenses and splits       |
| V4      | Add attachments               |
| V5      | Add sync and idempotency keys |
| V6      | Add domain events (outbox)    |

To run migrations independently (without starting the app), connect to the database and use the Flyway CLI, or simply start the application -- it will apply any pending migrations before accepting requests.

## Running Tests

```bash
# Run all tests
./gradlew test

# Run with verbose output
./gradlew test --info
```

Tests use **JUnit 5** with **MockK** for mocking and **Testcontainers** where applicable. Тест **`ExposedSchemaMatchesFlywayTest`** проверяет соответствие миграций Flyway моделям Exposed и требует **Docker**; без Docker он помечается как пропущенный. See [docs/TESTING.md](docs/TESTING.md) for the full testing strategy.

### Артефакты GitHub Actions

После успешного workflow на вкладке **Actions** → выбранный run → **Artifacts** скачайте **`travel-planner-server-jar`**, распакуйте и запустите (нужен JRE 21 и доступная PostgreSQL/Redis/MinIO по переменным окружения):

```bash
java -jar travel-planner-server-all.jar
```

Образ из registry (только после **push** в `main`/`master`; пакет может быть приватным — включите чтение в настройках пакета или войдите):

```bash
docker login ghcr.io -u USERNAME
docker pull ghcr.io/OWNER/REPO:latest
docker run -p 8080:8080 -e DATABASE_URL=... ghcr.io/OWNER/REPO:latest
```

Подставьте `OWNER/REPO` из URL репозитория (в нижнем регистре для `docker pull`, как формирует GHCR).

## API Overview

All API endpoints are served under the `/api/v1` prefix (except health checks). Authentication uses JWT Bearer tokens.

| Group          | Base Path                                        | Description                            |
|----------------|--------------------------------------------------|----------------------------------------|
| Health         | `/health`                                        | Liveness and readiness probes          |
| Auth           | `/api/v1/auth`                                   | Register, login, refresh, logout       |
| Users          | `/api/v1/me`                                     | Profile and device management          |
| Trips          | `/api/v1/trips`                                  | CRUD for trips                         |
| Participants   | `/api/v1/trips/{tripId}/participants`             | Invite, remove, change roles           |
| Itinerary      | `/api/v1/trips/{tripId}/itinerary`                | Itinerary points with reordering       |
| Expenses       | `/api/v1/trips/{tripId}/expenses`                 | Expense tracking with flexible splits  |
| Analytics      | `/api/v1/trips/{tripId}/balances|settlements|statistics` | Balances, settlements, spending stats |
| Attachments    | `/api/v1/attachments`, `/api/v1/trips/{tripId}/attachments` | File uploads via presigned S3 URLs |
| Sync           | `/api/v1/trips/{tripId}/snapshot|sync`            | Offline-first sync (snapshot + delta)  |

For the complete API reference, see **[docs/API.md](docs/API.md)**.

## Project Structure

```
src/main/kotlin/com/travelplanner/
|-- Application.kt                  # Ktor entry point and module configuration
|-- api/
|   |-- dto/
|   |   |-- ErrorResponse.kt        # Unified error envelope
|   |   |-- request/Requests.kt     # All request DTOs
|   |   |-- response/Responses.kt   # All response DTOs
|   |-- middleware/
|   |   |-- AuthMiddleware.kt       # JWT principal extraction
|   |   |-- TripAccessMiddleware.kt # Trip participant/role checks
|   |-- plugins/                    # Ktor plugin configuration
|   |   |-- Authentication.kt
|   |   |-- CORSConfig.kt
|   |   |-- CallLogging.kt
|   |   |-- ContentNegotiation.kt
|   |   |-- StatusPages.kt         # DomainException -> HTTP status mapping
|   |-- routes/                     # Route definitions (one file per domain)
|-- application/
|   |-- service/                    # Cross-cutting services (cache, notifications)
|   |-- usecase/                    # Application use cases grouped by domain
|       |-- analytics/
|       |-- attachment/
|       |-- auth/
|       |-- expense/
|       |-- itinerary/
|       |-- participant/
|       |-- sync/
|       |-- trip/
|       |-- user/
|-- domain/
|   |-- exception/DomainException.kt  # Sealed hierarchy of domain errors
|   |-- model/                        # Pure domain models (no framework deps)
|   |-- repository/                   # Repository interfaces (ports)
|   |-- validation/                   # Domain validation logic
|-- infrastructure/
    |-- auth/                         # JWT and password hashing
    |-- config/                       # Typed configuration classes
    |-- di/AppModule.kt               # Koin dependency wiring
    |-- fcm/                          # Firebase Cloud Messaging + outbox
    |-- persistence/
    |   |-- DatabaseFactory.kt        # HikariCP + Flyway setup
    |   |-- repository/               # Exposed repository implementations
    |   |-- tables/                   # Exposed table definitions
    |-- redis/                        # Redis cache integration
    |-- s3/                           # S3 presigned URL generation
```

For a detailed architecture walkthrough, see **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Environment Variables

| Variable                          | Description                              | Default                                         |
|-----------------------------------|------------------------------------------|-------------------------------------------------|
| `SERVER_PORT`                     | HTTP server port                         | `8080`                                          |
| `SERVER_HOST`                     | HTTP server bind address                 | `0.0.0.0`                                       |
| `DATABASE_URL`                    | PostgreSQL JDBC URL                      | `jdbc:postgresql://localhost:5433/travel_planner`|
| `DATABASE_USER`                   | Database username                        | `tp_user`                                       |
| `DATABASE_PASSWORD`               | Database password                        | `tp_pass`                                       |
| `JWT_SECRET`                      | HMAC-SHA256 secret (min 32 chars)        | *(development default, change in production)*   |
| `JWT_ISSUER`                      | JWT issuer claim                         | `travel-planner`                                |
| `JWT_AUDIENCE`                    | JWT audience claim                       | `travel-planner-client`                         |
| `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` | Access token TTL in minutes              | `30`                                            |
| `JWT_REFRESH_TOKEN_EXPIRY_DAYS`   | Refresh token TTL in days                | `30`                                            |
| `REDIS_HOST`                      | Redis hostname                           | `localhost`                                     |
| `REDIS_PORT`                      | Redis port                               | `6379`                                          |
| `S3_ENDPOINT`                     | S3-compatible endpoint URL               | `http://localhost:9000`                         |
| `S3_ACCESS_KEY`                   | S3 access key                            | `minioadmin`                                    |
| `S3_SECRET_KEY`                   | S3 secret key                            | `minioadmin`                                    |
| `S3_BUCKET`                       | S3 bucket name                           | `travel-planner`                                |
| `S3_REGION`                       | S3 region                                | `us-east-1`                                     |
| `FCM_SERVICE_ACCOUNT_PATH`        | Path to Firebase service account JSON    | *(empty -- disables push notifications)*        |

## Further Documentation

- [Architecture](docs/ARCHITECTURE.md) -- Clean Architecture layers, design decisions, data flow
- [API Reference](docs/API.md) -- Complete REST endpoint documentation
- [Database Schema](docs/DB_SCHEMA.md) -- Tables, columns, relationships, indexes
- [Sync Protocol](docs/SYNC_PROTOCOL.md) -- Offline-first synchronization design
- [Notifications](docs/NOTIFICATIONS.md) -- Push notification architecture and outbox pattern
- [Testing](docs/TESTING.md) -- Testing strategy, tooling, and coverage goals

## License

Proprietary. All rights reserved.
