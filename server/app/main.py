import asyncio
import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, HTTPException, Query, Response
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from . import db, migrate, security, worker
from .config import load as load_config
from .ratelimit import LoginLimiter
from .schemas import LoginIn, RecordIn, RenderErrorIn, StatusIn, TemplateIdsIn

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("idea.capture")

cfg = load_config()
bearer = HTTPBearer(auto_error=False)


@asynccontextmanager
async def lifespan(app: FastAPI):
    await db.init(cfg.database_url)
    await migrate.run_all()
    recovered = await worker.recover()
    if recovered:
        log.info("requeued %d records left running", recovered)
    stop = asyncio.Event()
    task = asyncio.create_task(worker.run(cfg, stop))
    try:
        yield
    finally:
        stop.set()
        try:
            await asyncio.wait_for(task, timeout=5)
        except (asyncio.TimeoutError, asyncio.CancelledError):
            task.cancel()
        await db.close()


# 生产默认不暴露接口文档：/docs 和 /openapi.json 会给扫描器送接口清单。
app = FastAPI(
    title="idea-capture",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs" if cfg.api_docs_enabled else None,
    redoc_url="/redoc" if cfg.api_docs_enabled else None,
    openapi_url="/openapi.json" if cfg.api_docs_enabled else None,
)

login_limiter = LoginLimiter()


def _now() -> datetime:
    return datetime.now(timezone.utc)


async def current_user(cred: HTTPAuthorizationCredentials | None = Depends(bearer)) -> dict:
    if cred is None or not cred.credentials:
        raise HTTPException(status_code=401, detail="缺少 token")
    user_id = security.decode_token(cred.credentials, cfg.jwt_secret)
    if user_id is None:
        raise HTTPException(status_code=401, detail="token 无效或已过期")
    user = await db.fetch_one(
        "SELECT id, username, display_name, role, status FROM users WHERE id = %s", (user_id,)
    )
    if user is None or user["status"] != "active":
        raise HTTPException(status_code=401, detail="账号不可用")
    return user


def _template_public(row: dict, selected: bool) -> dict:
    return {
        "id": row["id"],
        "key": row["key"],
        "name": row["name"],
        "criterion": row["criterion"],
        "sample_md": row["sample_md"],
        "word_range": row["word_range"],
        "is_builtin": row["is_builtin"],
        "selected": selected,
    }


@app.get("/v1/ping")
async def ping() -> dict:
    return {"ok": True, "version": app.version, "time": _now().isoformat()}


@app.post("/v1/auth/login")
async def login(payload: LoginIn) -> dict:
    key = payload.username.strip().lower()
    wait = login_limiter.retry_after(key)
    if wait > 0:
        raise HTTPException(
            status_code=429,
            detail=f"密码错误次数太多，{wait} 秒后再试",
            headers={"Retry-After": str(wait)},
        )
    user = await db.fetch_one(
        "SELECT id, username, display_name, role, status, password_hash FROM users WHERE username = %s",
        (payload.username.strip(),),
    )
    if user is None or not security.verify_password(payload.password, user["password_hash"]):
        login_limiter.fail(key)
        raise HTTPException(status_code=401, detail="用户名或密码不对")
    if user["status"] != "active":
        raise HTTPException(status_code=403, detail="账号已停用")
    login_limiter.reset(key)

    token, expires = security.create_token(user["id"], cfg.jwt_secret, cfg.jwt_ttl_hours)
    await db.execute("UPDATE users SET last_login_at = now() WHERE id = %s", (user["id"],))
    chosen = await db.fetch_one(
        "SELECT count(*) AS n FROM user_templates WHERE user_id = %s", (user["id"],)
    )
    return {
        "token": token,
        "expires_at": expires.isoformat(),
        "user": {
            "id": user["id"],
            "username": user["username"],
            "display_name": user["display_name"],
            "role": user["role"],
        },
        "needs_template_setup": (chosen or {}).get("n", 0) == 0,
    }


@app.get("/v1/client-config")
async def client_config(user: dict = Depends(current_user)) -> dict:
    rows = await db.fetch_all("SELECT * FROM templates WHERE enabled ORDER BY sort, id")
    selected = {
        r["template_id"]
        for r in await db.fetch_all(
            "SELECT template_id FROM user_templates WHERE user_id = %s", (user["id"],)
        )
    }
    return {
        "submit_interval_sec": cfg.submit_interval_sec,
        "min_selectable": 1,
        "asr_presets": cfg.asr_presets,
        "templates": [_template_public(r, r["id"] in selected) for r in rows],
    }


@app.get("/v1/templates")
async def list_templates(user: dict = Depends(current_user)) -> dict:
    rows = await db.fetch_all("SELECT * FROM templates WHERE enabled ORDER BY sort, id")
    selected = {
        r["template_id"]
        for r in await db.fetch_all(
            "SELECT template_id FROM user_templates WHERE user_id = %s", (user["id"],)
        )
    }
    return {"templates": [_template_public(r, r["id"] in selected) for r in rows]}


@app.put("/v1/me/templates")
async def set_templates(payload: TemplateIdsIn, user: dict = Depends(current_user)) -> dict:
    ids = list(dict.fromkeys(payload.template_ids))
    rows = await db.fetch_all(
        "SELECT id, is_builtin FROM templates WHERE enabled AND id = ANY(%s)", (ids,)
    )
    if len(rows) != len(ids):
        raise HTTPException(status_code=400, detail="有模板不存在或已下架")
    if all(r["is_builtin"] for r in rows):
        raise HTTPException(status_code=400, detail="至少要勾选一个自己的常用体裁")
    keep = [r["id"] for r in rows if not r["is_builtin"]]
    await db.execute("DELETE FROM user_templates WHERE user_id = %s", (user["id"],))
    for template_id in keep:
        await db.execute(
            "INSERT INTO user_templates (user_id, template_id) VALUES (%s, %s) ON CONFLICT DO NOTHING",
            (user["id"], template_id),
        )
    return {"ok": True, "selected": keep}


