from psycopg.rows import dict_row
from psycopg_pool import AsyncConnectionPool

_pool: AsyncConnectionPool | None = None


async def init(dsn: str) -> None:
    global _pool
    _pool = AsyncConnectionPool(
        dsn,
        min_size=1,
        max_size=10,
        open=False,
        kwargs={"row_factory": dict_row, "autocommit": True},
    )
    await _pool.open()


async def close() -> None:
    if _pool is not None:
        await _pool.close()


async def fetch_all(sql: str, params: tuple = ()) -> list[dict]:
    assert _pool is not None
    async with _pool.connection() as conn:
        cur = await conn.execute(sql, params)
        return await cur.fetchall()


async def fetch_one(sql: str, params: tuple = ()) -> dict | None:
    rows = await fetch_all(sql, params)
    return rows[0] if rows else None


async def execute(sql: str, params: tuple = ()) -> int:
    """返回受影响行数。"""
    assert _pool is not None
    async with _pool.connection() as conn:
        cur = await conn.execute(sql, params)
        return cur.rowcount
