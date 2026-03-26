# A2UI Protocol - Detail Design

## 1. Overview

A2UI (AI-to-UI) is the protocol that enables the AI backend to dynamically render interactive UI components inside the chat interface. Instead of returning plain text, the backend returns structured JSON that the frontend interprets and renders as React components.

---

## 2. JSON Schema Specification

### TypeScript Type Definitions

```typescript
interface A2UIMessage {
  text: string | null;                 // Plain text portion of the message
  components: A2UIComponent[];        // Interactive UI components
}

interface A2UIComponent {
  type: string;                        // Component type identifier
  id: string;                          // Unique ID for this component instance
  props: Record<string, unknown>;     // Component-specific properties
  children?: A2UIComponent[];         // Nested child components
}
```

### JSON Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "properties": {
    "text": { "type": ["string", "null"] },
    "components": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["type", "id", "props"],
        "properties": {
          "type": { "type": "string" },
          "id": { "type": "string" },
          "props": { "type": "object" },
          "children": { "type": "array", "items": { "$ref": "#/properties/components/items" } }
        }
      }
    }
  }
}
```

---

## 3. Component Type Registry

### quiz-form
Renders an interactive quiz that users can answer in chat.
```json
{
  "type": "quiz-form",
  "id": "quiz-abc123",
  "props": {
    "quizId": "q-123",
    "title": "Cell Biology Quiz",
    "questions": [
      {
        "id": "q1",
        "type": "mcq",
        "text": "What is the powerhouse of the cell?",
        "options": ["Nucleus", "Mitochondria", "Ribosome", "Golgi"]
      },
      {
        "id": "q2",
        "type": "fill_blank",
        "text": "DNA stands for ___ acid."
      },
      {
        "id": "q3",
        "type": "true_false",
        "text": "Cells can only reproduce through mitosis."
      }
    ]
  }
}
```
**Events:** `onSubmit(quizId, responses[])` → POST `/api/quiz/{id}/attempt`

### quiz-result
Displays quiz results with per-question feedback.
```json
{
  "type": "quiz-result",
  "id": "result-abc",
  "props": {
    "score": 80,
    "total": 100,
    "passed": true,
    "answers": [
      { "questionId": "q1", "correct": true, "userAnswer": "Mitochondria", "explanation": "Correct!" },
      { "questionId": "q2", "correct": false, "userAnswer": "ribonucleic", "correctAnswer": "deoxyribonucleic", "explanation": "DNA = Deoxyribonucleic acid" }
    ]
  }
}
```

### vocab-list
Displays vocabulary with mastery levels and save/remove actions.
```json
{
  "type": "vocab-list",
  "id": "vocab-abc",
  "props": {
    "words": [
      { "id": "v1", "word": "ephemeral", "meaning": "lasting for a very short time", "context": "The ephemeral beauty of cherry blossoms", "masteryLevel": 2 },
      { "id": "v2", "word": "ubiquitous", "meaning": "present everywhere", "context": "Smartphones are ubiquitous in modern society", "masteryLevel": 0 }
    ]
  }
}
```
**Events:** `onSave(wordId)`, `onRemove(wordId)`

### audio-player
Audio playback for shadowing practice.
```json
{
  "type": "audio-player",
  "id": "audio-abc",
  "props": {
    "src": "/api/ai/tools/text_to_speech/result/tts-123",
    "label": "Listen and repeat",
    "speed": 1.0,
    "autoplay": false,
    "text": "Hello, how are you today?"
  }
}
```

### video-player
Embedded video with subtitle support.
```json
{
  "type": "video-player",
  "id": "video-abc",
  "props": {
    "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "subtitles": [
      { "start": 0.0, "end": 2.5, "text": "Hello everyone", "translation": "みなさんこんにちは" }
    ],
    "showTranslation": true
  }
}
```

### subtitle-overlay
Scrollable subtitle list with translation and seek-to-time.
```json
{
  "type": "subtitle-overlay",
  "id": "subs-abc",
  "props": {
    "subtitles": [
      { "start": 0.0, "end": 2.5, "text": "Hello everyone", "translation": "みなさんこんにちは" },
      { "start": 2.5, "end": 5.0, "text": "Welcome to the lesson", "translation": "レッスンへようこそ" }
    ],
    "activeIndex": 0
  }
}
```
**Events:** `onSeek(startTime)`

### progress-chart
Displays learning progress data.
```json
{
  "type": "progress-chart",
  "id": "chart-abc",
  "props": {
    "title": "Quiz Scores - Last 30 Days",
    "chartType": "line",
    "data": [
      { "date": "2024-01-01", "value": 65 },
      { "date": "2024-01-08", "value": 72 },
      { "date": "2024-01-15", "value": 85 }
    ],
    "yLabel": "Score (%)",
    "xLabel": "Date"
  }
}
```

### feedback-card
Displays evaluation feedback with suggestions.
```json
{
  "type": "feedback-card",
  "id": "feedback-abc",
  "props": {
    "score": 87,
    "type": "pronunciation",
    "summary": "Great pronunciation! 87% accuracy.",
    "strengths": ["Clear vowel sounds", "Good pacing"],
    "suggestions": ["Practice the 'th' sound", "Slow down on longer words"]
  }
}
```

---

## 4. Message Format

A chat message with A2UI components:
```json
{
  "id": "msg-123",
  "role": "ASSISTANT",
  "content": "Here's a quiz based on your document:",
  "a2uiPayload": {
    "components": [
      { "type": "quiz-form", "id": "quiz-1", "props": { ... } }
    ]
  }
}
```

The `content` field is always present (plain text). The `a2uiPayload` is optional and only present when the response includes interactive components.

---

## 5. Rendering Contract

Rules the frontend must follow:
1. **Unknown type** → Render a fallback component (`"Unsupported component"`)
2. **Missing props** → Use sensible defaults, don't crash
3. **Error in component** → Error boundary catches per-component, other components still render
4. **Empty components array** → Render text-only message
5. **Null a2uiPayload** → Render text-only message

---

## 6. Event Handling

User interactions on A2UI components trigger actions:

```typescript
interface A2UIEvent {
  componentId: string;
  eventType: string;        // "submit", "save", "remove", "seek"
  payload: Record<string, unknown>;
}
```

Events are sent via WebSocket:
```json
{
  "type": "A2UI_EVENT",
  "conversationId": "conv-123",
  "event": {
    "componentId": "quiz-abc123",
    "eventType": "submit",
    "payload": {
      "responses": [
        { "questionId": "q1", "answer": "Mitochondria" }
      ]
    }
  }
}
```

---

## 7. Versioning

- Protocol version included in message: `"a2uiVersion": "1.0"`
- Frontend checks version, applies backward-compatible rendering
- New component types are additive (don't break old clients)
- Deprecated types: render with warning, then remove after 2 versions

---

## 8. Error States

| State | Rendering |
|-------|-----------|
| Component loading | Skeleton/spinner placeholder |
| Component error | Error boundary with "Something went wrong" + retry button |
| Network error on event | Toast notification + retry |
| Unknown component type | Gray box with "Unsupported content" |
