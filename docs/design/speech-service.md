# Speech Service - Detail Design

## 1. Overview

Python FastAPI service handling Text-to-Speech (TTS) and Speech-to-Text (STT) for shadowing practice and pronunciation evaluation.

- **Port**: 8091
- **TTS**: edge-tts (Microsoft Edge, free, high quality)
- **STT**: Pluggable interface (provider TBD)

---

## 2. TTS Pipeline

```
Text + voice + speed
    ↓
edge-tts async generation
    ↓
Audio bytes (MP3)
    ↓
Return as streaming response or complete file
```

### Voice Selection
- Default: `en-US-AriaNeural` (English)
- Japanese: `ja-JP-NanamiNeural`
- Korean: `ko-KR-SunHiNeural`
- Full list via `GET /voices`

### Speed Control
- Range: 0.5x to 2.0x
- Implemented via edge-tts rate parameter: `+0%` to `+100%` or `-50%`

---

## 3. STT Pluggable Interface

```python
from abc import ABC, abstractmethod

class STTProvider(ABC):
    @abstractmethod
    async def transcribe(self, audio_bytes: bytes, language: str) -> TranscriptionResult:
        """Transcribe audio to text."""
        pass

class TranscriptionResult:
    text: str                           # Full transcription
    words: list[WordTiming]            # Word-level timestamps
    confidence: float                  # Overall confidence (0-1)
    language: str

class WordTiming:
    word: str
    start: float                       # Start time in seconds
    end: float                         # End time in seconds
    confidence: float
```

### Planned Providers
| Provider | Class | Notes |
|----------|-------|-------|
| Whisper (local) | `WhisperSTTProvider` | Free, needs CPU/GPU |
| Google Cloud STT | `GoogleSTTProvider` | Managed, pay-per-use |
| AWS Transcribe | `AWSTranscribeProvider` | Managed, pay-per-use |

### Provider Configuration
```python
# config.py
stt_provider: str = "whisper"  # or "google", "aws"
```

---

## 4. Audio Format Handling

| Format | Support | Use Case |
|--------|---------|----------|
| MP3 | Input + Output | TTS output, general audio |
| WAV | Input | STT input (raw audio from browser) |
| OGG | Input | Browser MediaRecorder default |
| WebM | Input | Chrome MediaRecorder |

- TTS output: Always MP3 (edge-tts native format)
- STT input: Accept any format, convert internally if needed (via ffmpeg/pydub)

---

## 5. Streaming vs Batch

| Scenario | Mode | Reason |
|----------|------|--------|
| Short phrases (<100 chars) | Batch | Fast enough, simpler |
| Long text (>100 chars) | Streaming | Avoid client timeout, better UX |
| Subtitle shadowing | Batch per segment | Each subtitle segment is short |

### Streaming Response
- Content-Type: `audio/mpeg`
- Transfer-Encoding: chunked

---

## 6. API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/tts` | Convert text to speech |
| POST | `/stt` | Convert speech to text |
| GET | `/voices` | List available TTS voices |
| GET | `/health` | Health check |

### POST /tts
**Request:**
```json
{
  "text": "Hello, how are you today?",
  "voice": "en-US-AriaNeural",
  "speed": 1.0,
  "format": "mp3"
}
```
**Response:** Binary audio stream (`Content-Type: audio/mpeg`)

### POST /stt
**Request:** multipart/form-data
- `audio`: audio file (WAV/MP3/OGG/WebM)
- `language`: `en` (ISO 639-1)

**Response (200):**
```json
{
  "text": "Hello how are you today",
  "words": [
    { "word": "Hello", "start": 0.0, "end": 0.5, "confidence": 0.95 },
    { "word": "how", "start": 0.6, "end": 0.8, "confidence": 0.92 }
  ],
  "confidence": 0.93,
  "language": "en"
}
```

### GET /voices
**Response (200):**
```json
{
  "voices": [
    { "id": "en-US-AriaNeural", "name": "Aria", "language": "en-US", "gender": "Female" },
    { "id": "ja-JP-NanamiNeural", "name": "Nanami", "language": "ja-JP", "gender": "Female" }
  ]
}
```

---

## 7. Error Handling

| Error | Status | Response |
|-------|--------|----------|
| Empty text | 400 | `{"error": "Text cannot be empty"}` |
| Unsupported voice | 400 | `{"error": "Voice not found: xyz"}` |
| Audio format unsupported | 400 | `{"error": "Unsupported audio format"}` |
| STT provider unavailable | 503 | `{"error": "STT service not configured"}` |
| TTS generation failed | 500 | `{"error": "Failed to generate audio"}` |
