# 实机记录

我们这套跑起来的真实形态、实测数据、踩过的坑。

这不是教程——教程在各模块目录里。**出问题时先翻这一页。**

## 运行形态

| 位置 | 跑什么 | 形态 |
| --- | --- | --- |
| 一台常开的 NAS（4 核 / 7 GB） | Speakr、桥接、MCP、frpc | host 网络 + user systemd + linger |
| 一台带 GPU 的机器 | ASR（FunASR） | 容器，只绑 `127.0.0.1:8030` |
| 一台只有流量的小服务器 | nginx + frps | 只做反代和打洞，不跑业务 |

端口：Speakr `8899`、ASR `8030`、MCP `8891`、frps `7000/7500`。

Speakr 依赖两条宿主回环链路：ASR（NAS → GPU 机器的 SSH 隧道）和 LLM（本机端口转发）。
**这两条不通时容器照跑，但转写 / 出稿会失败** —— 所以「服务在」不等于「能用」。

我们对拓扑**没有做任何假设**：换个顺序、合并到一台机器、把 ASR 换成云 API 都能跑，
只是改环境变量。上面这张表是我们当时手上有什么就用什么的结果，不是这套东西的要求。

## 实测数据

| 环节 | 输入 | 结果 |
| --- | --- | --- |
| 转写 | 201 秒中文口述 | 16.5～18.1 秒（约 12×），897 字，多次结果逐字一致 |
| 出稿 | 同一条转写 | 21～31 秒，2300～2770 字，八章齐全 |
| 端到端 | 网页上传 | 全流程 COMPLETED，容器内存约 457 MB |
| 自动推送 | 真实上传一条 4.7 秒录音 | 30 秒内建文档 + 收到私聊链接 |

## 踩过的坑

1. **拉不动镜像。** 那台机器上 6 个国内镜像站连 `hello-world` 都 EOF，但普通 HTTPS 能跑满 22 MB/s。
   解法：按 registry 协议用 curl 逐层下 blob、拼 `manifest.json`、`docker load`。
   坑中坑：token 接口对 python-urllib 的 UA 返回 403（用 curl 的 UA 就行），大 layer 会中途 stall（`curl -C -` 续传）。
2. **`SUMMARY_MAX_TOKENS` 代码默认 3000**，官方文档写 8000。不显式设，长推演被从中间截断且不报错。
3. **`ENABLE_INTERNAL_SHARING` 默认 false**，Share 弹窗会整个报
   `Failed to load shares: Internal sharing is not enabled`，连公开链接的按钮也点不出来
   （前端 `sharing.js:514` 无条件先拉内部共享列表，后端 `shares.py:376` 拒绝）。
4. **`ASR_BASE_URL` 是另一个连接器。** 要 OpenAI 协议就得用 `TRANSCRIPTION_*` 那三个变量，
   否则会走 `asr_endpoint`（`registry.py:242` vs `:406`），协议对不上。
5. **systemd 不展开 `~`。** 单元文件里写 `EnvironmentFile=~/bridge/bridge.env` 会直接起不来，
   要用 `%h`。同理 env 文件里的路径不展开波浪号，写绝对路径或让脚本默认值处理。
6. **一次上传吃满限流。** Speakr 约 520 单位/分钟，一次上传基本占满，连点两次就是 429。
7. **`dws` 在无头机器上装不上官方脚本**：`raw.githubusercontent.com` 被重置、npm 12 拦 postinstall。
   解法：npm 装包后手动跑包内 `install.js` 解出 vendor 二进制；登录必须带 `--device`。
8. **`nginx` 的 `client_max_body_size` 默认 1m**，长录音（40～80 MB）会 413。我们设 300m（应用侧上限 250 MB）。
9. **不传 `X-Forwarded-Proto`，公开分享会 403 `https_required`** —— 应用以为自己是 http。

## 端到端验收清单

搭完之后依次跑这六条，全绿才算能用：

1. 手机说 2 分钟 → 5 分钟内钉钉收到链接（转写 + 出稿 + 推送都在跑）；
2. 稿子的体裁判对（不是所有稿都长一个样）；
3. 稿子里出现你录音中确实说过的具体名词（证明是抽取，不是编造）；
4. 长推演（≥30 分钟）同一条录音只生成一篇稿，不人工拼；
5. agent 能通过 MCP 检索到这条稿；
6. 故意设错 token：上传被拒、MCP 401、桥接不推送 —— **失败要看得见**。

> 我们自己还没验第 4 条：30～90 分钟的真推演没跑过，长输入耗时、是否丢段、是否被
> `SUMMARY_MAX_TOKENS` 截断都未实测。这是这套东西最大的未知数。

## 还没做

- 真推演（30～90 分钟）压测。
- 摘要失败的通知（`recording.summary.failed` 现在静默，只在日志里）。
- `smoke-upload.sh` 没落成文件，上传冒烟一直是手工跑的。
- 采集端还在共享表单：自研 iOS App（两个 App Intent）能消掉最后那次手动分享，没做。
