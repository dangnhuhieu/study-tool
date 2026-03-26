# Session Log — 2026-03-25

## What Was Done

### Phase 0: Project Scaffolding ✅
Created the entire monorepo structure from scratch:

**Repository & Build**
- Initialized git repository with comprehensive `.gitignore` (Java, Python, Node, IDE, env)
- Created Maven multi-module parent POM (Spring Boot 3.3.5, Spring Cloud 2023.0.3, Java 21)
- 6 Maven modules: shared-lib, api-gateway, auth-service, chat-service, quiz-service, ai-orchestrator-service

**Java Services (5 services + shared library)**
- `shared-lib` — Common DTOs (`ApiResponse`, `PagedResponse`), exceptions (`ResourceNotFoundException`, `BadRequestException`, `GlobalExceptionHandler`), A2UI models (`A2UIComponent`, `A2UIComponentType`, `A2UIMessage`)
- `api-gateway` (8080) — Spring Cloud Gateway, Redis, JWT deps
- `auth-service` (8081) — Spring Security, JPA, OAuth2, JWT, PostgreSQL
- `chat-service` (8082) — WebSocket, JPA, RabbitMQ, PostgreSQL
- `quiz-service` (8083) — JPA, RabbitMQ, file upload (50MB), PostgreSQL
- `ai-orchestrator-service` (8084) — WebFlux, Redis, Claude API config
- Each service has: Application.java, application.yml, Dockerfile

**Python AI Services (3 services)**
- `ai-services/embedding` (8090) — FastAPI, LangChain, pgvector, sentence-transformers, yt-dlp, unstructured
- `ai-services/speech` (8091) — FastAPI, edge-tts
- `ai-services/evaluation` (8092) — FastAPI, Anthropic SDK
- Each service has: main.py, config.py, requirements.txt, Dockerfile

**Frontend**
- React 18 + Vite 5 + TypeScript
- Folder structure: components/, hooks/, services/, types/, utils/, stores/
- Vite config with API proxy to gateway, path alias (@/*)
- Production Dockerfile (multi-stage: build → nginx)
- nginx.conf with SPA routing + WebSocket proxy

**Infrastructure**
- `docker-compose.yml` — All 9 services + PostgreSQL (pgvector), Redis, RabbitMQ
- `scripts/init-databases.sql` — Creates 4 databases, enables vector extension
- `.github/workflows/ci.yml` — CI pipeline (Java build, Python matrix build, Frontend build, Docker image build)
- `.env.example` — Template for secrets

### Phase 1: Detail Design Documents ✅
Created 10 comprehensive design documents in `docs/design/`:

| Document | Key Contents |
|----------|-------------|
| `api-gateway.md` | Route table, JWT filter chain, rate limiting (Redis), CORS, error handling |
| `auth-service.md` | User/Role ERD, registration/login/OAuth2 flows, JWT structure, SQL schema, full API specs |
| `chat-service.md` | Conversation/Message ERD, WebSocket protocol, A2UI message format, progress tracking, RabbitMQ events, SQL schema |
| `quiz-service.md` | Quiz/Question/Attempt/Vocabulary ERD, quiz types (MCQ/fill-blank/true-false), SM-2 spaced repetition, document upload flow, full API specs, SQL schema |
| `ai-orchestrator.md` | Model routing (Haiku vs Opus), tool registry, function calling protocol, prompt templates, 9 tools defined, streaming, Redis caching |
| `embedding-service.md` | Document pipeline, chunking (512/50), all-MiniLM-L6-v2 (384-dim), pgvector HNSW index, RAG flow, yt-dlp subtitle extraction |
| `speech-service.md` | edge-tts pipeline, pluggable STT interface (abstract class), audio format handling, streaming vs batch |
| `evaluation-service.md` | Answer grading (exact match + Claude for fill-blank), pronunciation scoring (accuracy/completeness/fluency), adaptive difficulty algorithm |
| `a2ui-protocol.md` | JSON schema, 8 component types with full props/events, rendering contract, event handling, versioning |
| `frontend.md` | Component hierarchy, A2UI renderer (lazy loading + error boundaries), Zustand stores, WebSocket client, auth flow (in-memory tokens), routing |

---

## Technical Decisions Made
- **Monorepo**: Single repo with Maven multi-module (Java) + separate Python dirs
- **Database**: PostgreSQL + pgvector (single engine for relational + vector data)
- **Messaging**: REST (sync) + RabbitMQ (async heavy tasks)
- **STT Provider**: Pluggable interface, provider TBD (Whisper/Google/AWS)
- **Frontend**: Vite 5 + React 18 + TypeScript (Note: Node 18 on system, Vite 5 used for compat)
- **Token storage**: In-memory (not localStorage) for XSS protection

---

## Next Steps

### Immediate: Phase 2 — Auth & Gateway
1. **auth-service**: Implement User/Role/RefreshToken JPA entities, BCrypt, JWT generation/validation, registration + login endpoints
2. **auth-service**: Add OAuth2 social login (Google, GitHub)
3. **api-gateway**: Configure Spring Cloud Gateway routes, JWT validation filter, CORS
4. **api-gateway**: Redis-backed rate limiting

### Then: Phase 3 — Chat & AI Core
- Implement chat-service (conversations, messages, WebSocket)
- Implement ai-orchestrator (Claude API, tool calling, streaming)
- Build A2UI protocol implementation
- Build frontend chat UI + A2UI renderer

### Parallel after Phase 3: Phases 4, 5, 6
- Document/Quiz, Shadowing, Video features can be developed in parallel

---

## Files Created This Session

```
Total: 65 files

Root:          .gitignore, pom.xml, docker-compose.yml, .env.example, CLAUDE.md
Scripts:       scripts/init-databases.sql
CI:            .github/workflows/ci.yml
Shared lib:    shared-lib/pom.xml + 6 Java files (DTOs, exceptions, A2UI)
API Gateway:   pom.xml, Application.java, application.yml, Dockerfile
Auth Service:  pom.xml, Application.java, application.yml, Dockerfile
Chat Service:  pom.xml, Application.java, application.yml, Dockerfile
Quiz Service:  pom.xml, Application.java, application.yml, Dockerfile
AI Orchestr.:  pom.xml, Application.java, application.yml, Dockerfile
Embedding:     main.py, config.py, requirements.txt, Dockerfile
Speech:        main.py, config.py, requirements.txt, Dockerfile
Evaluation:    main.py, config.py, requirements.txt, Dockerfile
Frontend:      package.json, tsconfig.json, vite.config.ts, index.html,
               src/main.tsx, src/App.tsx, src/vite-env.d.ts,
               Dockerfile, nginx.conf
Design docs:   10 markdown files in docs/design/
Task/Logs:     docs/TASK.md, logs/session.md
```
