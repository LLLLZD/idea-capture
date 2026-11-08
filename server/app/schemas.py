from datetime import datetime

from pydantic import BaseModel, Field, field_validator


class LoginIn(BaseModel):
    username: str = Field(min_length=1, max_length=64)
    password: str = Field(min_length=1, max_length=128)


class TemplateIdsIn(BaseModel):
    template_ids: list[int] = Field(min_length=1)


class RecordIn(BaseModel):
    client_id: str = Field(min_length=8, max_length=64)
    text: str = Field(min_length=1, max_length=20000)
    duration_ms: int | None = None
    recorded_at: datetime | None = None
    lang: str = "zh"
    asr_provider: str | None = None
    asr_model: str | None = None

    @field_validator("text")
    @classmethod
    def _text_not_blank(cls, v: str) -> str:
        stripped = v.strip()
        if not stripped:
            raise ValueError("text 不能只有空白字符")
        return stripped


class StatusIn(BaseModel):
    client_ids: list[str] = Field(min_length=1, max_length=50)


class RenderErrorIn(BaseModel):
    detail: str = ""
