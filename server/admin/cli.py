"""运维命令：建用户、改密码、看用量、跑迁移。

用法（在 server 目录下）：
    python -m admin.cli migrate
    python -m admin.cli user-add zhangsan --name 张三 --password 初始密码
    python -m admin.cli user-passwd zhangsan --password 新密码
    python -m admin.cli user-disable zhangsan
    python -m admin.cli user-list
    python -m admin.cli usage --days 7
"""

import argparse
import asyncio
import os
import sys

import psycopg
from psycopg.rows import dict_row


def _dsn() -> str:
    dsn = os.environ.get("DATABASE_URL")
    if not dsn:
        sys.exit("缺少环境变量 DATABASE_URL")
    return dsn


async def _run(sql: str, params: tuple = (), fetch: bool = False):
    async with await psycopg.AsyncConnection.connect(_dsn(), row_factory=dict_row, autocommit=True) as conn:
        cur = await conn.execute(sql, params)
        return await cur.fetchall() if fetch else cur.rowcount


async def cmd_migrate(_args) -> None:
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    from app import db, migrate

    await db.init(_dsn())
    await migrate.run_all()
    await db.close()
    print("迁移与模板种子完成")


async def cmd_user_add(args) -> None:
    from app.security import hash_password

    password = args.password or input("初始密码: ")
    await _run(
        """
        INSERT INTO users (username, password_hash, display_name, role)
        VALUES (%s, %s, %s, %s)
        """,
        (args.username, hash_password(password), args.name or args.username, args.role),
    )
    print(f"已创建用户 {args.username}")


async def cmd_user_passwd(args) -> None:
    from app.security import hash_password

    password = args.password or input("新密码: ")
    changed = await _run(
        "UPDATE users SET password_hash = %s WHERE username = %s",
        (hash_password(password), args.username),
    )
    print("已更新" if changed else "没有这个用户")


async def cmd_user_toggle(args) -> None:
    status = "disabled" if args.action == "disable" else "active"
    changed = await _run("UPDATE users SET status = %s WHERE username = %s", (status, args.username))
    print(f"已设为 {status}" if changed else "没有这个用户")


async def cmd_user_list(_args) -> None:
    rows = await _run(
        """
        SELECT u.username, u.display_name, u.role, u.status, u.last_login_at,
               (SELECT count(*) FROM user_templates ut WHERE ut.user_id = u.id) AS template_count,
               (SELECT count(*) FROM records r WHERE r.user_id = u.id) AS record_count
        FROM users u ORDER BY u.id
        """,
        fetch=True,
    )
    if not rows:
        print("还没有用户")
        return
    for row in rows:
        print(
            "{username:<16} {display_name:<10} {role:<6} {status:<9} 模板{template_count} 记录{record_count} 最近登录 {last_login_at}".format(
                **(row | {"last_login_at": row["last_login_at"] or "-"})
            )
        )


async def cmd_usage(args) -> None:
    rows = await _run(
        """
        SELECT u.username,
               count(*) AS calls,
               sum(l.token_in) AS token_in,
               sum(l.token_out) AS token_out,
               sum(CASE WHEN l.ok THEN 0 ELSE 1 END) AS failures,
               round(avg(l.duration_ms)) AS avg_ms
        FROM usage_logs l JOIN users u ON u.id = l.user_id
        WHERE l.created_at > now() - make_interval(days => %s)
        GROUP BY u.username ORDER BY calls DESC
        """,
        (args.days,),
        fetch=True,
    )
    if not rows:
        print("这段时间没有调用")
        return
    for row in rows:
        print(
            "{username:<16} 调用{calls:<6} 入{token_in:<9} 出{token_out:<9} 失败{failures:<3} 平均{avg_ms}ms".format(**row)
        )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="idea-admin", description="idea-capture 运维命令")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("migrate", help="建表 + 导入模板").set_defaults(func=cmd_migrate)

    p = sub.add_parser("user-add", help="新建用户")
    p.add_argument("username")
    p.add_argument("--name", default="")
    p.add_argument("--role", default="user", choices=["user", "admin"])
    p.add_argument("--password", default="")
    p.set_defaults(func=cmd_user_add)

    p = sub.add_parser("user-passwd", help="改密码")
    p.add_argument("username")
    p.add_argument("--password", default="")
    p.set_defaults(func=cmd_user_passwd)

    for action in ("disable", "enable"):
        p = sub.add_parser(f"user-{action}", help=f"{action} 用户")
        p.add_argument("username")
        p.set_defaults(func=cmd_user_toggle, action=action)

    sub.add_parser("user-list", help="用户一览").set_defaults(func=cmd_user_list)

    p = sub.add_parser("usage", help="用量统计")
    p.add_argument("--days", type=int, default=7)
    p.set_defaults(func=cmd_usage)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    asyncio.run(args.func(args))


if __name__ == "__main__":
    main()
