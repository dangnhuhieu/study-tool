# Quiz Service - Detail Design

## 1. Overview

Manages quizzes, questions, attempts, vocabulary, document uploads, and spaced repetition scheduling. Central service for all learning content and progress data.

- **Port**: 8083
- **Database**: PostgreSQL (`studytool_quiz`)
- **Messaging**: RabbitMQ
- **Max upload**: 50MB

---

## 2. Data Model (ERD)

```
┌─────────────────────┐     ┌─────────────────────┐
│     documents       │     │       quizzes        │
├─────────────────────┤     ├─────────────────────┤
│ id        UUID   PK │──┐  │ id        UUID   PK │
│ user_id   UUID      │  │  │ user_id   UUID      │
│ filename  VARCHAR   │  │  │ document_id UUID FK  │──→ documents.id
│ mime_type VARCHAR   │  │  │ title     VARCHAR    │
│ size_bytes BIGINT   │  │  │ type      VARCHAR    │ (DOCUMENT, VOCABULARY, CUSTOM)
│ status    VARCHAR   │  │  │ difficulty VARCHAR   │ (EASY, MEDIUM, HARD)
│ created_at TIMESTAMP│  │  │ created_at TIMESTAMP │
└─────────────────────┘  │  └─────────────────────┘
                         │           │
                         │  ┌─────────────────────┐
                         │  │     questions        │
                         │  ├─────────────────────┤
                         │  │ id        UUID   PK │
                         │  │ quiz_id   UUID   FK │──→ quizzes.id
                         │  │ type      VARCHAR   │ (MCQ, FILL_BLANK, TRUE_FALSE)
                         │  │ text      TEXT      │
                         │  │ options   JSONB     │ (for MCQ)
                         │  │ correct_answer TEXT  │
                         │  │ explanation TEXT     │
                         │  │ order_index INT     │
                         │  └─────────────────────┘
                         │           │
┌─────────────────────┐  │  ┌─────────────────────┐
│   quiz_attempts     │  │  │ question_responses   │
├─────────────────────┤  │  ├─────────────────────┤
│ id        UUID   PK │  │  │ id        UUID   PK │
│ quiz_id   UUID   FK │──┘  │ attempt_id UUID  FK │──→ quiz_attempts.id
│ user_id   UUID      │     │ question_id UUID FK │──→ questions.id
│ score     INT       │     │ user_answer TEXT     │
│ total     INT       │     │ is_correct BOOLEAN   │
│ started_at TIMESTAMP│     │ feedback   TEXT      │
│ completed_at TIMESTAMP│   └─────────────────────┘
└─────────────────────┘

┌─────────────────────┐
│    vocabulary       │
├─────────────────────┤
│ id        UUID   PK │
│ user_id   UUID      │
│ word      VARCHAR   │
│ meaning   TEXT      │
│ context   TEXT      │ (sentence where the word was found)
│ mastery_level INT   │ (0-5, SM-2 scale)
│ ease_factor FLOAT   │ (SM-2, default 2.5)
│ interval  INT       │ (days until next review)
│ next_review_at DATE │
│ created_at TIMESTAMP│
│ updated_at TIMESTAMP│
└─────────────────────┘
```

---

## 3. Quiz Types

### MCQ (Multiple Choice)
```json
{
  "type": "MCQ",
  "text": "What is the capital of France?",
  "options": ["London", "Paris", "Berlin", "Madrid"],
  "correct_answer": "Paris"
}
```

### FILL_BLANK
```json
{
  "type": "FILL_BLANK",
  "text": "The ___ is the powerhouse of the cell.",
  "correct_answer": "mitochondria"
}
```

### TRUE_FALSE
```json
{
  "type": "TRUE_FALSE",
  "text": "The Earth is flat.",
  "correct_answer": "false"
}
```

---

## 4. Quiz Generation Flow

```
1. User uploads document via POST /api/quiz/documents
2. quiz-service saves document metadata (status=PROCESSING)
3. Publishes "document.uploaded" to RabbitMQ with file bytes
4. embedding-service consumes → parse → chunk → embed → stores vectors
5. Publishes "document.processed" when done
6. quiz-service updates document status=READY
7. User requests quiz: POST /api/quiz/generate
8. quiz-service calls ai-orchestrator with document ID
9. ai-orchestrator retrieves RAG context from embedding-service
10. Claude generates structured quiz JSON
11. quiz-service saves quiz + questions to DB
12. Returns quiz to user via A2UI component
```

---

## 5. Scoring Logic

- **MCQ**: Exact match → 1 point, wrong → 0
- **FILL_BLANK**: Sent to evaluation-service for fuzzy matching (synonyms, partial credit 0.0-1.0)
- **TRUE_FALSE**: Exact match → 1 point, wrong → 0
- **Score**: `(sum of points / total questions) * 100`

---

