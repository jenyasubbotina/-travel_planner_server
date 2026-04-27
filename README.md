# Travel Planner Server

Бэкенд для совместного планирования поездок на Kotlin и Ktor. Проект позволяет группам пользователей совместно формировать маршруты, вести и делить расходы, работать с вложениями и синхронизировать данные между устройствами.

## Дипломный проект

**Тема:** серверная часть мобильного/веб-приложения **Travel Planner** для совместных поездок с друзьями: itinerary, расходы и доли, приглашения, роли, вложения в S3, офлайн-синхронизация и push-уведомления (FCM).

В репозитории реализованы и задокументированы:

| Направление | Что сделано |
|-------------|-------------|
| **Проверка схемы БД при сборке** | Интеграционный тест [`ExposedSchemaMatchesFlywayTest`](src/test/kotlin/com/travelplanner/schema/ExposedSchemaMatchesFlywayTest.kt): поднимает PostgreSQL через Testcontainers, применяет миграции Flyway и проверяет соответствие схемы моделям Exposed. |
| **CI/CD** | [`.github/workflows/ci.yml`](.github/workflows/ci.yml): на push/PR в `main`/`master` запускаются тесты и сборка fat JAR. На push в `main`/`master` дополнительно публикуется Docker-образ в GHCR. |
| **Метрики** | Подключен Micrometer + реестр Prometheus, доступен эндпоинт `GET /metrics`. |
| **Kubernetes** | В каталоге [`k8s/`](k8s/) есть манифесты для PostgreSQL, Redis, MinIO, приложения, Ingress и Fluent Bit. |
| **JUnit Platform** | В `testRuntimeOnly` добавлен `junit-platform-launcher` для стабильного запуска тестов с Gradle 9. |

**OpenAPI / Swagger:** спецификация — [`src/main/resources/openapi/documentation.yaml`](src/main/resources/openapi/documentation.yaml), UI — `/swagger`.

## Технологический стек

| Компонент              | Технология                                |
|------------------------|-------------------------------------------|
| Язык                   | Kotlin 2.3                                |
| Фреймворк              | Ktor 3.4 (Netty)                          |
| База данных            | PostgreSQL 16                             |
| ORM                    | Exposed 0.61                              |
| Миграции               | Flyway 9.22                               |
| DI                     | Koin 4.1                                  |
| Аутентификация         | JWT (HMAC-SHA256)                         |
| Кеширование            | Redis 7 (Lettuce)                         |
| Объектное хранилище    | S3-compatible (AWS SDK Kotlin / MinIO)    |
| Push-уведомления       | Firebase Cloud Messaging (FCM)            |
| Сериализация           | kotlinx.serialization (JSON)              |
| Пул соединений         | HikariCP 5.1                              |
| Тестирование           | JUnit 5, MockK, Testcontainers            |
| Метрики                | Micrometer, Prometheus                    |
| CI                     | GitHub Actions                            |

## Требования

- **JDK 21**
- **Docker** и **Docker Compose**
- **Gradle 8.8+** (через Gradle Wrapper в проекте)

## Быстрый старт

### Вариант 1: Docker Compose (рекомендуется)

```bash
docker-compose up --build
```

Сервер будет доступен по адресу `http://localhost:8080`.

### Вариант 2: Локальная разработка

1. Поднимите инфраструктуру:

```bash
docker compose up -d postgres redis minio
```

2. Запустите приложение:

```bash
./gradlew run
```

### Проверка работы сервера

```bash
curl http://localhost:8080/health/live
```

### Метрики (Prometheus)

```bash
curl -s http://localhost:8080/metrics | head
```

### Swagger UI

Откройте [http://localhost:8080/swagger](http://localhost:8080/swagger).

## Kubernetes (обзор)

1. Соберите образ: `docker build -t travel-planner-server:local .`
2. Для minikube: `minikube image load travel-planner-server:local`
3. Установите Ingress NGINX при необходимости.
4. Примените манифесты из каталога `k8s/` (после настройки секретов в [`k8s/secrets.yaml`](k8s/secrets.yaml)).

## Миграции

Миграции Flyway применяются автоматически при старте приложения. Файлы миграций:

```text
src/main/resources/db/migration/
```

## Запуск тестов

```bash
./gradlew test
./gradlew test --info
```

Тесты используют JUnit 5, MockK и Testcontainers. Полное описание стратегии тестирования: [docs/TESTING.md](docs/TESTING.md).

## Артефакты GitHub Actions

После успешного workflow в GitHub Actions скачайте артефакт `travel-planner-server-jar`, распакуйте и запустите:

```bash
java -jar travel-planner-server-all.jar
```

Docker-образ (после push в `main`/`master`):

```bash
docker login ghcr.io -u USERNAME
docker pull ghcr.io/OWNER/REPO:latest
docker run -p 8080:8080 -e DATABASE_URL=... ghcr.io/OWNER/REPO:latest
```

## Структура проекта

```text
src/main/kotlin/com/travelplanner/
|-- Application.kt
|-- api/
|-- application/
|-- domain/
|-- infrastructure/
```

Подробный разбор архитектуры: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Переменные окружения

> Информация о значениях по умолчанию намеренно убрана.

| Переменная                        | Описание |
|-----------------------------------|----------|
| `SERVER_PORT`                     | Порт HTTP-сервера |
| `SERVER_HOST`                     | Адрес привязки HTTP-сервера |
| `DATABASE_URL`                    | JDBC URL PostgreSQL |
| `DATABASE_USER`                   | Пользователь базы данных |
| `DATABASE_PASSWORD`               | Пароль базы данных |
| `JWT_SECRET`                      | Секрет HMAC-SHA256 (минимум 32 символа) |
| `JWT_ISSUER`                      | Claim `iss` для JWT |
| `JWT_AUDIENCE`                    | Claim `aud` для JWT |
| `JWT_ACCESS_TOKEN_EXPIRY_MINUTES` | TTL access-токена в минутах |
| `JWT_REFRESH_TOKEN_EXPIRY_DAYS`   | TTL refresh-токена в днях |
| `REDIS_HOST`                      | Хост Redis |
| `REDIS_PORT`                      | Порт Redis |
| `S3_ENDPOINT`                     | URL S3-совместимого хранилища |
| `S3_ACCESS_KEY`                   | Access key для S3 |
| `S3_SECRET_KEY`                   | Secret key для S3 |
| `S3_BUCKET`                       | Имя S3-бакета |
| `S3_REGION`                       | Регион S3 |
| `FCM_SERVICE_ACCOUNT_PATH`        | Путь к JSON service account Firebase |

## Лицензия

Proprietary. All rights reserved.
