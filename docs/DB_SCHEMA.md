# Database Schema

This document describes the PostgreSQL database schema for the Travel Planner backend, including all tables, columns, types, constraints, indexes, and relationships.

## Overview

The schema is managed through **Flyway** versioned migrations located in `src/main/resources/db/migration/`. There are currently six migration files (V1 through V6).

The database uses:

- **PostgreSQL 16** with `gen_random_uuid()` for UUID generation
- **TIMESTAMPTZ** for all timestamps (stored in UTC)
- **Soft deletes** via nullable `deleted_at` columns on entities that support it
- **Optimistic locking** via `version` columns on concurrently-edited entities
- **DECIMAL(15,2)** and **DECIMAL(15,4)** for monetary amounts

## Entity Relationship Diagram

```
users ──────────────────────────────────────────────────────────────────┐
  |                                                                      |
  |  1:N                                                                 |
  +-----> user_devices                                                   |
  |                                                                      |
  +-----> auth_refresh_tokens                                            |
  |                                                                      |
  +-----> trips (created_by)                                             |
  |         |                                                            |
  |         |  1:N                                                       |
  |         +-----> trip_participants (trip_id, user_id) <---- users     |
  |         |                                                            |
  |         +-----> trip_invitations (invited_by) <------------ users    |
  |         |                                                            |
  |         +-----> itinerary_points (created_by) <------------ users   |
  |         |                                                            |
  |         +-----> expenses (payer_user_id, created_by) <----- users   |
  |         |         |                                                  |
  |         |         +-----> expense_splits (participant_user_id) <---- users
  |         |                                                            |
  |         +-----> attachments (uploaded_by) <------------- users      |
  |                   |                                                  |
  |                   +-----> expenses (optional expense_id link)        |
  |                                                                      |
  +-----> domain_events                                                  |
  |                                                                      |
  +-----> idempotency_keys (user_id)                                    |
```

## Tables

### users

Core user identity table.

| Column        | Type          | Nullable | Default            | Description              |
|---------------|---------------|----------|--------------------|--------------------------|
| id            | UUID          | No       | gen_random_uuid()  | Primary key              |
| email         | VARCHAR(255)  | No       |                    | Unique email address     |
| display_name  | VARCHAR(255)  | No       |                    | User display name        |
| password_hash | VARCHAR(255)  | No       |                    | BCrypt password hash     |
| avatar_url    | VARCHAR(500)  | Yes      |                    | Profile picture URL      |
| created_at    | TIMESTAMPTZ   | No       | now()              | Account creation time    |
| updated_at    | TIMESTAMPTZ   | No       | now()              | Last profile update      |

**Constraints:** `UNIQUE(email)`

**Indexes:**

| Name            | Columns | Purpose            |
|-----------------|---------|---------------------|
| idx_users_email | email   | Fast login lookups  |

---

### user_devices

FCM push notification device tokens for users.

| Column      | Type          | Nullable | Default            | Description            |
|-------------|---------------|----------|--------------------|------------------------|
| id          | UUID          | No       | gen_random_uuid()  | Primary key            |
| user_id     | UUID          | No       |                    | FK -> users(id)        |
| fcm_token   | VARCHAR(500)  | No       |                    | Firebase Cloud Messaging token |
| device_name | VARCHAR(255)  | Yes      |                    | Human-readable device name |
| created_at  | TIMESTAMPTZ   | No       | now()              | Registration time      |

**Constraints:** `UNIQUE(user_id, fcm_token)`, `FK user_id -> users(id) ON DELETE CASCADE`

**Indexes:**

| Name                   | Columns | Purpose                      |
|------------------------|---------|-------------------------------|
| idx_user_devices_user  | user_id | Find devices by user          |

---

### auth_refresh_tokens

Stores hashed refresh tokens for JWT token rotation.

| Column     | Type          | Nullable | Default            | Description                     |
|------------|---------------|----------|--------------------|----------------------------------|
| id         | UUID          | No       | gen_random_uuid()  | Primary key                      |
| user_id    | UUID          | No       |                    | FK -> users(id)                  |
| token_hash | VARCHAR(255)  | No       |                    | SHA-256 hash of the refresh token |
| expires_at | TIMESTAMPTZ   | No       |                    | Token expiration time            |
| created_at | TIMESTAMPTZ   | No       | now()              | Token creation time              |

**Constraints:** `UNIQUE(token_hash)`, `FK user_id -> users(id) ON DELETE CASCADE`

**Indexes:**

| Name                       | Columns    | Purpose                       |
|----------------------------|------------|-------------------------------|
| idx_refresh_tokens_user    | user_id    | Find tokens by user (logout)  |
| idx_refresh_tokens_expires | expires_at | Expire old tokens             |

---

### trips

Central entity representing a collaborative trip.

