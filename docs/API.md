# API Reference

Complete REST API reference for the Travel Planner backend.

## General Information

| Property       | Value                        |
|----------------|------------------------------|
| Base URL       | `/api/v1`                    |
| Protocol       | HTTP/HTTPS                   |
| Content-Type   | `application/json`           |
| Authentication | JWT Bearer token             |
| Date format    | ISO 8601 (`2025-06-15`)      |
| Datetime format| ISO 8601 (`2025-06-15T10:30:00Z`) |
| ID format      | UUID v4                      |

## Authentication

All endpoints except `/health/*`, `/api/v1/auth/register`, `/api/v1/auth/login`, and `/api/v1/auth/refresh` require a valid JWT access token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

## Error Response Format

All errors return a consistent JSON envelope:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable error description",
  "details": null,
  "traceId": null
}
```

### Common Error Codes

| HTTP Status | Code                     | Description                                 |
|-------------|--------------------------|---------------------------------------------|
| 400         | `BAD_REQUEST`            | Malformed request body or parameters        |
| 401         | `INVALID_CREDENTIALS`    | Wrong email or password                     |
| 401         | `INVALID_REFRESH_TOKEN`  | Refresh token is invalid or expired         |
| 401         | `TOKEN_EXPIRED`          | JWT access token has expired                |
| 403         | `ACCESS_DENIED`          | User lacks permission for this action       |
| 403         | `INSUFFICIENT_ROLE`      | User's role is too low for this operation   |
| 404         | `*_NOT_FOUND`            | Requested resource does not exist           |
| 409         | `EMAIL_ALREADY_EXISTS`   | Registration with duplicate email           |
| 409         | `VERSION_CONFLICT`       | Optimistic locking conflict                 |
| 409         | `IDEMPOTENCY_CONFLICT`   | Duplicate client mutation ID                |
| 409         | `ALREADY_PARTICIPANT`    | User is already a trip participant          |
| 422         | `VALIDATION_ERROR`       | Business rule validation failed             |
| 422         | `INVALID_SPLIT_SUM`      | Expense splits do not sum correctly         |
| 422         | `TRIP_NOT_ACTIVE`        | Operation not allowed on archived/completed trip |
| 500         | `INTERNAL_ERROR`         | Unexpected server error                     |

---

## Health

Health check endpoints do not require authentication.

### GET /health/live

Liveness probe. Returns `UP` if the process is running.

**Response** `200 OK`

```json
{
  "status": "UP",
  "timestamp": "2025-06-15T10:30:00Z"
}
```

### GET /health/ready

Readiness probe. Returns `UP` if the application is ready to accept traffic.

**Response** `200 OK`

```json
{
  "status": "UP",
  "timestamp": "2025-06-15T10:30:00Z"
}
```

---

## Auth

### POST /api/v1/auth/register

Register a new user account.

**Request Body**

```json
{
  "email": "alice@example.com",
  "displayName": "Alice Johnson",
  "password": "secureP@ss123"
}
```

**Response** `201 Created`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "user": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "email": "alice@example.com",
    "displayName": "Alice Johnson",
    "avatarUrl": null,
    "createdAt": "2025-06-15T10:30:00Z",
    "updatedAt": "2025-06-15T10:30:00Z"
  }
}
```

**Errors**

| Status | Code                   | Cause                      |
|--------|------------------------|----------------------------|
| 409    | `EMAIL_ALREADY_EXISTS` | Email is already registered |

### POST /api/v1/auth/login

Authenticate with email and password.

**Request Body**

```json
{
  "email": "alice@example.com",
  "password": "secureP@ss123"
}
```

**Response** `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "user": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "email": "alice@example.com",
    "displayName": "Alice Johnson",
    "avatarUrl": null,
    "createdAt": "2025-06-15T10:30:00Z",
    "updatedAt": "2025-06-15T10:30:00Z"
  }
}
```

**Errors**

