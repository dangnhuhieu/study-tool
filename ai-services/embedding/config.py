from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service_name: str = "embedding-service"
    host: str = "0.0.0.0"
    port: int = 8090
    debug: bool = False

    database_url: str = "postgresql://postgres:postgres@localhost:5432/studytool_embedding"
    embedding_model: str = "all-MiniLM-L6-v2"
    chunk_size: int = 512
    chunk_overlap: int = 50

    class Config:
        env_prefix = "EMBEDDING_"


settings = Settings()