| Column        | Type          | Nullable | Default            | Description                   |
|---------------|---------------|----------|--------------------|-------------------------------|
| id            | UUID          | No       | gen_random_uuid()  | Primary key                   |
| title         | VARCHAR(255)  | No       |                    | Trip title                    |
| description   | TEXT          | Yes      |                    | Trip description              |
| start_date    | DATE          | Yes      |                    | Planned start date            |
| end_date      | DATE          | Yes      |                    | Planned end date              |
| base_currency | VARCHAR(10)   | No       | 'USD'              | ISO 4217 currency code        |
| status        | VARCHAR(20)   | No       | 'ACTIVE'           | ACTIVE, ARCHIVED, COMPLETED   |
| created_by    | UUID          | No       |                    | FK -> users(id)               |
| created_at    | TIMESTAMPTZ   | No       | now()              | Creation time                 |
| updated_at    | TIMESTAMPTZ   | No       | now()              | Last modification time        |
| version       | BIGINT        | No       | 1                  | Optimistic lock version       |
| deleted_at    | TIMESTAMPTZ   | Yes      |                    | Soft delete timestamp         |

**Constraints:** `FK created_by -> users(id)`

**Indexes:**

| Name                  | Columns    | Purpose                            |
|-----------------------|------------|------------------------------------|
| idx_trips_created_by  | created_by | Find trips by creator              |
| idx_trips_status      | status     | Filter by status                   |
| idx_trips_updated_at  | updated_at | Delta sync queries                 |

---

### trip_participants

Junction table linking users to trips with roles. Uses a composite primary key.

| Column    | Type        | Nullable | Default   | Description                      |
|-----------|-------------|----------|-----------|----------------------------------|
| trip_id   | UUID        | No       |           | FK -> trips(id), part of PK      |
| user_id   | UUID        | No       |           | FK -> users(id), part of PK      |
| role      | VARCHAR(20) | No       | 'EDITOR'  | OWNER, EDITOR, or VIEWER         |
| joined_at | TIMESTAMPTZ | No       | now()     | When the user joined the trip    |

**Constraints:** `PRIMARY KEY (trip_id, user_id)`, `FK trip_id -> trips(id) ON DELETE CASCADE`, `FK user_id -> users(id) ON DELETE CASCADE`

**Indexes:**

| Name                       | Columns | Purpose                       |
|----------------------------|---------|-------------------------------|
| idx_trip_participants_user | user_id | Find all trips for a user     |

---

### trip_invitations

Pending, accepted, or declined trip invitations.

| Column      | Type          | Nullable | Default   | Description                         |
|-------------|---------------|----------|-----------|-------------------------------------|
| id          | UUID          | No       | gen_random_uuid() | Primary key                   |
| trip_id     | UUID          | No       |           | FK -> trips(id)                     |
| email       | VARCHAR(255)  | No       |           | Invitee email address               |
| invited_by  | UUID          | No       |           | FK -> users(id), who sent the invite|
| role        | VARCHAR(20)   | No       | 'EDITOR'  | Role to assign on acceptance        |
| status      | VARCHAR(20)   | No       | 'PENDING' | PENDING, ACCEPTED, or DECLINED      |
| created_at  | TIMESTAMPTZ   | No       | now()     | Invitation creation time            |
| resolved_at | TIMESTAMPTZ   | Yes      |           | When accepted or declined           |

**Constraints:** `FK trip_id -> trips(id) ON DELETE CASCADE`, `FK invited_by -> users(id)`

**Indexes:**

| Name                         | Columns | Purpose                          |
|------------------------------|---------|----------------------------------|
| idx_trip_invitations_trip    | trip_id | Find invitations for a trip      |
| idx_trip_invitations_email   | email   | Find invitations by email        |

---

### itinerary_points

Individual stops, activities, or events within a trip itinerary.

| Column      | Type             | Nullable | Default            | Description                |
|-------------|------------------|----------|--------------------|----------------------------|
| id          | UUID             | No       | gen_random_uuid()  | Primary key                |
| trip_id     | UUID             | No       |                    | FK -> trips(id)            |
| title       | VARCHAR(255)     | No       |                    | Point title                |
| description | TEXT             | Yes      |                    | Description                |
| type        | VARCHAR(50)      | Yes      |                    | Category (e.g., ATTRACTION)|
| date        | DATE             | Yes      |                    | Scheduled date             |
| start_time  | TIME             | Yes      |                    | Start time                 |
| end_time    | TIME             | Yes      |                    | End time                   |
| latitude    | DOUBLE PRECISION | Yes      |                    | GPS latitude               |
| longitude   | DOUBLE PRECISION | Yes      |                    | GPS longitude              |
| address     | VARCHAR(500)     | Yes      |                    | Human-readable address     |
| sort_order  | INT              | No       | 0                  | Display order within trip  |
| created_by  | UUID             | No       |                    | FK -> users(id)            |
| created_at  | TIMESTAMPTZ      | No       | now()              | Creation time              |
| updated_at  | TIMESTAMPTZ      | No       | now()              | Last modification time     |
| version     | BIGINT           | No       | 1                  | Optimistic lock version    |
| deleted_at  | TIMESTAMPTZ      | Yes      |                    | Soft delete timestamp      |