@app.post("/v1/records")
async def submit_record(payload: RecordIn, response: Response, user: dict = Depends(current_user)) -> dict:
    existing = await db.fetch_one(
        "SELECT status FROM records WHERE user_id = %s AND client_id = %s",
        (user["id"], payload.client_id),
    )
    if existing is not None:
        response.status_code = 200
        return {"ok": True, "duplicated": True, "status": existing["status"]}

    last = await db.fetch_one(
        "SELECT created_at FROM records WHERE user_id = %s ORDER BY id DESC LIMIT 1", (user["id"],)
    )
    if last is not None:
        gap = (_now() - last["created_at"]).total_seconds()
        if gap < cfg.submit_interval_sec:
            response.status_code = 429
            return {
                "ok": False,
                "error": "rate_limited",
                "retry_after": int(cfg.submit_interval_sec - gap) + 1,
            }

    inserted = await db.fetch_one(
        """
        INSERT INTO records (user_id, client_id, raw_text, duration_ms, recorded_at, lang, asr_provider, asr_model)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (user_id, client_id) DO NOTHING
        RETURNING id, status
        """,
        (
            user["id"],
            payload.client_id,
            payload.text,
            payload.duration_ms,
            payload.recorded_at,
            payload.lang,
            payload.asr_provider,
            payload.asr_model,
        ),
    )
    if inserted is None:
        existing = await db.fetch_one(
            "SELECT status FROM records WHERE user_id = %s AND client_id = %s",
            (user["id"], payload.client_id),
        )
        response.status_code = 200
        return {"ok": True, "duplicated": True, "status": (existing or {}).get("status")}

    response.status_code = 202
    return {"ok": True, "duplicated": False, "status": inserted["status"]}


@app.post("/v1/records/status")
async def records_status(payload: StatusIn, user: dict = Depends(current_user)) -> dict:
    rows = await db.fetch_all(
        """
        SELECT client_id, status, error, created_at, updated_at
        FROM records WHERE user_id = %s AND client_id = ANY(%s)
        """,
        (user["id"], payload.client_ids),
    )
    return {"records": rows}


@app.get("/v1/records")
async def list_records(
    user: dict = Depends(current_user),
    limit: int = Query(20, ge=1, le=50),
    before_id: int | None = None,
) -> dict:
    params: list = [user["id"]]
    sql = """
        SELECT r.id, r.client_id, r.status, r.duration_ms, r.created_at, r.recorded_at,
               d.id AS document_id, d.body_md, t.name AS template_name
        FROM records r
        LEFT JOIN documents d ON d.record_id = r.id AND d.is_current
        LEFT JOIN templates t ON t.id = d.template_id
        WHERE r.user_id = %s
    """
    if before_id is not None:
        sql += " AND r.id < %s"
        params.append(before_id)
    sql += " ORDER BY r.id DESC LIMIT %s"
    params.append(limit)
    rows = await db.fetch_all(sql, tuple(params))
    items = []
    for row in rows:
        body = row.pop("body_md") or ""
        preview = body.replace("#", "").strip().split("\n")[0][:60] if body else ""
        items.append({**row, "preview": preview})
    return {"records": items}


@app.get("/v1/records/{client_id}")
async def get_record(client_id: str, user: dict = Depends(current_user)) -> dict:
    record = await db.fetch_one(
        "SELECT * FROM records WHERE user_id = %s AND client_id = %s", (user["id"], client_id)
    )
    if record is None:
        raise HTTPException(status_code=404, detail="没有这条记录")
    document = await db.fetch_one(
        """
        SELECT d.*, t.name AS template_name, t.key AS template_key
        FROM documents d LEFT JOIN templates t ON t.id = d.template_id
        WHERE d.record_id = %s AND d.is_current
        """,
        (record["id"],),
    )
    return {"record": record, "document": document}


@app.post("/v1/records/{client_id}/retry")
async def retry_record(client_id: str, response: Response, user: dict = Depends(current_user)) -> dict:
    record = await db.fetch_one(
        "SELECT id, status, updated_at FROM records WHERE user_id = %s AND client_id = %s",
        (user["id"], client_id),
    )
    if record is None:
        raise HTTPException(status_code=404, detail="没有这条记录")
    if record["status"] in ("pending", "running"):
        response.status_code = 409
        return {"ok": False, "error": "still_processing"}
    if record["status"] == "done":
        gap = (_now() - record["updated_at"]).total_seconds()
        if gap < 30:
            response.status_code = 429
            return {"ok": False, "error": "cooldown", "retry_after": int(30 - gap) + 1}

    await db.execute("UPDATE documents SET is_current = false WHERE record_id = %s", (record["id"],))
    await db.execute(
        "UPDATE records SET status = 'pending', error = NULL, updated_at = now() WHERE id = %s",
        (record["id"],),
    )
    return {"ok": True, "status": "pending"}


@app.post("/v1/records/{client_id}/render-error")
async def render_error(client_id: str, payload: RenderErrorIn, user: dict = Depends(current_user)) -> dict:
    await db.execute(
        """
        UPDATE records SET render_retry = render_retry + 1, updated_at = now()
        WHERE user_id = %s AND client_id = %s
        """,
        (user["id"], client_id),
    )
    log.warning("client render error client_id=%s detail=%s", client_id, payload.detail[:200])
    return {"ok": True}
