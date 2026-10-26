"""Read-only MCP server over a Speakr instance: search and read transcribed ideas.

Design notes:
- Auth is a Bearer passthrough: the caller's token is verified against Speakr's own
  API (TTL cache), so no second credential store exists and revocation is immediate.
- Genre is carried by the summary's first line ("体裁：<genre> — <basis>"), written by
  the summary prompt. Listing therefore goes through /api/v1/search with the term
  "体裁" instead of reading every summary (no N+1, single request).
"""

from __future__ import annotations

import argparse
import asyncio
import contextvars
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Annotated

from pydantic import Field

SPEAKR_BASE = os.environ.get("SPEAKR_BASE", "http://127.0.0.1:8899").rstrip("/")
MCP_HOST = os.environ.get("MCP_HOST", "127.0.0.1")
MCP_PORT = int(os.environ.get("MCP_PORT", "8891"))
ALLOWED_HOSTS = [h.strip() for h in os.environ.get("MCP_ALLOWED_HOSTS", "capture.example.com,127.0.0.1:8891").split(",") if h.strip()]
AUTH_TTL = float(os.environ.get("MCP_AUTH_TTL", "60"))
SNIPPET_CHARS = 300
QUERY_CHARS = 500

TOKEN: contextvars.ContextVar[str | None] = contextvars.ContextVar("speakr_token", default=None)

GENRES = [
    {"genre": "推演/设计", "chars": "2000-3500", "when": "在脑子里推演方案、架构、实现路径"},
    {"genre": "待办/推进", "chars": "400-900", "when": "交代要做的事、进度、卡点"},
    {"genre": "会议/沟通", "chars": "600-1500", "when": "两人及以上往返对话、排障、谈判"},
    {"genre": "随记/日记", "chars": "500-1200", "when": "情绪、生活近况、一时感想"},
    {"genre": "口述/回忆", "chars": "800-2000", "when": "讲故事、讲过去的人和事"},
    {"genre": "演练/练习", "chars": "800-2000", "when": "模拟面试、发言、复盘练习"},
    {"genre": "其他", "chars": "600-1200", "when": "判不准体裁时的兜底"},
]


def build_search_query(query: str | None, genre: str | None) -> str:
    """Compose the search q: free text plus an optional genre term filter."""
    text = " ".join(part for part in [(query or "").strip(), f"体裁：{genre}" if genre else ""] if part)
    return (text or "体裁")[:QUERY_CHARS]


def parse_genre_line(text: str | None) -> tuple[str | None, str | None]:
    """Read '体裁：<genre> — <basis>' from the first line of a summary."""
    if not text:
        return (None, None)
    stripped = text.strip()
    if not stripped:
        return (None, None)
    first = stripped.splitlines()[0].strip()
    prefix = "体裁："
    if not first.startswith(prefix):
        return (None, None)
    rest = first[len(prefix) :].strip()
    genre, _, basis = rest.partition("—")
    genre = genre.strip()
    return (genre or None, basis.strip())


def shape_list(payload: dict) -> list[dict]:
    """Search results that carry a genre line -> idea stubs."""
    ideas = []
    for item in payload.get("results") or []:
        genre, basis = parse_genre_line(item.get("text"))
        ideas.append(
            {
                "recording_id": item.get("recording_id"),
                "title": item.get("title") or "",
                "date": (item.get("meeting_date") or "")[:10],
                "genre": genre or "未标注",
                "genre_basis": basis or "",
            }
        )
    return ideas


def shape_search(payload: dict) -> list[dict]:
    """Search hits -> compact rows with a truncated snippet."""
    rows = []
    for item in payload.get("results") or []:
        text = (item.get("text") or "").strip()
        if len(text) > SNIPPET_CHARS:
            text = text[:SNIPPET_CHARS] + "…"
        rows.append(
            {
                "recording_id": item.get("recording_id"),
                "title": item.get("title") or "",
                "date": (item.get("meeting_date") or "")[:10],
                "field": item.get("field") or "",
                "snippet": text,
                "score": item.get("score"),
            }
        )
    return rows