**Constraints:** `FK trip_id -> trips(id) ON DELETE CASCADE`, `FK created_by -> users(id)`

**Indexes:**

| Name                   | Columns    | Purpose                     |
|------------------------|------------|------------------------------|
| idx_itinerary_trip     | trip_id    | List points for a trip       |
| idx_itinerary_updated  | updated_at | Delta sync queries           |
| idx_itinerary_date     | date       | Filter by date               |

---

### expenses

Shared expenses within a trip.

| Column         | Type          | Nullable | Default            | Description                   |
|----------------|---------------|----------|--------------------|-------------------------------|
| id             | UUID          | No       | gen_random_uuid()  | Primary key                   |
| trip_id        | UUID          | No       |                    | FK -> trips(id)               |
| payer_user_id  | UUID          | No       |                    | FK -> users(id), who paid     |
| title          | VARCHAR(255)  | No       |                    | Expense title                 |
| description    | TEXT          | Yes      |                    | Expense description           |
| amount         | DECIMAL(15,2) | No       |                    | Total amount                  |
| currency       | VARCHAR(10)   | No       |                    | ISO 4217 currency code        |
| category       | VARCHAR(100)  | No       |                    | Expense category              |
| expense_date   | DATE          | No       |                    | When the expense occurred     |
| split_type     | VARCHAR(20)   | No       | 'EQUAL'            | EQUAL, PERCENTAGE, SHARES, EXACT_AMOUNT |
| created_by     | UUID          | No       |                    | FK -> users(id)               |
| created_at     | TIMESTAMPTZ   | No       | now()              | Creation time                 |
| updated_at     | TIMESTAMPTZ   | No       | now()              | Last modification time        |
| version        | BIGINT        | No       | 1                  | Optimistic lock version       |
| deleted_at     | TIMESTAMPTZ   | Yes      |                    | Soft delete timestamp         |

**Constraints:** `FK trip_id -> trips(id) ON DELETE CASCADE`, `FK payer_user_id -> users(id)`, `FK created_by -> users(id)`

**Indexes:**

| Name                  | Columns       | Purpose                        |
|-----------------------|---------------|--------------------------------|
| idx_expenses_trip     | trip_id       | List expenses for a trip       |
| idx_expenses_payer    | payer_user_id | Filter by payer                |
| idx_expenses_category | category      | Filter by category             |
| idx_expenses_date     | expense_date  | Filter by date                 |
| idx_expenses_updated  | updated_at    | Delta sync queries             |

---

### expense_splits

How an expense is divided among trip participants.

| Column                     | Type          | Nullable | Default            | Description                            |
|----------------------------|---------------|----------|--------------------|----------------------------------------|
| id                         | UUID          | No       | gen_random_uuid()  | Primary key                            |
| expense_id                 | UUID          | No       |                    | FK -> expenses(id)                     |
| participant_user_id        | UUID          | No       |                    | FK -> users(id), who owes this portion |
| share_type                 | VARCHAR(20)   | No       |                    | Same as parent expense split_type      |
| value                      | DECIMAL(15,4) | No       |                    | Input value (shares, percentage, etc.) |
| amount_in_expense_currency | DECIMAL(15,2) | No       |                    | Calculated amount owed in expense currency |

**Constraints:** `UNIQUE(expense_id, participant_user_id)`, `FK expense_id -> expenses(id) ON DELETE CASCADE`, `FK participant_user_id -> users(id)`

**Indexes:**

| Name                          | Columns              | Purpose                              |
|-------------------------------|----------------------|--------------------------------------|
| idx_expense_splits_expense    | expense_id           | Find splits for an expense           |
| idx_expense_splits_participant| participant_user_id  | Find all splits for a participant    |

---

### attachments

Files attached to trips or individual expenses, stored in S3.

| Column      | Type          | Nullable | Default            | Description                       |
|-------------|---------------|----------|--------------------|-----------------------------------|
| id          | UUID          | No       | gen_random_uuid()  | Primary key                       |
| trip_id     | UUID          | No       |                    | FK -> trips(id)                   |
| expense_id  | UUID          | Yes      |                    | FK -> expenses(id), optional link |
| uploaded_by | UUID          | No       |                    | FK -> users(id)                   |
| file_name   | VARCHAR(255)  | No       |                    | Original file name                |
| file_size   | BIGINT        | No       |                    | File size in bytes                |
| mime_type   | VARCHAR(100)  | No       |                    | MIME type (e.g., image/jpeg)      |
| s3_key      | VARCHAR(500)  | No       |                    | S3 object key                     |
| created_at  | TIMESTAMPTZ   | No       | now()              | Upload time                       |
| deleted_at  | TIMESTAMPTZ   | Yes      |                    | Soft delete timestamp             |

