# Study Tool - Task List

## Project Overview
Generative UI Chatbot System — 3-tier microservices (React → Spring Boot → Python AI services)

---

## Phase 0: Project Scaffolding ✅ COMPLETE
- [x] 0.1 Git init, .gitignore, repo structure
- [x] 0.2 Maven multi-module parent POM (Spring Boot 3.3.5, Java 21)
- [x] 0.3 Shared Java library (DTOs, exceptions, A2UI models)
- [x] 0.4 Python AI services scaffolds (FastAPI: speech, embedding, evaluation)
- [x] 0.5 Frontend init (React + Vite 5 + TypeScript)
- [x] 0.6 Docker Compose (PostgreSQL+pgvector, Redis, RabbitMQ, all services)
- [x] 0.7 GitHub Actions CI pipeline

## Phase 1: Detail Design Documents ✅ COMPLETE
- [x] 1.1 api-gateway design → `docs/design/api-gateway.md`
- [x] 1.2 auth-service design → `docs/design/auth-service.md`
- [x] 1.3 chat-service design → `docs/design/chat-service.md`
- [x] 1.4 quiz-service design → `docs/design/quiz-service.md`
- [x] 1.5 ai-orchestrator design → `docs/design/ai-orchestrator.md`
- [x] 1.6 embedding-service design → `docs/design/embedding-service.md`
- [x] 1.7 speech-service design → `docs/design/speech-service.md`
- [x] 1.8 evaluation-service design → `docs/design/evaluation-service.md`
- [x] 1.9 A2UI protocol design → `docs/design/a2ui-protocol.md`
- [x] 1.10 frontend architecture design → `docs/design/frontend.md`

## Phase 2: Auth & Gateway ⬜ PENDING
- [ ] 2.1 auth-service: User entity, registration, login, JWT generation/validation
- [ ] 2.2 auth-service: OAuth2 social login (Google, GitHub)
- [ ] 2.3 api-gateway: Spring Cloud Gateway, route config, JWT filter, CORS
- [ ] 2.4 api-gateway: Rate limiting (Redis-backed)

## Phase 3: Chat & AI Core ⬜ PENDING
- [ ] 3.1 chat-service: Conversation + Message entities, CRUD API
- [ ] 3.2 chat-service: WebSocket/SSE endpoint for real-time streaming
- [ ] 3.3 ai-orchestrator: Claude API integration (Haiku + Opus), prompt template engine
- [ ] 3.4 ai-orchestrator: Tool registry + function calling framework
- [ ] 3.5 A2UI protocol: JSON schema, component types implementation
- [ ] 3.6 Frontend: Chat UI (message list, input, streaming, WebSocket client)
- [ ] 3.7 Frontend: A2UI renderer engine (component registry, dynamic mounting)
- [ ] 3.8 Integration: Chat → ai-orchestrator → response → A2UI render (E2E)

## Phase 4: Document & Quiz Feature ⬜ PENDING
- [ ] 4.1 embedding-service: FastAPI scaffold, document parsing (PDF/DOCX/TXT)
- [ ] 4.2 embedding-service: Text chunking + vector embedding (pgvector)
- [ ] 4.3 embedding-service: RAG retrieval endpoint
- [ ] 4.4 quiz-service: Quiz/Question/Answer/Attempt entities + CRUD API
- [ ] 4.5 quiz-service: Document upload → RabbitMQ → embedding-service
- [ ] 4.6 ai-orchestrator: Quiz generation tool (RAG → Claude → quiz JSON)
- [ ] 4.7 evaluation-service: Answer grading endpoint
- [ ] 4.8 Frontend: Quiz A2UI components (quiz-form, quiz-result)
- [ ] 4.9 quiz-service: Store results, progress queries

## Phase 5: Language Learning / Shadowing ⬜ PENDING
- [ ] 5.1 speech-service: TTS endpoint (edge-tts)
- [ ] 5.2 ai-orchestrator: Conversation generation tool
- [ ] 5.3 quiz-service: Vocabulary CRUD
- [ ] 5.4 ai-orchestrator: Vocabulary quiz generation tool
- [ ] 5.5 quiz-service: Spaced repetition (SM-2 algorithm)
- [ ] 5.6 Frontend: Shadowing UI (audio player, speed control, record, vocab save)
- [ ] 5.7 Integration: Material → conversation → TTS → shadow → save vocab → quiz

## Phase 6: Video Subtitle Learning ⬜ PENDING
- [ ] 6.1 embedding-service: Subtitle extraction (yt-dlp)
- [ ] 6.2 ai-orchestrator: Subtitle translation tool
- [ ] 6.3 speech-service: STT endpoint (pluggable provider)
- [ ] 6.4 evaluation-service: Pronunciation scoring
- [ ] 6.5 evaluation-service: Feedback generation
- [ ] 6.6 Frontend: Video learning UI (video embed, subtitles, translation, record)
- [ ] 6.7 Integration: URL → subtitles → translate → shadow → evaluate → feedback

## Phase 7: Polish & Production ⬜ PENDING
- [ ] 7.1 Global error handling (Java + Python)
- [ ] 7.2 Structured logging + health endpoints
- [ ] 7.3 Frontend: Progress dashboard
- [ ] 7.4 Kubernetes manifests
- [ ] 7.5 Integration + E2E tests
- [ ] 7.6 API documentation (Swagger + FastAPI auto-docs)

---

## Summary

| Phase | Status | Tasks |
|-------|--------|-------|
| Phase 0: Scaffolding | ✅ Complete | 7/7 |
| Phase 1: Detail Design | ✅ Complete | 10/10 |
| Phase 2: Auth & Gateway | ⬜ Pending | 0/4 |
| Phase 3: Chat & AI Core | ⬜ Pending | 0/8 |
| Phase 4: Document & Quiz | ⬜ Pending | 0/9 |
| Phase 5: Shadowing | ⬜ Pending | 0/7 |
| Phase 6: Video Learning | ⬜ Pending | 0/7 |
| Phase 7: Polish | ⬜ Pending | 0/6 |
| **Total** | | **17/58** |