| Status | Code                  | Cause                    |
|--------|-----------------------|--------------------------|
| 401    | `INVALID_CREDENTIALS` | Wrong email or password  |

### POST /api/v1/auth/refresh

Exchange a refresh token for a new access/refresh token pair.

**Request Body**

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response** `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "660f9500-f30c-52e5-b827-ff2345678901"
}
```

**Errors**

| Status | Code                    | Cause                           |
|--------|-------------------------|---------------------------------|
| 401    | `INVALID_REFRESH_TOKEN` | Token is invalid, expired, or revoked |

### POST /api/v1/auth/logout

Invalidate all refresh tokens for the current user. Requires authentication.

**Response** `200 OK`

```json
{
  "message": "Logged out successfully"
}
```

---

## Users

All user endpoints require authentication.

### GET /api/v1/me

Get the current user's profile.

**Response** `200 OK`

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "alice@example.com",
  "displayName": "Alice Johnson",
  "avatarUrl": "https://example.com/avatar.jpg",
  "createdAt": "2025-06-15T10:30:00Z",
  "updatedAt": "2025-06-15T10:30:00Z"
}
```

### POST /api/v1/me/devices

Register a device for push notifications.

**Request Body**

```json
{
  "fcmToken": "dGhpcyBpcyBhIGZjbSB0b2tlbg...",
  "deviceName": "Alice's iPhone"
}
```

**Response** `201 Created`

```json
{
  "id": "b2c3d4e5-f6a7-8901-bcde-f23456789012",
  "fcmToken": "dGhpcyBpcyBhIGZjbSB0b2tlbg...",
  "deviceName": "Alice's iPhone",
  "createdAt": "2025-06-15T10:30:00Z"
}
```

### DELETE /api/v1/me/devices/{deviceId}

Unregister a device from push notifications.

**Path Parameters**

| Name     | Type | Description |
|----------|------|-------------|
| deviceId | UUID | Device ID   |

**Response** `204 No Content`

**Errors**

| Status | Code             | Cause                      |
|--------|------------------|----------------------------|
| 404    | `DEVICE_NOT_FOUND` | Device does not exist or belongs to another user |

---

## Trips

All trip endpoints require authentication.

### GET /api/v1/trips

List all trips the current user participates in.

**Response** `200 OK`

```json
[
  {
    "id": "c3d4e5f6-a7b8-9012-cdef-345678901234",
    "title": "Japan 2025",
    "description": "Two weeks in Tokyo and Kyoto",
    "startDate": "2025-10-01",
    "endDate": "2025-10-14",
    "baseCurrency": "JPY",
    "status": "ACTIVE",
    "createdBy": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "createdAt": "2025-06-15T10:30:00Z",
    "updatedAt": "2025-06-15T10:30:00Z",
    "version": 1,
    "deletedAt": null
  }
]
```

### POST /api/v1/trips

Create a new trip. The creator is automatically added as an OWNER participant.

**Request Body**

```json
{
  "title": "Japan 2025",
  "description": "Two weeks in Tokyo and Kyoto",
  "startDate": "2025-10-01",
  "endDate": "2025-10-14",
  "baseCurrency": "JPY",
  "clientMutationId": "client-uuid-12345"
}
```

| Field            | Type   | Required | Default | Description                      |
|------------------|--------|----------|---------|----------------------------------|
| title            | string | Yes      |         | Trip title                       |
| description      | string | No       | null    | Trip description                 |
| startDate        | string | No       | null    | ISO 8601 date                    |
| endDate          | string | No       | null    | ISO 8601 date                    |
| baseCurrency     | string | No       | "USD"   | ISO 4217 currency code           |
| clientMutationId | string | No       | null    | Idempotency key for offline sync |

**Response** `201 Created`

```json
{
  "id": "c3d4e5f6-a7b8-9012-cdef-345678901234",
  "title": "Japan 2025",
  "description": "Two weeks in Tokyo and Kyoto",
  "startDate": "2025-10-01",
  "endDate": "2025-10-14",
  "baseCurrency": "JPY",
  "status": "ACTIVE",
  "createdBy": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "createdAt": "2025-06-15T10:30:00Z",
  "updatedAt": "2025-06-15T10:30:00Z",
  "version": 1,
  "deletedAt": null
}
```

### GET /api/v1/trips/{tripId}

Get a single trip by ID. The current user must be a participant.

**Path Parameters**

| Name   | Type | Description |
|--------|------|-------------|
| tripId | UUID | Trip ID     |

**Response** `200 OK` -- Same shape as trip object above.

**Errors**

| Status | Code             | Cause                          |
|--------|------------------|--------------------------------|
| 404    | `TRIP_NOT_FOUND` | Trip does not exist            |
| 403    | `ACCESS_DENIED`  | User is not a participant      |

### PATCH /api/v1/trips/{tripId}

Update a trip. Supports partial updates (only send fields you want to change).

**Request Body**

```json
{
  "title": "Japan & Korea 2025",
  "endDate": "2025-10-21",
  "expectedVersion": 1,
  "clientMutationId": "client-uuid-67890"
}
```

| Field           | Type   | Required | Description                                      |
|-----------------|--------|----------|--------------------------------------------------|
| title           | string | No       | New title                                        |
| description     | string | No       | New description                                  |
| startDate       | string | No       | New start date                                   |
| endDate         | string | No       | New end date                                     |
| baseCurrency    | string | No       | New base currency                                |
| status          | string | No       | ACTIVE, ARCHIVED, or COMPLETED                   |
| expectedVersion | long   | No       | For optimistic locking (default 0 = skip check)  |
| clientMutationId| string | No       | Idempotency key                                  |

**Response** `200 OK` -- Updated trip object.

**Errors**

| Status | Code               | Cause                              |
|--------|--------------------|------------------------------------|
| 409    | `VERSION_CONFLICT` | expectedVersion does not match     |

### DELETE /api/v1/trips/{tripId}

Soft-delete a trip. Only the OWNER can delete.

**Response** `204 No Content`

**Errors**

| Status | Code              | Cause                       |
|--------|-------------------|-----------------------------|
| 403    | `ACCESS_DENIED`   | User is not the trip owner  |

### POST /api/v1/trips/{tripId}/archive

Archive a trip. Sets status to ARCHIVED.

**Response** `200 OK` -- Updated trip object with `status: "ARCHIVED"`.

---

## Participants

All participant endpoints require authentication.

### GET /api/v1/trips/{tripId}/participants

List all participants of a trip with user details.

**Response** `200 OK`

```json
[
  {
    "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "email": "alice@example.com",
    "displayName": "Alice Johnson",
    "avatarUrl": "https://example.com/avatar.jpg",
    "role": "OWNER",
    "joinedAt": "2025-06-15T10:30:00Z"
  },
  {
    "userId": "d4e5f6a7-b8c9-0123-defg-456789012345",
    "email": "bob@example.com",
    "displayName": "Bob Smith",
    "avatarUrl": null,
    "role": "EDITOR",
    "joinedAt": "2025-06-16T08:00:00Z"
  }
]
```

### POST /api/v1/trips/{tripId}/participants/invite

Invite a user to the trip by email. Only the OWNER can invite.

**Request Body**

```json
{
  "email": "bob@example.com",
  "role": "EDITOR",
  "clientMutationId": "client-uuid-11111"
}
```

| Field            | Type   | Required | Default  | Description          |
|------------------|--------|----------|----------|----------------------|
| email            | string | Yes      |          | Invitee email        |
| role             | string | No       | "EDITOR" | EDITOR or VIEWER     |
| clientMutationId | string | No       | null     | Idempotency key      |

**Response** `201 Created`

```json
{
  "id": "e5f6a7b8-c9d0-1234-efab-567890123456",
  "tripId": "c3d4e5f6-a7b8-9012-cdef-345678901234",
  "email": "bob@example.com",
  "role": "EDITOR",
  "status": "PENDING",
  "createdAt": "2025-06-15T10:30:00Z"
}
```

**Errors**

| Status | Code                  | Cause                            |
|--------|-----------------------|----------------------------------|
| 403    | `INSUFFICIENT_ROLE`   | Only OWNER can invite            |
| 409    | `ALREADY_PARTICIPANT` | User is already in the trip      |

### POST /api/v1/trip-invitations/{invitationId}/accept

Accept a trip invitation. The authenticated user must match the invitation email.

**Path Parameters**

| Name         | Type | Description   |
|--------------|------|---------------|
| invitationId | UUID | Invitation ID |

**Response** `200 OK`

```json
{
  "tripId": "c3d4e5f6-a7b8-9012-cdef-345678901234",
  "userId": "d4e5f6a7-b8c9-0123-defg-456789012345",
  "role": "EDITOR",
  "joinedAt": "2025-06-16T08:00:00Z"
}
```

**Errors**

| Status | Code                          | Cause                                |
|--------|-------------------------------|--------------------------------------|
| 404    | `INVITATION_NOT_FOUND`        | Invitation does not exist            |
| 409    | `INVITATION_ALREADY_RESOLVED` | Invitation was already accepted/declined |

### DELETE /api/v1/trips/{tripId}/participants/{userId}

Remove a participant from the trip. OWNER can remove anyone; participants can remove themselves.

**Response** `204 No Content`

### PATCH /api/v1/trips/{tripId}/participants/{userId}

Change a participant's role. Only the OWNER can change roles.

**Request Body**

```json
{
  "role": "VIEWER",
  "clientMutationId": "client-uuid-22222"
}
```

**Response** `200 OK`

```json
{
  "message": "Role updated"
}
```

---

## Itinerary

All itinerary endpoints require authentication and trip participation.

### GET /api/v1/trips/{tripId}/itinerary

List all itinerary points for a trip, sorted by `sortOrder`. Soft-deleted points are excluded.

**Response** `200 OK`

```json
[
  {
    "id": "f6a7b8c9-d0e1-2345-fabc-678901234567",
    "tripId": "c3d4e5f6-a7b8-9012-cdef-345678901234",
    "title": "Visit Senso-ji Temple",
    "description": "Historic Buddhist temple in Asakusa",
    "type": "ATTRACTION",
    "date": "2025-10-02",
    "startTime": "09:00",
    "endTime": "11:00",
    "latitude": 35.7148,
    "longitude": 139.7967,
    "address": "2 Chome-3-1 Asakusa, Taito City, Tokyo",
    "sortOrder": 0,
    "createdBy": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "createdAt": "2025-06-15T10:30:00Z",
    "updatedAt": "2025-06-15T10:30:00Z",
    "version": 1,
    "deletedAt": null
  }
]
```

### POST /api/v1/trips/{tripId}/itinerary

Create a new itinerary point.

**Request Body**

```json
{
  "title": "Visit Senso-ji Temple",
  "description": "Historic Buddhist temple in Asakusa",
  "type": "ATTRACTION",
  "date": "2025-10-02",
  "startTime": "09:00",
  "endTime": "11:00",
  "latitude": 35.7148,
  "longitude": 139.7967,
  "address": "2 Chome-3-1 Asakusa, Taito City, Tokyo",
  "clientMutationId": "client-uuid-33333"
}
```

| Field            | Type   | Required | Description                      |
|------------------|--------|----------|----------------------------------|
| title            | string | Yes      | Point title                      |
| description      | string | No       | Description                      |
| type             | string | No       | Category (ATTRACTION, FOOD, etc.)|
| date             | string | No       | ISO 8601 date                    |
| startTime        | string | No       | HH:mm format                     |
| endTime          | string | No       | HH:mm format                     |
| latitude         | double | No       | GPS latitude                     |
| longitude        | double | No       | GPS longitude                    |
| address          | string | No       | Human-readable address           |
| clientMutationId | string | No       | Idempotency key                  |

**Response** `201 Created` -- Itinerary point object.

### PATCH /api/v1/trips/{tripId}/itinerary/{pointId}

Update an itinerary point. Supports partial updates.

**Request Body**

```json
{
  "title": "Visit Senso-ji Temple (Updated)",
  "startTime": "10:00",
  "expectedVersion": 1,
  "clientMutationId": "client-uuid-44444"
}
```

**Response** `200 OK` -- Updated itinerary point object.

**Errors**

| Status | Code               | Cause                                    |
|--------|--------------------|--------------------------------------------|
| 404    | `ITINERARY_POINT_NOT_FOUND` | Point does not exist              |
| 409    | `VERSION_CONFLICT` | expectedVersion does not match current     |

### DELETE /api/v1/trips/{tripId}/itinerary/{pointId}

Soft-delete an itinerary point.

**Response** `204 No Content`

### POST /api/v1/trips/{tripId}/itinerary/reorder

Reorder itinerary points by providing new sort orders.

**Request Body**

```json
{
  "items": [
    { "id": "f6a7b8c9-d0e1-2345-fabc-678901234567", "sortOrder": 1 },
    { "id": "a7b8c9d0-e1f2-3456-abcd-789012345678", "sortOrder": 0 }
  ],
  "clientMutationId": "client-uuid-55555"
}
```

**Response** `200 OK`

```json
{
  "message": "Reorder successful"
}
```

---

## Expenses

All expense endpoints require authentication and trip participation.

### GET /api/v1/trips/{tripId}/expenses

List expenses for a trip. Supports optional filters via query parameters.

**Query Parameters**

| Name        | Type   | Description                           |
|-------------|--------|---------------------------------------|
| category    | string | Filter by expense category            |
| payerUserId | UUID   | Filter by who paid                    |
| dateFrom    | string | Filter expenses on or after this date |
| dateTo      | string | Filter expenses on or before this date|

**Response** `200 OK`

```json
[
  {
    "id": "b8c9d0e1-f2a3-4567-bcde-890123456789",
    "tripId": "c3d4e5f6-a7b8-9012-cdef-345678901234",
    "payerUserId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "title": "Group dinner at Ichiran",
    "description": "Ramen dinner for everyone",
    "amount": "12000.00",
    "currency": "JPY",
    "category": "FOOD",
    "expenseDate": "2025-10-02",
    "splitType": "EQUAL",
    "splits": [
      {
        "id": "c9d0e1f2-a3b4-5678-cdef-901234567890",
        "participantUserId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "shareType": "EQUAL",
        "value": "1",
        "amountInExpenseCurrency": "6000.00"
      },
      {
        "id": "d0e1f2a3-b4c5-6789-defa-012345678901",
        "participantUserId": "d4e5f6a7-b8c9-0123-defg-456789012345",
        "shareType": "EQUAL",
        "value": "1",
        "amountInExpenseCurrency": "6000.00"
      }
    ],
    "createdBy": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "createdAt": "2025-10-02T19:00:00Z",
    "updatedAt": "2025-10-02T19:00:00Z",
    "version": 1,
    "deletedAt": null
  }
]
```

### POST /api/v1/trips/{tripId}/expenses

Create a new expense with splits.

**Request Body**

```json
{
  "title": "Group dinner at Ichiran",
  "description": "Ramen dinner for everyone",
  "amount": "12000.00",
  "currency": "JPY",
  "category": "FOOD",
  "payerUserId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "expenseDate": "2025-10-02",
  "splitType": "EQUAL",
  "splits": [
    { "participantUserId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "value": "1" },
    { "participantUserId": "d4e5f6a7-b8c9-0123-defg-456789012345", "value": "1" }
  ],
  "clientMutationId": "client-uuid-66666"
}
```

| Field            | Type   | Required | Default | Description                           |
|------------------|--------|----------|---------|---------------------------------------|
| title            | string | Yes      |         | Expense title                         |
| description      | string | No       | null    | Expense description                   |
| amount           | string | Yes      |         | Decimal amount as string              |
| currency         | string | Yes      |         | ISO 4217 currency code                |
| category         | string | Yes      |         | Expense category (FOOD, TRANSPORT, etc.) |
| payerUserId      | UUID   | Yes      |         | Who paid                              |
| expenseDate      | string | Yes      |         | ISO 8601 date                         |
| splitType        | string | No       | "EQUAL" | EQUAL, PERCENTAGE, SHARES, or EXACT_AMOUNT |
| splits           | array  | Yes      |         | How to divide the expense             |
| clientMutationId | string | No       | null    | Idempotency key                       |

**Split Types**

| Type          | `value` field meaning                              |
|---------------|----------------------------------------------------|
| EQUAL         | Ignored (set to any value, typically "0")          |
| PERCENTAGE    | Percentage share (must sum to 100)                 |
| SHARES        | Relative share count (proportional distribution)   |
| EXACT_AMOUNT  | Exact amount in expense currency (must sum to total)|

**Response** `201 Created` -- Expense object with splits.

**Errors**

| Status | Code                     | Cause                                 |
|--------|--------------------------|---------------------------------------|
| 422    | `INVALID_SPLIT_SUM`     | Split amounts do not add up correctly |
| 422    | `PARTICIPANT_NOT_IN_TRIP`| A split references a non-participant  |
| 422    | `VALIDATION_ERROR`       | Invalid amount, empty splits, etc.    |

### GET /api/v1/trips/{tripId}/expenses/{expenseId}

Get a single expense by ID.

**Response** `200 OK` -- Expense object with splits.

### PATCH /api/v1/trips/{tripId}/expenses/{expenseId}

Update an expense. Supports partial updates. If `splits` is provided, all existing splits are replaced.

**Request Body**

```json
{
  "amount": "15000.00",
  "splits": [
    { "participantUserId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "value": "1" },
    { "participantUserId": "d4e5f6a7-b8c9-0123-defg-456789012345", "value": "1" }
  ],
  "expectedVersion": 1,
  "clientMutationId": "client-uuid-77777"
}
```

**Response** `200 OK` -- Updated expense object with splits.

### DELETE /api/v1/trips/{tripId}/expenses/{expenseId}

Soft-delete an expense.

**Response** `204 No Content`

---

## Analytics

All analytics endpoints require authentication and trip participation.

### GET /api/v1/trips/{tripId}/balances

Calculate balances for all participants in the trip. Shows how much each person has paid, how much they owe, and their net balance.

**Response** `200 OK`

```json
[
  {
    "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "totalPaid": "12000.00",
    "totalOwed": "6000.00",
    "netBalance": "6000.00"
  },
  {
    "userId": "d4e5f6a7-b8c9-0123-defg-456789012345",
    "totalPaid": "0.00",
    "totalOwed": "6000.00",
    "netBalance": "-6000.00"
  }
]
```

### GET /api/v1/trips/{tripId}/settlements

Calculate optimized settlement payments to balance all debts with the minimum number of transfers.

**Response** `200 OK`

```json
[
  {
    "fromUserId": "d4e5f6a7-b8c9-0123-defg-456789012345",
    "toUserId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "amount": "6000.00",
    "currency": "JPY"
  }
]
```

### GET /api/v1/trips/{tripId}/statistics

Get spending statistics for the trip, broken down by category, participant, and day.

**Response** `200 OK`

```json
{
  "totalSpent": "45000.00",
  "currency": "JPY",
  "spentByCategory": {
    "FOOD": "20000.00",
    "TRANSPORT": "15000.00",
    "ACCOMMODATION": "10000.00"
  },
  "spentByParticipant": {
    "a1b2c3d4-e5f6-7890-abcd-ef1234567890": "30000.00",
    "d4e5f6a7-b8c9-0123-defg-456789012345": "15000.00"
  },
  "spentByDay": {
    "2025-10-01": "10000.00",
    "2025-10-02": "20000.00",
    "2025-10-03": "15000.00"
  }
}
```

---

## Attachments

All attachment endpoints require authentication.

### POST /api/v1/attachments/presign

Request a presigned S3 upload URL. The client uses this URL to upload the file directly to object storage.

**Request Body**

```json
{
  "fileName": "receipt.jpg",
  "contentType": "image/jpeg",
  "fileSize": 245000,
  "tripId": "c3d4e5f6-a7b8-9012-cdef-345678901234"
}
```

**Response** `200 OK`

```json
{
  "uploadUrl": "https://minio:9000/travel-planner/trips/c3d4e5f6/receipt.jpg?X-Amz-...",
  "s3Key": "trips/c3d4e5f6-a7b8-9012-cdef-345678901234/receipt.jpg"
}
```

### POST /api/v1/trips/{tripId}/attachments

Record an attachment after the file has been uploaded to S3.

**Request Body**

```json
{
  "fileName": "receipt.jpg",
  "fileSize": 245000,
  "mimeType": "image/jpeg",
  "s3Key": "trips/c3d4e5f6-a7b8-9012-cdef-345678901234/receipt.jpg",
  "clientMutationId": "client-uuid-88888"
}
```

**Response** `201 Created`

```json
{
  "id": "e1f2a3b4-c5d6-7890-efab-123456789012",
  "tripId": "c3d4e5f6-a7b8-9012-cdef-345678901234",
  "expenseId": null,
  "uploadedBy": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fileName": "receipt.jpg",
  "fileSize": 245000,
  "mimeType": "image/jpeg",
  "s3Key": "trips/c3d4e5f6-a7b8-9012-cdef-345678901234/receipt.jpg",
  "createdAt": "2025-10-02T19:30:00Z"
}
```

### POST /api/v1/trips/{tripId}/expenses/{expenseId}/attachments

Record an attachment linked to a specific expense.

Same request/response format as above, but the `expenseId` field will be populated in the response.

### DELETE /api/v1/attachments/{attachmentId}

Delete an attachment.

**Response** `204 No Content`

---

## Sync

Sync endpoints enable offline-first mobile clients to stay synchronized with the server. See [SYNC_PROTOCOL.md](SYNC_PROTOCOL.md) for the full protocol description.

### GET /api/v1/trips/{tripId}/snapshot

Get a complete snapshot of the trip state. Used for initial sync or to recover from a corrupted local state.

**Response** `200 OK`

```json
{
  "trip": { ... },
  "participants": [ ... ],
  "itineraryPoints": [ ... ],
  "expenses": [ ... ],
  "attachments": [ ... ],
  "cursor": "2025-10-02T19:30:00Z"
}
```

The `cursor` value should be stored by the client and passed to the delta sync endpoint on subsequent requests.

### GET /api/v1/trips/{tripId}/sync?cursor={cursor}

Get changes since the last sync. Returns only entities that have been created, updated, or soft-deleted after the cursor timestamp.

**Query Parameters**

| Name   | Type   | Required | Description                                  |
|--------|--------|----------|----------------------------------------------|
| cursor | string | Yes      | ISO 8601 timestamp from the last sync cursor |

**Response** `200 OK`

```json
{
  "trips": [ ... ],
  "participants": [ ... ],
  "itineraryPoints": [ ... ],
  "expenses": [ ... ],
  "attachments": [ ... ],
  "cursor": "2025-10-02T20:00:00Z"
}
```

**Errors**

| Status | Code               | Cause                       |
|--------|--------------------|-----------------------------|
| 422    | `VALIDATION_ERROR` | Missing cursor parameter    |
