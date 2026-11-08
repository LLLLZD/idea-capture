# mcp · 让你的 agent 直接读你的归档

只读 MCP 服务。它把 Speakr 里「已转写 + 已出稿」的想法暴露成 4 个工具，
任何支持 Streamable HTTP 的 agent 都能检索、读取——不用翻网页，也不用复制粘贴。

这是四个插槽里唯一**我们自己写的代码**（其余都是别人开源的东西 + 我们的配置）。
也因此它能单独用：不装 ASR、不配钉钉，只要有 Speakr（或任何实现了同样 REST API 的东西）。

## 两种用法

| 场景 | 怎么跑 | 适合谁 |
| --- | --- | --- |
| Speakr 和 agent 在同一台机器 | 直接 `python idea_mcp.py`，客户端连 `http://127.0.0.1:8891/mcp` | 自己电脑上全套 |
| agent 在别的设备 | 反代出 HTTPS（nginx + TLS），或 frp 打洞 | 手机 / 笔记本 / 多台 agent 共用一份归档 |

公网那层（域名、证书、frp）是**你自己的事**——我们只是给了一条实测过的路，见下面「公网接入」。

## 鉴权

没有第二套凭据。调用方带 `Authorization: Bearer <speakr-token>`，服务把它透传给
`GET /api/v1/recordings?per_page=1` 验一次，结果缓存 `MCP_AUTH_TTL` 秒（默认 60）。

好处有三条：**权限天然等于那把 token 的 scope**（给 read 就只读）、**吊销立即生效**、**服务自身不存任何密钥**。
坏处只有一条：每个请求多一次本机回环调用（约 2 ms），可以通过调大 TTL 摊薄。

**专门建一把 token**：网页 Settings → API Tokens，只勾 `read`，命名 `mcp-ideas`。
不要复用上传用的 Integration token。

## 工具

| 工具 | 作用 |
| --- | --- |
| `search_ideas(query, limit=10, genre=None)` | 跨标题 / 转写 / 摘要全文搜索，返回 id + 片段 |
| `list_ideas(limit=20, genre=None)` | 最近的条目（id / 标题 / 日期 / 体裁），不含正文 |
| `get_idea(recording_id, include_transcript=False)` | 读一篇完整稿，可选带原始转写 |
| `genres()` | 体裁分类表与字数区间 |

体裁来自摘要第一行 `体裁：<类型> — <判定依据>`（提示词 v3 的输出格式）。

`list_ideas` 因此走 `q=体裁` 的搜索而不是逐条拉摘要——一次请求，不 N+1。

## 部署

```bash
python3 -m venv ~/mcp-venv
~/mcp-venv/bin/pip install 'mcp>=1.9'          # 实测 2.3.0

mkdir -p ~/mcp && cp idea_mcp.py mcp.env.template ~/mcp/
cd ~/mcp && cp mcp.env.template mcp.env && chmod 600 mcp.env
vi mcp.env                                      # SPEAKR_BASE / MCP_ALLOWED_HOSTS

cp idea-mcp.service ~/.config/systemd/user/
systemctl --user daemon-reload && systemctl --user enable --now idea-mcp
```

**验证**：

```bash
curl -s 127.0.0.1:8891/health                                   # 不需要 token
~/mcp-venv/bin/python ~/mcp/idea_mcp.py --selftest <read-token>  # 打真库，不经过网络
```

单测**不入库**——断言里写的是真实归档内容（标题、体裁行）。要跑就放 `mcp/test/`：

```bash
cd ~/mcp && ~/mcp-venv/bin/python -m unittest test.test_idea_mcp -v
```

## 公网接入

反代本身就是个普通 location，但有四行不能省——它们解决的都是实测撞到的问题：

```nginx
location ^~ /mcp {
    proxy_pass http://127.0.0.1:8891;
    # Host 必须与 MCP_ALLOWED_HOSTS 完全一致，否则 SDK 的 DNS rebinding 防护直接回 421
    proxy_set_header Host your-domain.example.com;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    # 别继承外层 location 的 $connection_upgrade，事件流会断
    proxy_set_header Connection "";
    proxy_http_version 1.1;
    # 不关缓冲，SSE 会被 nginx 攒住，客户端看到的是「服务没响应」
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
}
```

这个 location 要放在 `location ^~ /` **之前**，否则请求会被那条通配规则吃掉。

`MCP_ALLOWED_HOSTS` 必须**逐个**列出客户端会带的 Host（域名、`127.0.0.1:8891`）——
这是 SDK 的 DNS rebinding 防护，多一个空格都不算匹配。

没有公网服务器也能出：`frpc.toml.template` 是走 frp TCP 隧道的实测配置，
它把本机 8891 映射到 frps 的 8891，再由那边的 nginx 接。

## 客户端配置

DSH / 任何接受静态 header 的客户端：

```json
{"mcpServers": {"idea-speakr": {"type": "http",
  "url": "https://your-domain.example.com/mcp",
  "headers": {"Authorization": "Bearer <read-token>"}}}}
```

Claude Code：`--transport http --url ... --header "Authorization: Bearer <read-token>"`。

服务端不实现 OAuth 发现——收到 401 只回 `invalid_token`，不会引导客户端去授权。
静态 header 是唯一方式，这也意味着**任何拿到这把 token 的人都能读你全部归档**。

它是只读的，但里面是你家的事，别乱发。

## 边界

- 只读：没有写 / 删 / 建。要写得走 Speakr 网页或桥接。
- `search_ideas` 是关键词模式（没配 embedding，语义检索不可用）。
- 单篇 1–3 千字，一次列 20 条，大结果由客户端截断。
- nginx 层没加限流（要动全局 http 块），需要自己加 `limit_req`。

## 这个目录里有什么

| 文件 | 作用 |
| --- | --- |
| `idea_mcp.py` | 服务本体：4 个工具 + Bearer 透传 + `--selftest` |
| `test/` | 16 个单测（查询拼装、体裁解析、返回整形、鉴权缓存）。**不入库**，见上 |
| `mcp.env.template` | 全部变量（里面没有密钥） |
| `idea-mcp.service` | systemd --user 单元（用 `%h`） |
| `frpc.toml.template` | 没有公网服务器时的 TCP 隧道配置 |
