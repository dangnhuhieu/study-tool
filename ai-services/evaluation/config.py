from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    service_name: str = "evaluation-service"
    host: str = "0.0.0.0"
    port: int = 8092
    debug: bool = False

    claude_api_key: str = ""

    class Config:
        env_prefix = "EVALUATION_"


settings = Settings()
