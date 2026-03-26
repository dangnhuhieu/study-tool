from fastapi import FastAPI
from contextlib import asynccontextmanager

from config import settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: load embedding model, init DB connection
    yield
    # Shutdown


app = FastAPI(
    title="Study Tool - Embedding Service",
    description="Document processing, RAG, vector embeddings, subtitle extraction",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health")
async def health():
    return {"status": "up", "service": settings.service_name}
