# Evaluation Service - Detail Design

## 1. Overview

Python FastAPI service handling answer grading, pronunciation scoring, feedback generation, and adaptive difficulty using Claude API.

- **Port**: 8092
- **AI**: Anthropic SDK (Claude API)
- **No database** — stateless, receives all context per request

---

## 2. Answer Grading Pipeline

```
Question + User Answer + Correct Answer + Quiz Type
    ↓
Type-specific grading:
  MCQ        → Exact match (fast, no AI needed)
  TRUE_FALSE → Exact match (fast, no AI needed)
  FILL_BLANK → Claude evaluates (synonyms, partial credit)
    ↓
Score (0.0 - 1.0) + Explanation
```

### MCQ / TRUE_FALSE Grading (local, no API call)
```python
def grade_exact(user_answer: str, correct_answer: str) -> GradeResult:
    is_correct = user_answer.strip().lower() == correct_answer.strip().lower()
    return GradeResult(score=1.0 if is_correct else 0.0, correct=is_correct)
```

### FILL_BLANK Grading (Claude API)
Prompt template:
```
Grade this fill-in-the-blank answer.

Question: {question_text}
Correct answer: {correct_answer}
Student answer: {user_answer}

Consider:
- Synonyms and alternative phrasings
- Spelling variations (minor typos = partial credit)
- Semantic correctness

Return JSON:
{"score": 0.0-1.0, "correct": bool, "explanation": "..."}
```

---

## 3. Pronunciation Scoring

```
STT Transcription + Original Text
    ↓
Word-level alignment (Levenshtein / sequence matching)
    ↓
Metrics:
  - Accuracy: % of words correctly pronounced
  - Completeness: % of original words covered
  - Fluency: based on word confidence scores
    ↓
Overall score (0-100) + word-level feedback
```

### Scoring Formula
```
accuracy = correct_words / total_original_words * 100
completeness = matched_words / total_original_words * 100
fluency = avg(word_confidences) * 100
overall = accuracy * 0.5 + completeness * 0.3 + fluency * 0.2
```

### Word-Level Comparison
```python
from difflib import SequenceMatcher

def compare_words(original: list[str], transcribed: list[str]) -> list[WordResult]:
    matcher = SequenceMatcher(None, original, transcribed)
    results = []
    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == 'equal':
            results.extend(WordResult(word=w, status='correct') for w in original[i1:i2])
        elif tag == 'replace':
            for o, t in zip(original[i1:i2], transcribed[j1:j2]):
                results.append(WordResult(word=o, status='mispronounced', got=t))
        elif tag == 'delete':
            results.extend(WordResult(word=w, status='missed') for w in original[i1:i2])
    return results
```

---

## 4. Feedback Generation

Claude generates contextual feedback based on evaluation results:

### Prompt Template
```
You are a language learning coach. Provide encouraging, constructive feedback.

Evaluation type: {type}  (answer_grading | pronunciation)
Score: {score}/100
Details: {details_json}

Provide:
1. Brief summary (1 sentence)
2. What they did well
3. Specific suggestions for improvement (max 3)

Return JSON: {"summary": "...", "strengths": ["..."], "suggestions": ["..."]}
```

---

## 5. Adaptive Difficulty

### Algorithm
```python
def recommend_difficulty(recent_scores: list[float], current_difficulty: str) -> str:
    avg = sum(recent_scores[-5:]) / min(len(recent_scores), 5)

    if avg >= 85:
        return next_harder(current_difficulty)    # EASY→MEDIUM, MEDIUM→HARD
    elif avg <= 50:
        return next_easier(current_difficulty)    # HARD→MEDIUM, MEDIUM→EASY
    else:
        return current_difficulty                 # Stay at current level
```

### Input
```json
{
  "userId": "user-123",
  "recentScores": [75, 80, 90, 85, 88],
  "currentDifficulty": "MEDIUM",
  "quizType": "DOCUMENT"
}
```

### Output
```json
{
  "recommendedDifficulty": "HARD",
  "reason": "Your recent average score is 83.6%, indicating readiness for harder questions.",
  "trend": "improving"
}
```

---

## 6. API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/evaluate/answer` | Grade a quiz answer |
| POST | `/evaluate/pronunciation` | Score pronunciation |
| POST | `/evaluate/adaptive-difficulty` | Recommend difficulty |
| POST | `/feedback` | Generate feedback from evaluation |
| GET | `/health` | Health check |

### POST /evaluate/answer
**Request:**
```json
{
  "questionType": "FILL_BLANK",
  "questionText": "The ___ is the powerhouse of the cell.",
  "correctAnswer": "mitochondria",
  "userAnswer": "mitochondrion"
}
```
**Response (200):**
```json
{
  "score": 0.9,
  "correct": true,
  "explanation": "Accepted — 'mitochondrion' is the singular form of 'mitochondria', which is semantically correct."
}
```

### POST /evaluate/pronunciation
**Request:**
```json
{
  "originalText": "The quick brown fox jumps over the lazy dog",
  "transcription": {
    "text": "The quick brown fox jumps over the lazy dog",
    "words": [
      { "word": "The", "confidence": 0.95 },
      { "word": "quick", "confidence": 0.88 }
    ]
  }
}
```
**Response (200):**
```json
{
  "overallScore": 87,
  "accuracy": 90,
  "completeness": 100,
  "fluency": 82,
  "wordResults": [
    { "word": "The", "status": "correct" },
    { "word": "quick", "status": "correct" },
    { "word": "brown", "status": "mispronounced", "got": "brow" }
  ]
}
```

### POST /feedback
**Request:**
```json
{
  "type": "pronunciation",
  "score": 87,
  "details": { "wordResults": [...] }
}
```
**Response (200):**
```json
{
  "summary": "Great job! You pronounced most words clearly with 87% accuracy.",
  "strengths": ["Clear articulation of most words", "Good sentence rhythm"],
  "suggestions": ["Practice the 'ow' sound in 'brown'", "Slow down slightly on multi-syllable words"]
}
```

---

## 7. Error Handling

| Error | Status | Response |
|-------|--------|----------|
| Missing required fields | 400 | `{"error": "field 'userAnswer' is required"}` |
| Claude API rate limit | 429 | `{"error": "AI service rate limited, retry in 30s"}` |
| Claude API failure | 502 | `{"error": "AI service unavailable"}` |
| Invalid quiz type | 400 | `{"error": "Unknown question type: XYZ"}` |
