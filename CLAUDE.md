# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Generative UI Chatbot System** - An AI-driven platform enabling self-learning and quiz interaction through a single chat interface with dynamic UI rendering (A2UI protocol).

### Core Features
- **Document-based Quiz Generation**: Upload documents → AI-generated quizzes → in-chat interaction with progress tracking
- **Language Learning (Shadowing Mode)**: Conversation generation from materials, vocabulary saving, quiz generation from saved terms
- **Video Subtitle Learning**: YouTube URL → subtitle extraction → real-time translation → shadowing practice → pronunciation evaluation with feedback

---

## Architecture

### 3-Tier Model

```
Frontend (React + Vite 6 + TypeScript + A2UI)
                ↓
API Gateway (Spring Boot - routing, JWT filter, rate limiting)
                ↓
┌──────────────────────────────────┐
│  Core Services (Java/Spring Boot)│
│  ├── auth-service                │
│  ├── chat-service                │
│  ├── quiz-service                │
│  └── ai-orchestrator-service     │
└──────────────────────────────────┘
                ↓ (REST + RabbitMQ)
┌──────────────────────────────────┐
│  AI Services (Python/FastAPI)    │
│  ├── speech-service              │
│  │   (TTS, STT, pronunciation)   │
│  ├── embedding-service           │
│  │   (RAG, doc processing, yt-dlp)│
│  └── evaluation-service          │
│      (answer grading, feedback)  │
└──────────────────────────────────┘
```

### Responsibility Split
- **Java services**: Business logic, persistence, API contracts, authentication
- **Python services**: ML/AI workloads (speech, embeddings, evaluation) leveraging the Python ML ecosystem
- **Communication**: REST for synchronous calls, RabbitMQ for async tasks (document processing, speech evaluation)

### Infrastructure
- **PostgreSQL + pgvector**: Single DB engine for relational data and vector embeddings
- **Redis**: Prompt caching (ai-orchestrator), rate limiting (api-gateway)
- **RabbitMQ**: Async inter-service messaging

---

## Monorepo Structure

```
study-tool/
├── pom.xml                          # Maven parent POM
├── shared-lib/                      # Shared Java DTOs, exceptions, A2UI models
├── services/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── chat-service/
│   ├── quiz-service/
│   └── ai-orchestrator-service/
├── ai-services/
│   ├── speech/                      # Python FastAPI
│   ├── embedding/                   # Python FastAPI
│   └── evaluation/                  # Python FastAPI
├── frontend/                        # React + Vite + TypeScript
├── docs/design/                     # Service design documents
├── k8s/                             # Kubernetes manifests
├── docker-compose.yml
├── .github/workflows/
└── CLAUDE.md
```

---

## Model Strategy

- **Claude Haiku**: Simple chat interactions, tool usage, command execution
- **Claude Opus**: Code generation, codebase understanding, complex reasoning tasks

---

## Development Commands

### Build & Dependencies
```bash
# Build all Java services
mvn clean install -DskipTests

# Build specific Java service
mvn package -pl services/<service-name> -DskipTests

# Install Python dependencies (per AI service)
cd ai-services/<service-name> && pip install -r requirements.txt

# Build frontend
cd frontend && npm install && npm run build
```

### Testing
```bash
# Java: Run all tests
mvn test

# Java: Run tests for specific service
mvn test -pl services/<service-name>

# Java: Tests with coverage
mvn clean test jacoco:report

# Python: Run tests per AI service
cd ai-services/<service-name> && pytest

# Frontend tests
cd frontend && npm test
```

### Local Development
```bash
# Start all services (Docker Compose)
docker-compose up

# Start specific Java service
mvn spring-boot:run -pl services/<service-name>

# Start specific Python service
cd ai-services/<service-name> && uvicorn main:app --reload --port <port>

# Start frontend dev server
cd frontend && npm run dev
```

### Code Quality
```bash
# Java lint/format
mvn checkstyle:check
mvn spotless:apply

# Python lint
cd ai-services/<service-name> && ruff check . && ruff format .

# Frontend lint
cd frontend && npm run lint
cd frontend && npm run lint:fix
```

### Debugging
```bash
# Java debug mode
mvn spring-boot:run -pl services/<service-name> -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"

# Frontend: npm run dev (then open DevTools)
```

---

## Key Architectural Patterns

### A2UI Protocol
- Enables rendering interactive UI components inside the chat (quizzes, vocab lists, video players, progress charts)
- Backend returns structured JSON; frontend dynamically renders React components

### Tool Calling Flow
1. User message → `chat-service`
2. `ai-orchestrator-service` analyzes intent, selects tools
3. Tools call Java or Python services (REST/RabbitMQ)
4. Results formatted with A2UI components
5. Frontend renders interactive UI

### Authentication & Authorization
- JWT tokens for API access, validated at api-gateway
- OAuth2 for social login (Google, GitHub)
- Role-based access: Admin, Normal user (with API rate limits)

### RAG Pipeline
1. Document upload → `quiz-service` → RabbitMQ → `embedding-service`
2. `embedding-service`: parse → chunk → embed → store in pgvector
3. Quiz generation: `ai-orchestrator` queries `embedding-service` for relevant chunks → Claude generates quiz

---

## Deployment

- **Local**: Docker Compose with all services + PostgreSQL + Redis + RabbitMQ
- **Production**: Kubernetes (manifests in `k8s/`)
- **Future**: AWS (ECS/EKS) — services already containerized