## 6. Spaced Repetition (SM-2)

Algorithm for vocabulary review scheduling:

```
Input: quality (0-5 rating of recall)

if quality >= 3 (correct):
    if repetition == 0: interval = 1
    elif repetition == 1: interval = 6
    else: interval = round(interval * ease_factor)
    ease_factor = max(1.3, ease_factor + 0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
else (incorrect):
    interval = 1
    (ease_factor unchanged)

next_review_at = today + interval days
```

### Mastery Levels
| Level | Name | Interval Range |
|-------|------|---------------|
| 0 | New | Not reviewed |
| 1 | Learning | 1 day |
| 2 | Reviewing | 2-6 days |
| 3 | Familiar | 7-21 days |
| 4 | Known | 22-60 days |
| 5 | Mastered | 60+ days |

---

## 7. RabbitMQ Events

### Published
| Event | Routing Key | Payload |
|-------|-------------|---------|
| Document uploaded | `document.uploaded` | `{documentId, userId, filename, bytes}` |
| Quiz completed | `quiz.completed` | `{attemptId, userId, quizId, score, total}` |
| Vocab reviewed | `vocab.reviewed` | `{vocabId, userId, quality, newLevel}` |

### Consumed
| Event | Queue | Action |
|-------|-------|--------|
| `document.processed` | `quiz.document.processed` | Update document status=READY |

---

## 8. API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/quiz/documents` | Upload document (multipart) |
| GET | `/api/quiz/documents` | List user's documents |
| GET | `/api/quiz/documents/{id}` | Get document status |
| POST | `/api/quiz/generate` | Generate quiz from document |
| GET | `/api/quiz/{id}` | Get quiz with questions |
| GET | `/api/quiz` | List user's quizzes (paginated) |
| POST | `/api/quiz/{id}/attempt` | Submit quiz attempt |
| GET | `/api/quiz/{id}/attempts` | Get attempt history |
| POST | `/api/vocabulary` | Save vocabulary word |
| GET | `/api/vocabulary` | List vocabulary (paginated, filterable) |
| PUT | `/api/vocabulary/{id}` | Update vocabulary |
| DELETE | `/api/vocabulary/{id}` | Delete vocabulary |
| GET | `/api/vocabulary/review` | Get words due for review |
| POST | `/api/vocabulary/{id}/review` | Submit review result (SM-2) |

### POST /api/quiz/generate
**Request:**
```json
{
  "documentId": "550e8400-...",
  "questionCount": 10,
  "types": ["MCQ", "FILL_BLANK", "TRUE_FALSE"],
  "difficulty": "MEDIUM"
}
```
**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "quiz-123",
    "title": "Quiz: Chapter 1 - Cell Biology",
    "questions": [
      {
        "id": "q1",
        "type": "MCQ",
        "text": "What is the powerhouse of the cell?",
        "options": ["Nucleus", "Mitochondria", "Ribosome", "Golgi"]
      }
    ]
  }
}
```

### POST /api/quiz/{id}/attempt
**Request:**
```json
{
  "responses": [
    { "questionId": "q1", "answer": "Mitochondria" },
    { "questionId": "q2", "answer": "true" }
  ]
}
```
**Response (200):**
```json
{
  "success": true,
  "data": {
    "attemptId": "att-123",
    "score": 85,
    "total": 100,
    "results": [
      { "questionId": "q1", "correct": true, "feedback": "Correct!" },
      { "questionId": "q2", "correct": false, "feedback": "The answer is false because..." }
    ]
  }
}
```

---

## 9. Database Schema

```sql
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE quizzes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    document_id UUID REFERENCES documents(id),
    title VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT',
    difficulty VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    text TEXT NOT NULL,
    options JSONB,
    correct_answer TEXT NOT NULL,
    explanation TEXT,
    order_index INT NOT NULL DEFAULT 0
);

CREATE TABLE quiz_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quizzes(id),
    user_id UUID NOT NULL,
    score INT NOT NULL,
    total INT NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP
);

CREATE TABLE question_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id),
    user_answer TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    feedback TEXT
);

CREATE TABLE vocabulary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    word VARCHAR(255) NOT NULL,
    meaning TEXT NOT NULL,
    context TEXT,
    mastery_level INT NOT NULL DEFAULT 0,
    ease_factor FLOAT NOT NULL DEFAULT 2.5,
    interval INT NOT NULL DEFAULT 0,
    next_review_at DATE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_user_id ON documents(user_id);
CREATE INDEX idx_quizzes_user_id ON quizzes(user_id);
CREATE INDEX idx_quiz_attempts_user_quiz ON quiz_attempts(user_id, quiz_id);
CREATE INDEX idx_vocabulary_user_id ON vocabulary(user_id);
CREATE INDEX idx_vocabulary_next_review ON vocabulary(user_id, next_review_at);
```
