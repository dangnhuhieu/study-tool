# API Gateway - Detail Design

## 1. Overview

The API Gateway is a Spring Cloud Gateway service that serves as the single entry point for all client requests. It handles routing, JWT validation, rate limiting, CORS, and request/response transformation.

- **Port**: 8080
- **Framework**: Spring Cloud Gateway (reactive/WebFlux)
- **Dependencies**: Redis (rate limiting), auth-service (JWT validation)

---

## 2. Route Table

| Route ID | Path Pattern | Target | Port | Methods | Auth Required |
|----------|-------------|--------|------|---------|---------------|
| auth-service | `/api/auth/**` | auth-service | 8081 | ALL | No (public) |
| chat-service | `/api/chat/**` | chat-service | 8082 | ALL | Yes |
| chat-ws | `/ws/chat/**` | chat-service | 8082 | WebSocket | Yes |
| quiz-service | `/api/quiz/**` | quiz-service | 8083 | ALL | Yes |
| vocabulary | `/api/vocabulary/**` | quiz-service | 8083 | ALL | Yes |
| ai-orchestrator | `/api/ai/**` | ai-orchestrator | 8084 | ALL | Yes |

### Public Endpoints (no JWT required)
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/oauth2/**`
- `GET /actuator/health`

---

## 3. JWT Filter Chain

```
Request → CORS Filter → JWT Auth Filter → Rate Limit Filter → Route Handler
```

### JWT Auth Filter Logic
1. Extract `Authorization: Bearer <token>` header
2. Skip validation for public endpoints (whitelist)
3. Parse JWT using shared secret (HMAC-SHA256)
4. Validate: expiration, signature, issuer
5. Extract claims: `sub` (userId), `roles` (ADMIN/USER)
6. Propagate headers downstream:
   - `X-User-Id: <userId>`
   - `X-User-Roles: <roles>`
7. Reject with `401 Unauthorized` if invalid

### Response on Invalid Token
```json
{
  "success": false,
  "message": "Invalid or expired token"
}
```

---

## 4. Rate Limiting

**Backend**: Redis (token bucket algorithm via Spring Cloud Gateway `RequestRateLimiter`)

| Role | Requests/second | Burst capacity |
|------|----------------|----------------|
| USER | 10 | 20 |
| ADMIN | 50 | 100 |
| Anonymous | 5 | 10 |

### Response Headers
```
X-RateLimit-Remaining: 15
X-RateLimit-Limit: 20
X-RateLimit-Reset: 1700000000
```

### Rate Limit Exceeded Response (429)
```json
{
  "success": false,
  "message": "Rate limit exceeded. Try again later."
}
```

---

## 5. CORS Configuration

| Property | Value |
|----------|-------|
| Allowed Origins | `http://localhost:3000`, `https://<production-domain>` |
| Allowed Methods | GET, POST, PUT, DELETE, PATCH, OPTIONS |
| Allowed Headers | Authorization, Content-Type, X-Requested-With |
| Exposed Headers | X-RateLimit-Remaining, X-RateLimit-Limit |
| Allow Credentials | true |
| Max Age | 3600 seconds |

---

## 6. Error Handling

| Scenario | Status | Response |
|----------|--------|----------|
| Service unavailable | 503 | `{"success": false, "message": "Service temporarily unavailable"}` |
| Route not found | 404 | `{"success": false, "message": "Endpoint not found"}` |
| JWT invalid | 401 | `{"success": false, "message": "Invalid or expired token"}` |
| Rate limited | 429 | `{"success": false, "message": "Rate limit exceeded"}` |
| Gateway timeout | 504 | `{"success": false, "message": "Request timeout"}` |

### Timeout Configuration
- Connect timeout: 5s
- Response timeout: 30s (longer for AI endpoints: 120s)

---

## 7. Health Check

- **Endpoint**: `GET /actuator/health`
- Reports gateway status and downstream service connectivity
- Used by Kubernetes liveness/readiness probes

---

## 8. Configuration

```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "${CORS_ORIGINS:http://localhost:3000}"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

jwt:
  secret: ${JWT_SECRET:change-me}
```
