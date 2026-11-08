import json
import os
from dataclasses import dataclass, field


def _env(key: str, default: str | None = None, required: bool = False) -> str:
    value = os.environ.get(key, default)
    if required and not value:
        raise RuntimeError(f"缺少环境变量 {key}")
    return value or ""


@dataclass(frozen=True)
class Config:
    database_url: str
    jwt_secret: str
    llm_base_url: str
    llm_api_key: str
    llm_model: str
    llm_max_tokens: int = 6000
    llm_timeout_sec: int = 180
    submit_interval_sec: int = 10
    jwt_ttl_hours: int = 24
    api_docs_enabled: bool = False
    asr_presets: list = field(default_factory=list)


def load() -> Config:
    presets_raw = os.environ.get("ASR_PRESETS", "")
    try:
        presets = json.loads(presets_raw) if presets_raw else []
    except json.JSONDecodeError:
        presets = []
    return Config(
        database_url=_env("DATABASE_URL", required=True),
        jwt_secret=_env("JWT_SECRET", required=True),
        llm_base_url=_env("LLM_BASE_URL", required=True).rstrip("/"),
        llm_api_key=_env("LLM_API_KEY"),
        llm_model=_env("LLM_MODEL", required=True),
        llm_max_tokens=int(_env("LLM_MAX_TOKENS", "6000")),
        llm_timeout_sec=int(_env("LLM_TIMEOUT_SEC", "180")),
        submit_interval_sec=int(_env("SUBMIT_INTERVAL_SEC", "10")),
        jwt_ttl_hours=int(_env("JWT_TTL_HOURS", "24")),
        api_docs_enabled=_env("ENABLE_API_DOCS", "0") in ("1", "true", "True", "yes"),
        asr_presets=presets,
    )
