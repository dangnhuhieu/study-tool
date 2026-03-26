# Auth Service - Detail Design

## 1. Overview

Handles user registration, authentication (email/password + OAuth2), JWT token management, and role-based authorization.

- **Port**: 8081
- **Database**: PostgreSQL (`studytool_auth`)
- **Dependencies**: Spring Security, jjwt, OAuth2 Client

---

## 2. Data Model (ERD)

```
┌──────────────────────┐       ┌──────────────────────┐
│        users         │       │     refresh_tokens    │
├──────────────────────┤       ├──────────────────────┤
│ id          UUID  PK │───┐   │ id          UUID  PK │
│ email    VARCHAR UQ  │   │   │ user_id     UUID  FK │──→ users.id
│ password VARCHAR     │   │   │ token    VARCHAR UQ   │
│ name     VARCHAR     │   │   │ expires_at TIMESTAMP  │
│ provider VARCHAR     │   │   │ created_at TIMESTAMP  │
│ provider_id VARCHAR  │   │   └──────────────────────┘
│ role     VARCHAR     │   │
│ enabled  BOOLEAN     │   │
│ created_at TIMESTAMP │   │
│ updated_at TIMESTAMP │   │
└──────────────────────┘   │
                           │
```

### Roles
- `ROLE_USER` — Default for new users
- `ROLE_ADMIN` — Full access, user management, no rate limits

### Provider
- `LOCAL` — email/password registration
- `GOOGLE` — Google OAuth2
- `GITHUB` — GitHub OAuth2

---

## 3. Authentication Flows

### 3.1 Registration (Local)
```
Client                    Auth Service              Database
  │ POST /api/auth/register  │                        │
  │──────────────────────────→│                        │
  │  {email, password, name}  │ Validate + hash pwd   │
  │                           │────────────────────────→│
  │                           │   Save user (LOCAL)    │
  │                           │←────────────────────────│
  │   {accessToken, refresh}  │                        │
  │←──────────────────────────│                        │
```

### 3.2 Login (Local)
```
Client                    Auth Service              Database
  │ POST /api/auth/login     │                        │
  │──────────────────────────→│                        │
  │  {email, password}        │ Find user by email    │
  │                           │────────────────────────→│
  │                           │ Verify BCrypt hash     │
  │                           │ Generate JWT + refresh │
  │   {accessToken, refresh}  │                        │
  │←──────────────────────────│                        │
```

### 3.3 OAuth2 Flow (Google/GitHub)
```
Client → /api/auth/oauth2/google → Google consent → callback → Auth Service
  → Find or create user (provider=GOOGLE) → Generate JWT → Redirect to frontend with tokens
```

### 3.4 Token Refresh
```
Client                    Auth Service
  │ POST /api/auth/refresh   │
  │  {refreshToken}          │
  │──────────────────────────→│
  │                           │ Validate refresh token
  │                           │ Generate new access + refresh
  │                           │ Revoke old refresh token
  │   {accessToken, refresh}  │
  │←──────────────────────────│
```

---

## 4. JWT Structure

### Access Token Claims
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "name": "John Doe",
  "roles": ["ROLE_USER"],
  "iat": 1700000000,
  "exp": 1700086400
}
```

| Token Type | Expiration | Storage |
|-----------|-----------|---------|
| Access Token | 24 hours | Client memory (not localStorage) |
| Refresh Token | 7 days | HttpOnly cookie or secure storage |

### Signing
- Algorithm: HMAC-SHA256
- Secret: Configurable via `JWT_SECRET` env var

---

## 5. Password Security

- **Hashing**: BCrypt (strength 10)
- **Rules**: Minimum 8 characters (enforced via `@Size` validation)

---

## 6. API Endpoints

### POST /api/auth/register
**Request:**
```json
{
  "email": "user@example.com",
  "password": "securepass123",
  "name": "John Doe"
}
```
**Response (201):**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d4f5a6b7-...",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

### POST /api/auth/login
**Request:**
```json
{
  "email": "user@example.com",
  "password": "securepass123"
}
```
**Response (200):** Same as register response.

**Error (401):**
```json
{
  "success": false,
  "message": "Invalid email or password"
}
```

### POST /api/auth/refresh
**Request:**
```json
{
  "refreshToken": "d4f5a6b7-..."
}
```
**Response (200):** New access + refresh tokens.

### POST /api/auth/logout
**Headers:** `Authorization: Bearer <token>`
**Response (200):**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

### GET /api/auth/me
**Headers:** `Authorization: Bearer <token>`
**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-...",
    "email": "user@example.com",
    "name": "John Doe",
    "role": "ROLE_USER",
    "provider": "LOCAL",
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

### GET /api/auth/oauth2/{provider}
Redirects to Google/GitHub consent page.

### GET /api/auth/oauth2/callback/{provider}
Handles OAuth2 callback, creates/updates user, redirects to frontend with tokens.

---

## 7. Error Responses

| Status | Message |
|--------|---------|
| 400 | Validation failed (field errors) |
| 401 | Invalid email or password |
| 401 | Invalid or expired token |
| 409 | Email already registered |
| 500 | Internal server error |

---

## 8. Database Schema

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    provider_id VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```
