# server

FastAPI + PostgreSQL。手机端把转写好的文本发上来，服务端判体裁、出稿、记用量。

## 目录

| 路径 | 说明 |
| --- | --- |
| `app/main.py` | 全部 HTTP 接口 |
| `app/refine.py` | 一次出稿：提示词 → 模型 → 体裁解析 → 图校验 → 落库 |
| `app/worker.py` | 进程内 worker，轮询 pending 出稿 |
| `app/prompt.py` | 提示词组装（骨架来自 `seeds/templates.py`） |
| `app/mermaid.py` | mermaid 校验，不合格的图整块丢掉 |
| `migrations/` | 纯 SQL 建表，启动时幂等执行 |
| `seeds/templates.py` | 体裁模板种子，改文案不改代码 |
| `admin/cli.py` | 建用户、改密码、看用量 |
| `deploy/` | systemd unit 与环境变量模板 |
| `mcp/` | 旧 MCP 服务，待改造（读的是 Speakr 接口） |

## 本地跑

```bash
python -m venv .venv && .venv/bin/pip install -r requirements.txt
export DATABASE_URL=postgresql://idea:pw@127.0.0.1:5432/idea
export JWT_SECRET=dev
export LLM_BASE_URL=http://127.0.0.1:11434/v1 LLM_MODEL=...
.venv/bin/uvicorn app.main:app --port 8892
```

表结构和模板种子在启动时自动建；也可以 `python -m admin.cli migrate`。

## 建人

```bash
python -m admin.cli user-add alice --name 爱丽丝 --password 初始密码
python -m admin.cli user-list
python -m admin.cli usage --days 7
```

## 安全相关的环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `ENABLE_API_DOCS` | `0` | 置 1 才暴露 `/docs`、`/redoc`、`/openapi.json`；生产保持关闭 |
| `JWT_TTL_HOURS` | `24` | 登录 token 有效期 |

登录失败限流在 `app/ratelimit.py`：同一用户名 10 分钟内错 5 次锁 5 分钟，返回 429 带 `Retry-After`；成功登录清零。单进程内存实现，重启即清空。

测试：`python tests/test_mermaid.py`、`python tests/test_login_limit.py`（纯 assert，不需要 pytest 和数据库）。

