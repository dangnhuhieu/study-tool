# Embedding Service - Detail Design

## 1. Overview

Python FastAPI service handling document parsing, text chunking, vector embeddings, RAG retrieval, and YouTube subtitle extraction.

- **Port**: 8090
- **Database**: PostgreSQL + pgvector (`studytool_embedding`)
- **Stack**: FastAPI, LangChain, sentence-transformers, unstructured, yt-dlp

---

## 2. Document Processing Pipeline

```
Upload (bytes + metadata)
    ↓
Parse (unstructured: PDF/DOCX/TXT)
    ↓
Clean (remove headers, footers, artifacts)
    ↓
Chunk (RecursiveCharacterTextSplitter: 512 tokens, 50 overlap)
    ↓
Embed (all-MiniLM-L6-v2 → 384-dim vectors)
    ↓
Store (pgvector: chunks + vectors + metadata)
    ↓
Publish "document.processed" event
```

---

## 3. Chunking Strategy

| Parameter | Value |
|-----------|-------|
| Method | RecursiveCharacterTextSplitter |
| Chunk size | 512 tokens (~2000 chars) |
| Chunk overlap | 50 tokens (~200 chars) |
| Separators | `["\n\n", "\n", ". ", " ", ""]` |

### Metadata per Chunk
```json
{
  "document_id": "doc-abc",
  "chunk_index": 0,
  "page_number": 1,
  "source": "biology-ch1.pdf",
  "total_chunks": 45
}
```

---

## 4. Embedding Model

| Property | Value |
|----------|-------|
| Model | `all-MiniLM-L6-v2` |
| Dimensions | 384 |
| Max sequence length | 256 tokens |
| Speed | ~14,000 sentences/sec on CPU |
| Size | ~80MB |

- Loaded once at startup via `sentence_transformers.SentenceTransformer`
- Batch embedding for documents (batch_size=64)
- Single embedding for queries

---

## 5. pgvector Schema

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(384) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_chunks_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

### HNSW Index Parameters
| Parameter | Value | Reason |
|-----------|-------|--------|
| m | 16 | Good balance of speed vs recall |
| ef_construction | 64 | Higher build quality |
| Distance metric | cosine | Standard for semantic similarity |

---

## 6. RAG Retrieval Flow

```
Query string
    ↓
Embed query (same model: all-MiniLM-L6-v2)
    ↓
Vector similarity search (pgvector: cosine distance, top-k=5)
    ↓
Filter by document_id (if specified)
    ↓
Return chunks with metadata + similarity scores
```

### SQL Query
```sql
SELECT id, document_id, content, metadata,
       1 - (embedding <=> $1::vector) AS similarity
FROM document_chunks
WHERE document_id = $2
ORDER BY embedding <=> $1::vector
LIMIT $3;
```

---

## 7. Subtitle Extraction

### Flow
```
YouTube URL → yt-dlp → extract available subtitles → parse SRT/VTT → structured JSON
```

### Supported
- YouTube URLs (youtube.com, youtu.be)
- Auto-generated subtitles
- Manual subtitles (multiple languages)

### Output Format
```json
{
  "videoId": "dQw4w9WgXcQ",
  "title": "Video Title",
  "duration": 213,
  "language": "en",
  "subtitles": [
    { "start": 0.0, "end": 2.5, "text": "Hello everyone" },
    { "start": 2.5, "end": 5.0, "text": "Welcome to the lesson" }
  ],
  "availableLanguages": ["en", "ja", "ko"]
}
```

---

## 8. API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/documents` | Process document (bytes + metadata) |
| GET | `/documents/{id}/status` | Check processing status |
| GET | `/documents/{id}/chunks` | Get all chunks for a document |
| POST | `/retrieve` | RAG query — semantic search |
| POST | `/subtitles/extract` | Extract subtitles from video URL |
| GET | `/health` | Health check |

### POST /documents
**Request:** multipart/form-data with `file` + `document_id` + `user_id`

**Response (202):**
```json
{
  "documentId": "doc-abc",
  "status": "processing",
  "message": "Document queued for processing"
}
```

### POST /retrieve
**Request:**
```json
{
  "query": "What is the function of mitochondria?",
  "documentId": "doc-abc",
  "topK": 5
}
```
**Response (200):**
```json
{
  "results": [
    {
      "chunkId": "chunk-1",
      "content": "Mitochondria are the powerhouse of the cell...",
      "similarity": 0.87,
      "metadata": { "page_number": 3, "chunk_index": 12 }
    }
  ]
}
```

### POST /subtitles/extract
**Request:**
```json
{
  "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
  "language": "en"
}
```
**Response (200):** Subtitle JSON as shown in section 7.

---

## 9. Error Handling

| Error | Status | Response |
|-------|--------|----------|
| Unsupported file type | 400 | `{"error": "Unsupported file type: .xyz"}` |
| File too large | 413 | `{"error": "File exceeds 50MB limit"}` |
| PDF parsing failure | 422 | `{"error": "Could not parse document"}` |
| Video URL invalid | 400 | `{"error": "Invalid or unsupported video URL"}` |
| Subtitles unavailable | 404 | `{"error": "No subtitles available for this video"}` |
| yt-dlp failure | 502 | `{"error": "Failed to fetch video data"}` |
