"""出稿 worker：单进程内轮询 pending，claim 之后交给 refine。"""

import asyncio
import logging

from . import db
from .config import Config
from .refine import refine_record

log = logging.getLogger(__name__)

CLAIM_SQL = """
UPDATE records SET status = 'running', updated_at = now()
WHERE id = (
    SELECT id FROM records WHERE status = 'pending' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED
)
RETURNING id
"""


async def recover() -> int:
    """进程重启时，把上次没跑完的 running 重新排回 pending。"""
    return await db.execute(
        "UPDATE records SET status = 'pending', updated_at = now() WHERE status = 'running'"
    )


async def run(cfg: Config, stop: asyncio.Event, poll_sec: float = 2.0) -> None:
    log.info("refine worker started")
    while not stop.is_set():
        try:
            row = await db.fetch_one(CLAIM_SQL)
            if row is None:
                try:
                    await asyncio.wait_for(stop.wait(), timeout=poll_sec)
                except asyncio.TimeoutError:
                    pass
                continue
            await refine_record(row["id"], cfg)
        except Exception:  # noqa: BLE001 - 单条失败不能拖垮整个 worker
            log.exception("worker iteration failed")
            await asyncio.sleep(poll_sec)
    log.info("refine worker stopped")
