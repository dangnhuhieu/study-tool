from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service_name: str = "speech-service"
    host: str = "0.0.0.0"
    port: int = 8091
    debug: bool = False

    class Config:
        env_prefix = "SPEECH_"


settings = Settings()
