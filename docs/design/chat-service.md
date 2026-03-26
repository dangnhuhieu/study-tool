# Chat Service - Detail Design

## 1. Overview

Core service managing chat conversations, messages, real-time streaming via WebSocket/SSE, and user progress tracking. Acts as the primary interface between the frontend and the AI orchestrator.

- **Port**: 8082
- **Database**: PostgreSQL (`studytool_chat`)
- **Messaging**: RabbitMQ (events)
- **Real-time**: WebSocket + SSE

---

## 2. Data Model (ERD)

```
┌────────────────────────┐
│     conversations      │
├────────────────────────┤
│ id           UUID   PK │───┐
│ user_id      UUID      │   │
│ title        VARCHAR   │   │
│ created_at   TIMESTAMP │   │
│ updated_at   TIMESTAMP │   │
└────────────────────────┘   │
                             │
┌────────────────────────┐   │
│       messages         │   │
├────────────────────────┤   │
│ id           UUID   PK │   │
│ conversation_id UUID FK│───┘
│ role         VARCHAR   │  (USER / ASSISTANT / SYSTEM)
│ content      TEXT      │
│ a2ui_payload JSONB     │  (nullable - A2UI components)
│ created_at   TIMESTAMP │
└────────────────────────┘

┌────────────────────────┐
│    user_progress       │
├────────────────────────┤
│ id           UUID   PK │
│ user_id      UUID      │
│ metric_type  VARCHAR   │  (QUIZ_SCORE, VOCAB_MASTERY, PRONUNCIATION, SHADOWING)
│ metric_value JSONB     │
│ recorded_at  TIMESTAMP │
└────────────────────────┘
```

---

## 3. WebSocket/SSE Protocol

### Connection
```
Client connects: ws://localhost:8080/ws/chat?token=<JWT>
```

### Client → Server Message
```json
{
  "type": "USER_MESSAGE",
  "conversationId": "550e8400-...",
  "content": "Generate a quiz from my uploaded document"
}
```

### Server → Client Messages

**Streaming token:**
```json
{
  "type": "STREAM_TOKEN",
  "conversationId": "550e8400-...",
  "token": "Here"
}
```

**Stream complete with A2UI:**
```json
{
  "type": "STREAM_END",
  "conversationId": "550e8400-...",
  "messageId": "660e8400-...",
  "content": "Here's your quiz based on the document:",
  "a2uiPayload": {
    "components": [
      {
        "type": "quiz-form",
        "id": "quiz-1",
        "props": { "questions": [...] }
      }
    ]
  }
}
```

**Error:**
```json
{
  "type": "ERROR",
  "message": "Failed to process request"
}
```

### Heartbeat
- Client sends `PING` every 30s
- Server responds with `PONG`
- Disconnect after 3 missed heartbeats

---

## 4. Message Lifecycle

```
1. User sends message via WebSocket
2. chat-service saves user message to DB
3. chat-service calls ai-orchestrator (POST /api/ai/chat)
4. ai-orchestrator processes (tool calls, Claude API)
5. Response streamed back (SSE from orchestrator → WebSocket to client)
6. chat-service saves assistant message (with optional A2UI payload)
7. A2UI components rendered by frontend
```

---

## 5. A2UI Message Format

Messages with interactive UI embed the `a2ui_payload` JSONB column:
```json
{
  "components": [
    {
      "type": "quiz-form",
      "id": "quiz-abc123",
      "props": {
        "quizId": "q-123",
        "questions": [
          {
            "id": "q1",
            "type": "mcq",
            "text": "What is the capital of France?",
            "options": ["London", "Paris", "Berlin", "Madrid"]
          }
        ]
      }
    }
  ]
}
```

---

## 6. Progress Tracking

### Metric Types
| Type | Value Structure | Triggered By |
|------|----------------|-------------|
| QUIZ_SCORE | `{"quizId": "...", "score": 85, "total": 100}` | Quiz submission |
| VOCAB_MASTERY | `{"wordId": "...", "level": 3, "interval": 7}` | Vocabulary review |
| PRONUNCIATION | `{"score": 72, "text": "..."}` | Pronunciation eval |
| SHADOWING | `{"duration": 300, "materialId": "..."}` | Shadowing session |

### Aggregation Queries
- `GET /api/chat/progress/summary` — Overall stats per metric type
- `GET /api/chat/progress/history?type=QUIZ_SCORE&days=30` — Time series

---

## 7. RabbitMQ Events

### Published
| Event | Exchange | Routing Key | When |
|-------|----------|-------------|------|
| `progress.updated` | `study-tool` | `progress.updated` | After any progress metric is saved |

### Consumed
| Event | Queue | Action |
|-------|-------|--------|
| `quiz.completed` | `chat.quiz.completed` | Save QUIZ_SCORE progress |
| `vocab.reviewed` | `chat.vocab.reviewed` | Save VOCAB_MASTERY progress |

---

## 8. API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/chat/conversations` | Yes | Create new conversation |
| GET | `/api/chat/conversations` | Yes | List user's conversations (paginated) |
| GET | `/api/chat/conversations/{id}` | Yes | Get conversation with messages |
| DELETE | `/api/chat/conversations/{id}` | Yes | Delete conversation |
| GET | `/api/chat/conversations/{id}/messages` | Yes | Get messages (paginated, cursor-based) |
| POST | `/api/chat/conversations/{id}/messages` | Yes | Send message (REST fallback) |
| WS | `/ws/chat` | Yes (token param) | WebSocket for real-time chat |
| GET | `/api/chat/progress/summary` | Yes | Progress summary |
| GET | `/api/chat/progress/history` | Yes | Progress time series |

### POST /api/chat/conversations
**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-...",
    "title": "New Conversation",
    "createdAt": "2024-01-01T00:00:00Z"
  }
}
```

### GET /api/chat/conversations/{id}/messages?cursor=&limit=20
**Response (200):**
```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "id": "660e8400-...",
        "role": "USER",
        "content": "Generate a quiz",
        "a2uiPayload": null,
        "createdAt": "2024-01-01T00:00:00Z"
      },
      {
        "id": "770e8400-...",
        "role": "ASSISTANT",
        "content": "Here's your quiz:",
        "a2uiPayload": { "components": [...] },
        "createdAt": "2024-01-01T00:00:01Z"
      }
    ],
    "nextCursor": "770e8400-...",
    "hasMore": true
  }
}
```

---

## 9. Database Schema

```sql
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL DEFAULT 'New Conversation',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    a2ui_payload JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE user_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    metric_type VARCHAR(50) NOT NULL,
    metric_value JSONB NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_conversations_user_id ON conversations(user_id);
CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_messages_created_at ON messages(created_at);
CREATE INDEX idx_user_progress_user_type ON user_progress(user_id, metric_type);
CREATE INDEX idx_user_progress_recorded_at ON user_progress(recorded_at);
```
