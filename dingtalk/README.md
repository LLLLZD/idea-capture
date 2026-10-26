# dingtalk · 把稿子送到手机上

出口插槽。Speakr 每出一篇稿，就在你的钉钉个人空间写一篇文档，然后把链接单聊发给你。

**全程个人身份**：用的是 `dws`（钉钉官方 CLI）加你自己的 OAuth 登录态，
不需要建企业内部应用、不需要加机器人、不需要管理员授权，也不往群里刷消息。

换掉它很简单：你只要一个能拿到「新出稿」并把它推到你手机的方式，飞书、邮件、自建 App 都行。
本目录给的是我们跑通的那一种。

## 为什么是轮询而不是 webhook

Speakr 自带 webhook（`recording.summary.completed`），但投递带 SSRF 防护，回环和私网地址一律拒绝
（`src/services/webhook_dispatch.py`）。

桥接和 Speakr 同机，想用 webhook 就得先给内部服务开一个公网入口——**为了一条通知开公网，不划算**。

轮询只发出站请求：不开端口、不改反代、不扩大攻击面。代价是最多晚 `POLL_SECONDS` 秒，
以及失败摘要不会推送（只推成功出稿的）。

## 依赖

1. Speakr 在跑，且有一把 **read** 权限的 API token；
2. `dws` 二进制 + 一次个人授权（`dws auth login --device`，30 天 refresh token 自动续期）；
3. 一个目标文件夹的 nodeId（比如「我的文档 / IDEA」）。

## 部署

跑在宿主上而不是容器里——它要用 `dws` 二进制和你个人的 `~/.dws` 登录态。

```bash
mkdir -p ~/bridge/state
cp bridge.py ~/bridge/
cp bridge.env.template ~/bridge/bridge.env && chmod 600 ~/bridge/bridge.env
vi ~/bridge/bridge.env                      # 见下表

mkdir -p ~/.config/systemd/user
cp idea-bridge.service ~/.config/systemd/user/
loginctl enable-linger "$USER"              # 不开的话退出 SSH 服务就被停掉
systemctl --user daemon-reload && systemctl --user enable --now idea-bridge
```

**验证**（重放一条已经出稿的录音，立即写文档 + 发消息）：

```bash
cd ~/bridge && set -a && . ./bridge.env && set +a && python3 bridge.py --publish <recording_id>
python3 bridge.py --selftest      # 不联网的逻辑自检
tail -f ~/bridge/bridge.log
```

## 配置

| 变量 | 说明 |
| --- | --- |
| `SPEAKR_BASE` | Speakr 地址，同机就是 `http://127.0.0.1:8899` |
| `SPEAKR_SITE` | 网页地址，只用来拼「在 Speakr 里打开」的链接 |
| `SPEAKR_TOKEN` | read 权限 token。**别复用上传用的那把** |
| `DWS_BIN` | `dws` 路径，默认 `~/.local/bin/dws` |
| `DWS_USER_ID` | 单聊发给谁（你自己的钉钉 userId） |
| `DWS_FOLDER_ID` | 文档落在哪个文件夹 |
| `DWS_DOC_PREFIX` | 文档名前缀，默认 `IDEA 报告`。**含空格，env 里必须带引号** |
| `POLL_SECONDS` | 轮询间隔，默认 30 |
| `STATE_PATH` | 去重状态文件，默认 `~/bridge/state/state.json` |

env 文件由 systemd 读取，**里面的 `~` 不会被展开**，需要绝对路径；
不填则用脚本默认值（脚本内部会 expanduser）。

## 怎么改成飞书 / 别的出口

链路上只有两处跟钉钉有关，都在 `bridge.py` 里：

1. `dws()` —— 调 CLI 的那个包装（建文档、取链接、发单聊）；
2. `publish(rec)` —— 决定「一篇稿要做什么」，现在做的是「建文档 → 取 docUrl → 发链接」。

换平台就改这两处，其余（轮询、去重、状态文件、日志）不用动。

去重键是 `recording_id + summary 哈希`：重新出稿会再推一次，只改标题不会。

## 已知取舍

- 文档只能新建，不能原地改写：重新出稿会多出一篇，旧的那篇留着。
- 文档名 = 前缀 + Speakr 标题。同名录音再传一次会多出一篇同名文档，靠时间区分。
- `dws` 登录态 30 天到期后不会自愈，日志里出现 dws 报错就跑一次 `dws auth login --device`。
- 摘要失败不通知（`recording.summary.failed`），只推成功出稿的。

## 这个目录里有什么

| 文件 | 作用 |
| --- | --- |
| `bridge.py` | 轮询 + 去重 + 建文档 + 发单聊（`--selftest` / `--publish <id>` / `--once`） |
| `bridge.env.template` | 全部变量 + 那两处不能写 `~` 的说明 |
| `idea-bridge.service` | systemd --user 单元（用 `%h` 而不是 `~`） |
