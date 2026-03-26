from fastapi import FastAPI
from contextlib import asynccontextmanager

from config import settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    yield
    # Shutdown


app = FastAPI(
    title="Study Tool - Speech Service",
    description="TTS, STT, and pronunciation processing",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health")
async def health():
    return {"status": "up", "service": settings.service_name}