def shape_idea(payload: dict, include_transcript: bool) -> dict:
    """One recording -> the readable idea document."""
    summary = payload.get("summary") or ""
    genre, basis = parse_genre_line(summary)
    idea = {
        "recording_id": payload.get("id"),
        "title": payload.get("title") or "",
        "date": (payload.get("meeting_date") or "")[:10],
        "duration_seconds": payload.get("audio_duration"),
        "genre": genre or "未标注",
        "genre_basis": basis or "",
        "summary": summary,
    }
    if include_transcript:
        idea["transcription"] = payload.get("transcription") or ""
    return idea


class AuthCache:
    """Cache of token -> allowed, refreshed every ttl seconds."""

    def __init__(self, fetch, ttl: float = 60.0, clock=time.monotonic, max_entries: int = 256):
        self._fetch = fetch
        self._ttl = ttl
        self._clock = clock
        self._max = max_entries
        self._items: dict[str, tuple[float, bool]] = {}

    def check(self, token: str) -> bool:
        now = self._clock()
        hit = self._items.get(token)
        if hit is not None and now - hit[0] < self._ttl:
            return hit[1]
        allowed = bool(self._fetch(token))
        if len(self._items) >= self._max:
            self._items.clear()
        self._items[token] = (now, allowed)
        return allowed


def api_get(path: str, params: dict | None = None, token: str | None = None, timeout: float = 30.0) -> dict:
    url = SPEAKR_BASE + path
    if params:
        url += "?" + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None})
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def token_allowed(token: str) -> bool:
    """Fail closed: only a 200 from the recordings API proves a usable read token."""
    try:
        api_get("/api/v1/recordings", {"per_page": 1}, token, timeout=10.0)
        return True
    except urllib.error.HTTPError as exc:
        if exc.code in (401, 403):
            return False
        print(f"token check failed: HTTP {exc.code}", file=sys.stderr)
        return False
    except Exception as exc:
        print(f"token check failed: {exc}", file=sys.stderr)
        return False


def current_token() -> str:
    token = TOKEN.get()
    if not token:
        raise RuntimeError("no bearer token in request context")
    return token


async def fetch(path: str, params: dict | None = None) -> dict:
    token = current_token()
    return await asyncio.to_thread(api_get, path, params, token)


from mcp.server.mcpserver import MCPServer
from mcp.server.transport_security import TransportSecuritySettings

server = MCPServer(
    "idea-speakr",
    instructions=(
        "Read-only access to the owner's voice-note archive. Each recording is a spoken idea, "
        "transcribed and summarised into a Chinese document whose first line is "
        "体裁：<genre> — <basis>. Start with list_ideas to see what exists, search_ideas to find "
        "a topic, get_idea to read one in full. Content is the owner's own words: quote rather "
        "than invent, and never claim a decision the document does not state."
    ),
)


@server.tool(
    description=(
        "Search the owner's voice-note archive for a topic: matches titles, transcripts and summaries. "
        "Returns one row per hit with recording_id, title, date, the matched field and a snippet; pass "
        "recording_id to get_idea to read that document in full. Use this first when you need to know "
        "what the owner has already thought or said about something. Matching is keyword-based, not "
        "semantic, and every whitespace-separated term must appear in the same recording, so search the "
        "words the owner would actually say (e.g. 'comfy', '部署', '面试') rather than a question."
    )
)
async def search_ideas(
    query: Annotated[str, Field(description="Words to look for (1-500 chars); all terms must match the same recording.")],
    limit: Annotated[int, Field(description="Max hits to return, 1-50, default 10. One recording can produce several hits.")] = 10,
    genre: Annotated[
        str | None,
        Field(description="Optional genre filter such as 推演/设计, 待办/推进, 随记/日记. Call genres() for the full list."),
    ] = None,
) -> list[dict]:
    payload = await fetch("/api/v1/search", {"q": build_search_query(query, genre), "limit": max(1, min(limit, 50))})
    return shape_search(payload)


@server.tool(
    description=(
        "Overview of the archive without the document text: the most recent recordings with id, title, "
        "date and the genre the summariser assigned. Use it to see what exists before searching, or to "
        "browse by genre. Follow up with get_idea to read anything that looks relevant."
    )
)
async def list_ideas(
    limit: Annotated[int, Field(description="How many entries to return, newest first. 1-50, default 20.")] = 20,
    genre: Annotated[
        str | None,
        Field(description="Optional genre filter such as 口述/回忆, 演练/练习. Call genres() for the full list."),
    ] = None,
) -> list[dict]:
    payload = await fetch("/api/v1/search", {"q": build_search_query(None, genre), "limit": max(1, min(limit, 50))})
    return shape_list(payload)


