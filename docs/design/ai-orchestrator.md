# AI Orchestrator Service - Detail Design

## 1. Overview

Central AI hub that manages Claude API interactions, prompt orchestration, tool/function calling, and routing to Python AI services. Determines which model (Haiku/Opus) to use and streams responses back.

- **Port**: 8084
- **Cache**: Redis (prompt/response caching)
- **Dependencies**: Claude API, embedding-service (8090), speech-service (8091), evaluation-service (8092)

---

## 2. Model Routing Strategy

| Use Case | Model | Reason |
|----------|-------|--------|
| Simple chat / Q&A | Haiku | Fast, cheap |
| Tool selection & execution | Haiku | Sufficient for intent detection |
| Quiz generation | Opus | Needs deep document understanding |
| Answer evaluation (complex) | Opus | Nuanced grading + explanation |
| Translation | Haiku | Straightforward task |
| Conversation generation | Opus | Creative, context-heavy |
| Code generation | Opus | Complex reasoning |

### Decision Logic
```
if task in [QUIZ_GENERATION, CONVERSATION_GENERATION, COMPLEX_EVALUATION]:
    model = OPUS
else:
    model = HAIKU
```

---

## 3. Tool Registry

### Tool Definition Schema
```json
{
  "name": "generate_quiz",
  "description": "Generate a quiz from a document using RAG",
  "category": "QUIZ",
  "targetService": "EMBEDDING + SELF",
  "parameters": {
    "type": "object",
    "properties": {
      "documentId": { "type": "string", "description": "Document UUID" },
      "questionCount": { "type": "integer", "default": 10 },
      "types": { "type": "array", "items": { "type": "string", "enum": ["MCQ", "FILL_BLANK", "TRUE_FALSE"] } },
      "difficulty": { "type": "string", "enum": ["EASY", "MEDIUM", "HARD"] }
    },
    "required": ["documentId"]
  }
}
```

---

## 4. Function Calling Protocol

```
Client → chat-service → ai-orchestrator
                              │
                    1. Send user message + tools to Claude
                              │
                    2. Claude returns tool_use block
                              │
                    3. Execute tool (call target service)
                              │
                    4. Return tool_result to Claude
                              │
                    5. Claude generates final response
                              │
                    6. Stream response back
```

### Claude API Request (with tools)
```json
{
  "model": "claude-haiku-4-5-20251001",
  "max_tokens": 4096,
  "system": "You are a learning assistant...",
  "tools": [
    {
      "name": "generate_quiz",
      "description": "Generate quiz from document",
      "input_schema": { ... }
    }
  ],
  "messages": [
    { "role": "user", "content": "Create a quiz from my biology document" }
  ]
}
```

### Claude Response (tool_use)
```json
{
  "content": [
    {
      "type": "tool_use",
      "id": "toolu_123",
      "name": "generate_quiz",
      "input": { "documentId": "doc-abc", "questionCount": 5 }
    }
  ]
}
```

---

## 5. Prompt Templates

### System Prompts
| Use Case | Template Key | Model |
|----------|-------------|-------|
| General chat | `chat.general` | Haiku |
| Quiz generation | `quiz.generate` | Opus |
| Answer evaluation | `eval.answer` | Opus |
| Conversation generation | `shadowing.conversation` | Opus |
| Translation | `translate.subtitle` | Haiku |

### Example: Quiz Generation Prompt
```
You are a quiz generator. Given the following document excerpts, generate {questionCount} questions.

Document context:
{ragContext}

Requirements:
- Types: {types}
- Difficulty: {difficulty}
- Return valid JSON matching the schema below:
{outputSchema}
```

---

## 6. Available Tools

| Tool Name | Description | Target Service | Category |
|-----------|-------------|---------------|----------|
| `generate_quiz` | Generate quiz from document via RAG | embedding + self | QUIZ |
| `evaluate_answer` | Grade a quiz answer | evaluation-service | QUIZ |
| `generate_conversation` | Generate practice dialogue | embedding + self | SHADOWING |
| `save_vocabulary` | Save a word to user's vocab list | quiz-service | VOCABULARY |
| `get_vocabulary_quiz` | Generate quiz from saved vocab | quiz-service + self | VOCABULARY |
| `extract_subtitles` | Extract subtitles from video URL | embedding-service | VIDEO |
| `translate_subtitles` | Translate subtitle text | self (Claude) | VIDEO |
| `text_to_speech` | Convert text to audio | speech-service | SPEECH |
| `evaluate_pronunciation` | Score pronunciation | speech + evaluation | SPEECH |

---

## 7. Response Streaming

Uses Spring WebFlux `Flux<String>` to stream Claude's response token-by-token:

```
ai-orchestrator → SSE stream → chat-service → WebSocket → frontend
```

- Content-Type: `text/event-stream`
- Each SSE event: `data: {"token": "Hello"}\n\n`
- Final event: `data: {"done": true, "a2ui": {...}}\n\n`

---

## 8. Redis Caching

| Cache Key Pattern | TTL | Purpose |
|------------------|-----|---------|
| `rag:{documentId}:{queryHash}` | 1 hour | RAG retrieval results |
| `prompt:{templateKey}:{hash}` | 30 min | Repeated identical prompts |
| `tool:{toolName}:{inputHash}` | 15 min | Identical tool executions |

---

## 9. API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/ai/chat` | Process chat message (streaming SSE response) |
| POST | `/api/ai/tools/{toolName}/execute` | Execute a specific tool directly |
| GET | `/api/ai/tools` | List available tools |
| GET | `/api/ai/models` | List available models and their use cases |

### POST /api/ai/chat
**Request:**
```json
{
  "conversationId": "conv-123",
  "message": "Generate a quiz from my biology document",
  "context": {
    "userId": "user-123",
    "documentIds": ["doc-abc"]
  }
}
```
**Response:** SSE stream of tokens, ending with final message + optional A2UI payload.

---

## 10. Error Handling

| Error | Strategy |
|-------|----------|
| Claude API rate limit (429) | Exponential backoff, max 3 retries |
| Claude API error (500) | Retry once, then return error to user |
| Tool execution failure | Return error as tool_result, let Claude explain to user |
| Timeout (>120s) | Cancel request, notify user |
| Invalid tool parameters | Validate before execution, return 400 |

---

## 11. Configuration

```yaml
claude:
  api:
    key: ${CLAUDE_API_KEY}
    models:
      haiku: claude-haiku-4-5-20251001
      opus: claude-opus-4-6
    max-retries: 3
    timeout: 120s

services:
  embedding:
    url: http://${EMBEDDING_SERVICE_HOST:localhost}:8090
  speech:
    url: http://${SPEECH_SERVICE_HOST:localhost}:8091
  evaluation:
    url: http://${EVALUATION_SERVICE_HOST:localhost}:8092

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
```
