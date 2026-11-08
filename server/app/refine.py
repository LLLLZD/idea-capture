"""一次出稿：组提示词 → 调模型 → 校验体裁与图 → 落文档 → 记用量。"""

import json
import logging
import re
import time

import httpx

from . import db, mermaid, prompt
from .config import Config

log = logging.getLogger(__name__)

GENRE_RE = re.compile(r"^体裁\s*[：:]\s*([A-Za-z0-9_]+)\s*[—–\-]\s*(.*)$")


async def load_templates_for_user(user_id: int) -> list[dict]:
    return await db.fetch_all(
        """
        SELECT t.* FROM templates t
        WHERE t.enabled
          AND (t.is_builtin OR EXISTS (
              SELECT 1 FROM user_templates ut WHERE ut.user_id = %s AND ut.template_id = t.id))
        ORDER BY t.sort, t.id
        """,
        (user_id,),
    )


async def call_llm(cfg: Config, messages: list[dict]) -> tuple[str, dict, int]:
    headers = {"Content-Type": "application/json"}
    if cfg.llm_api_key:
        headers["Authorization"] = "Bearer " + cfg.llm_api_key
    payload = {
        "model": cfg.llm_model,
        "messages": messages,
        "max_tokens": cfg.llm_max_tokens,
        "temperature": 0.3,
    }
    started = time.perf_counter()
    async with httpx.AsyncClient(timeout=cfg.llm_timeout_sec) as client:
        resp = await client.post(cfg.llm_base_url + "/chat/completions", json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
    elapsed = int((time.perf_counter() - started) * 1000)
    text = (data["choices"][0]["message"].get("content") or "").strip()
    return text, data.get("usage") or {}, elapsed


def parse_output(text: str) -> tuple[str | None, str, str]:
    """返回 (体裁 key, 判定依据, 正文)。第一行不是约定格式时就当没有体裁。"""
    lines = text.split("\n")
    head_idx = next((i for i, line in enumerate(lines) if line.strip()), None)
    if head_idx is None:
        return None, "", ""
    match = GENRE_RE.match(lines[head_idx].strip())
    if not match:
        return None, "", text.strip()
    key = match.group(1).strip().lower()
    reason = match.group(2).strip()
    body = "\n".join(lines[head_idx + 1 :]).strip()
    return key, reason, body


async def _log_usage(
    user_id: int,
    record_id: int,
    model: str,
    usage: dict,
    elapsed_ms: int,
    ok: bool,
    error: str | None = None,
) -> None:
    await db.execute(
        """
        INSERT INTO usage_logs (user_id, record_id, model, token_in, token_out, duration_ms, ok, error)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (
            user_id,
            record_id,
            model,
            int(usage.get("prompt_tokens") or 0),
            int(usage.get("completion_tokens") or 0),
            elapsed_ms,
            ok,
            error,
        ),
    )


async def refine_record(record_id: int, cfg: Config) -> None:
    record = await db.fetch_one(
        "SELECT id, user_id, raw_text FROM records WHERE id = %s", (record_id,)
    )
    if record is None:
        return
    user_id = record["user_id"]

    templates = await load_templates_for_user(user_id)
    if not templates:
        await _fail(record_id, "用户没有可用的模板")
        return
    by_key = {t["key"]: t for t in templates}
    fallback_key = "generic" if "generic" in by_key else templates[0]["key"]

    messages = [
        {"role": "system", "content": prompt.build_system_prompt(templates)},
        {"role": "user", "content": prompt.build_user_prompt(record["raw_text"])},
    ]

    text = ""
    usage: dict = {}
    elapsed = 0
    problems: list[str] = []
    try:
        for attempt in range(2):
            text, usage, elapsed = await call_llm(cfg, messages)
            await _log_usage(user_id, record_id, cfg.llm_model, usage, elapsed, True)
            if not text:
                raise RuntimeError("模型返回空内容")
            key, reason, body = parse_output(text)
            body = mermaid.normalize(body)
            problems = mermaid.validate_all(body)
            if not problems:
                break
            messages.append({"role": "assistant", "content": text})
            messages.append(
                {
                    "role": "user",
                    "content": "上面的图有问题：" + "；".join(problems) + "。只重画图，正文保持不变，重新输出整篇。",
                }
            )
    except Exception as exc:  # noqa: BLE001 - 失败要落库给客户端看
        log.exception("refine failed record=%s", record_id)
        await _log_usage(user_id, record_id, cfg.llm_model, usage, elapsed, False, str(exc)[:500])
        await _fail(record_id, "出稿失败：" + str(exc)[:300])
        return

    key, reason, body = parse_output(text)
    flags: dict = {}
    if key not in by_key:
        flags["genre_fallback"] = key or "missing"
        key = fallback_key
    body = mermaid.normalize(body)
    body, dropped = mermaid.drop_invalid_blocks(body)
    if dropped:
        flags["mermaid_dropped"] = True
    if not body.strip():
        await _fail(record_id, "模型没有返回正文")
        return

    template = by_key[key]
    await db.execute("UPDATE documents SET is_current = false WHERE record_id = %s", (record_id,))
    await db.execute(
        """
        INSERT INTO documents (record_id, template_id, template_version, genre_reason, body_md, flags, model, token_in, token_out)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (
            record_id,
            template["id"],
            template["version"],
            reason,
            body,
            json.dumps(flags, ensure_ascii=False),
            cfg.llm_model,
            int(usage.get("prompt_tokens") or 0),
            int(usage.get("completion_tokens") or 0),
        ),
    )
    await db.execute(
        "UPDATE records SET status = 'done', error = NULL, updated_at = now() WHERE id = %s",
        (record_id,),
    )


async def _fail(record_id: int, message: str) -> None:
    await db.execute(
        "UPDATE records SET status = 'failed', error = %s, updated_at = now() WHERE id = %s",
        (message[:500], record_id),
    )