@server.tool(
    description=(
        "Read one recording in full: title, date, duration, genre with its basis, and the summary document "
        "written from the transcript. That document is the owner's own reasoning condensed into prose - quote "
        "it rather than paraphrasing into something they did not say, and treat anything it marks as "
        "undecided (未定/卡点) as genuinely open. The first line is always '体裁：<genre> — <basis>'."
    )
)
async def get_idea(
    recording_id: Annotated[int, Field(description="Recording id from list_ideas or search_ideas.")],
    include_transcript: Annotated[
        bool,
        Field(description="Also return the raw ASR transcript (longer, contains recognition errors). Default false; ask only when the summary is not enough."),
    ] = False,
) -> dict:
    include = "summary,transcription" if include_transcript else "summary"
    payload = await fetch(f"/api/v1/recordings/{int(recording_id)}", {"include": include})
    return shape_idea(payload, include_transcript)


@server.tool(
    description=(
        "The genre taxonomy the summariser uses (7 buckets): each entry gives the genre name, the expected "
        "length in Chinese characters, and when it applies. Every summary's first line is "
        "'体裁：<genre> — <basis>'. Call this before filtering list_ideas or search_ideas by genre."
    )
)
def genres() -> list[dict]:
    return GENRES


class AuthMiddleware:
    """Bearer check in front of the Streamable HTTP app; stashes the token for tools."""

    def __init__(self, app, cache: AuthCache):
        self.app = app
        self.cache = cache

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)

        if scope.get("path") == "/health":
            return await _json(send, 200, {"ok": True, "speakr": SPEAKR_BASE})

        token = _bearer(scope)
        if not token or not await asyncio.to_thread(self.cache.check, token):
            return await _json(send, 401, {"error": "invalid_token", "error_description": "Bearer token required"})

        reset = TOKEN.set(token)
        try:
            await self.app(scope, receive, send)
        finally:
            TOKEN.reset(reset)


def _bearer(scope) -> str | None:
    for key, value in scope.get("headers") or []:
        if key == b"authorization":
            text = value.decode("latin-1").strip()
            if text.lower().startswith("bearer "):
                return text[7:].strip() or None
    return None


async def _json(send, status: int, body: dict):
    payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
    await send(
        {
            "type": "http.response.start",
            "status": status,
            "headers": [
                (b"content-type", b"application/json; charset=utf-8"),
                (b"content-length", str(len(payload)).encode()),
            ],
        }
    )
    await send({"type": "http.response.body", "body": payload})


def build_app():
    transport_security = TransportSecuritySettings(
        enable_dns_rebinding_protection=True,
        allowed_hosts=ALLOWED_HOSTS,
        allowed_origins=[],
    )
    inner = server.streamable_http_app(stateless_http=True, transport_security=transport_security)
    return AuthMiddleware(inner, AuthCache(token_allowed, ttl=AUTH_TTL))


def selftest(token: str) -> int:
    """Exercise the read paths against the configured Speakr instance."""
    if not token_allowed(token):
        print("FAIL token rejected by Speakr")
        return 1
    listing = shape_list(api_get("/api/v1/search", {"q": build_search_query(None, None), "limit": 5}, token))
    print(f"list_ideas -> {len(listing)} stubs")
    if not listing:
        print("OK (archive empty)")
        return 0
    first_id = listing[0]["recording_id"]
    idea = shape_idea(api_get(f"/api/v1/recordings/{first_id}", {"include": "summary"}, token), False)
    print(f"get_idea({first_id}) -> genre={idea['genre']}, {len(idea['summary'])} chars")
    print("OK")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="idea-speakr MCP server")
    parser.add_argument("--selftest", metavar="TOKEN", help="run read-path checks with this Speakr token, then exit")
    args = parser.parse_args(argv)
    if args.selftest:
        return selftest(args.selftest)

    import uvicorn

    print(f"idea-speakr MCP on {MCP_HOST}:{MCP_PORT} -> {SPEAKR_BASE}", flush=True)
    uvicorn.run(
        build_app(),
        host=MCP_HOST,
        port=MCP_PORT,
        log_level=os.environ.get("MCP_LOG_LEVEL", "info").lower(),
        access_log=False,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())