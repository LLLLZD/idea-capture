"""纯 SQL 迁移 + 模板种子。启动时幂等执行，也可以单独跑。"""

import logging
import pathlib

from seeds.templates import TEMPLATES

from . import db

log = logging.getLogger(__name__)
MIGRATIONS = pathlib.Path(__file__).resolve().parent.parent / "migrations"

SEED_SQL = """
INSERT INTO templates (key, name, criterion, skeleton_md, sample_md, word_range, is_builtin, sort)
VALUES (%(key)s, %(name)s, %(criterion)s, %(skeleton_md)s, %(sample_md)s, %(word_range)s, %(is_builtin)s, %(sort)s)
ON CONFLICT (key) DO UPDATE
SET name = excluded.name,
    criterion = excluded.criterion,
    skeleton_md = excluded.skeleton_md,
    sample_md = excluded.sample_md,
    word_range = excluded.word_range,
    is_builtin = excluded.is_builtin,
    sort = excluded.sort,
    version = templates.version + 1,
    updated_at = now()
WHERE templates.name IS DISTINCT FROM excluded.name
   OR templates.criterion IS DISTINCT FROM excluded.criterion
   OR templates.skeleton_md IS DISTINCT FROM excluded.skeleton_md
   OR templates.sample_md IS DISTINCT FROM excluded.sample_md
   OR templates.word_range IS DISTINCT FROM excluded.word_range
   OR templates.is_builtin IS DISTINCT FROM excluded.is_builtin
   OR templates.sort IS DISTINCT FROM excluded.sort
"""


async def apply_migrations() -> None:
    for path in sorted(MIGRATIONS.glob("*.sql")):
        await db.execute(path.read_text(encoding="utf-8"))
        log.info("migration applied: %s", path.name)


async def seed_templates() -> None:
    for item in TEMPLATES:
        row = dict(item)
        row.setdefault("is_builtin", False)
        row.setdefault("word_range", "")
        row.setdefault("sample_md", "")
        await db.execute(SEED_SQL, row)
    log.info("templates seeded: %d", len(TEMPLATES))


async def run_all() -> None:
    await apply_migrations()
    await seed_templates()