**Constraints:** `FK trip_id -> trips(id) ON DELETE CASCADE`, `FK expense_id -> expenses(id) ON DELETE SET NULL`, `FK uploaded_by -> users(id)`

**Indexes:**

| Name                    | Columns    | Purpose                          |
|-------------------------|------------|----------------------------------|
| idx_attachments_trip    | trip_id    | List attachments for a trip      |
| idx_attachments_expense | expense_id | Find attachments for an expense  |

---

### idempotency_keys

Stores processed client mutation IDs to ensure idempotent writes from offline clients.

| Column          | Type         | Nullable | Default | Description                              |
|-----------------|--------------|----------|---------|------------------------------------------|
| key             | VARCHAR(255) | No       |         | Primary key (client mutation ID)         |
| user_id         | UUID         | No       |         | User who submitted the mutation          |
| response_status | INT          | No       |         | HTTP status code of the original response|
| response_body   | TEXT         | Yes      |         | Serialized response body                 |
| created_at      | TIMESTAMPTZ  | No       | now()   | When the mutation was first processed    |
| expires_at      | TIMESTAMPTZ  | No       |         | When this record can be cleaned up       |

**Indexes:**

| Name                    | Columns    | Purpose                     |
|-------------------------|------------|-----------------------------|
| idx_idempotency_expires | expires_at | Clean up expired entries    |
| idx_idempotency_user    | user_id    | Find keys by user           |

---

### domain_events

Outbox table for reliable event-driven notifications. Events are written transactionally alongside business data and processed asynchronously.

| Column         | Type          | Nullable | Default            | Description                       |
|----------------|---------------|----------|--------------------|-----------------------------------|
| id             | UUID          | No       | gen_random_uuid()  | Primary key                       |
| event_type     | VARCHAR(100)  | No       |                    | Event type (e.g., EXPENSE_CREATED)|
| aggregate_type | VARCHAR(50)   | No       |                    | Entity type (e.g., Trip)          |
| aggregate_id   | UUID          | No       |                    | Entity ID (typically trip ID)     |
| payload        | JSONB         | No       |                    | Event data as JSON                |
| created_at     | TIMESTAMPTZ   | No       | now()              | When the event was created        |
| processed_at   | TIMESTAMPTZ   | Yes      |                    | When the event was dispatched     |
| retry_count    | INT           | No       | 0                  | Number of delivery attempts       |

**Indexes:**

| Name                          | Columns                     | Condition              | Purpose                         |
|-------------------------------|-----------------------------|------------------------|---------------------------------|
| idx_domain_events_unprocessed | created_at                  | WHERE processed_at IS NULL | Efficiently find pending events |
| idx_domain_events_aggregate   | aggregate_type, aggregate_id |                        | Query events by entity          |

## Conventions

### Soft Deletes

The following tables support soft deletes via a `deleted_at` column:

- `trips`
- `itinerary_points`
- `expenses`
- `attachments`

When a record is soft-deleted, `deleted_at` is set to the current timestamp. Application queries filter out soft-deleted records (`WHERE deleted_at IS NULL`), but sync endpoints include them so that clients can remove entities from their local stores.

### Optimistic Locking

The following tables have a `version` column for optimistic concurrency control:

- `trips`
- `itinerary_points`
- `expenses`

When updating, the client sends `expectedVersion`. The server checks that the current version matches before applying the update. On mismatch, a `409 VERSION_CONFLICT` is returned.

The version is incremented by 1 on every successful update.

### Timestamps

All timestamps are stored as `TIMESTAMPTZ` (timestamp with time zone) in UTC. The application serializes them as ISO 8601 strings.

### Cascading Deletes

Foreign keys with `ON DELETE CASCADE`:

- `user_devices.user_id` -> When a user is deleted, their devices are removed
- `auth_refresh_tokens.user_id` -> When a user is deleted, their tokens are removed
- `trip_participants` -> When a trip is deleted, participant records are removed
- `trip_invitations.trip_id` -> When a trip is deleted, pending invitations are removed
- `itinerary_points.trip_id` -> When a trip is deleted, itinerary is removed
- `expenses.trip_id` -> When a trip is deleted, expenses are removed
- `expense_splits.expense_id` -> When an expense is deleted, splits are removed
- `attachments.trip_id` -> When a trip is deleted, attachments are removed

Special case: `attachments.expense_id` uses `ON DELETE SET NULL` -- if an expense is hard-deleted, the attachment remains but loses its expense association.
