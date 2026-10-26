# speakr · 产品本体

上游：[**murtaza-nasir/speakr**](https://github.com/murtaza-nasir/speakr)（我们自己一行都没改）

本目录只放两样东西：**我们实际在用的部署文件**，以及**我们动过的三个设置**。
不想读上游仓库的人照着这页就能跑；想深究的人顺着上面的链接过去即可。

## 它是什么

一个自托管的语音归档产品：网页上传录音 → 自动转写 → 自动按模板出稿 → 存档、搜索、分享、API。
转写和出稿都不写死，各是一个「填地址」的槽位（任何 OpenAI 兼容服务都能接）。
这就是它能当整条链地基的原因——**产品已经做好了，我们只决定它接谁**。

上游的 REST API 也是下游两个插槽的入口：`../dingtalk/` 和 `../mcp/` 都只是它的客户端。

## 部署

```bash
mkdir -p ~/speakr && cd ~/speakr
cp <本仓库>/speakr/docker-compose.yml .
cp <本仓库>/speakr/speakr.env.template speakr.env
chmod 600 speakr.env
openssl rand -hex 32          # 填进 SECRET_KEY
vi speakr.env                 # 至少改 SECRET_KEY / TRANSCRIPTION_* / TEXT_MODEL_*
docker compose up -d
```

**验证**：

```bash
curl -s -o /dev/null -w '%{http_code}\n' 127.0.0.1:8899/login     # 期望 200
docker logs --tail 30 speakr | grep -i 'LLM client initialized'   # 期望有这一行
```

建管理员：

```bash
docker exec -i -e SKIP_EMAIL_DOMAIN_CHECK=true speakr \
  python scripts/create_admin.py <<< $'admin\nadmin@example.com\n<密码>\n<密码>'
```

登录后进 `/admin/settings`，把下面三项设上。

## 我们动过的三个设置

| 设置 | 值 | 为什么 |
| --- | --- | --- |
| `transcript_length_limit` | `60000` | 默认 30000。一分钟口述约 270 字，90 分钟约 2.4 万字，默认值会把后半段挡在提示词外面 |
| 默认摘要提示词 | [`summary-prompt.md`](summary-prompt.md) 全文 | 体裁路由的全部逻辑在这里，见下 |
| 默认标题提示词 | 见下 | 不改就一直是英文标题 |

另外把账号的 `output_language` 设成 `Chinese`——代码会把它拼成
"Please provide the title in Chinese."，只改模板不设它，标题仍可能回英文。

标题提示词：

```
用不超过 12 个汉字给这条录音起标题。要求：
1. 直接点出主题，不要"关于…的讨论"这种句式；
2. 优先使用录音里出现的具体人名、项目名、产品名；
3. 只输出标题本身，不要引号、不要句末标点。
```

## 为什么摘要提示词要自己判体裁

上游给的是「一份模板套所有录音」。实测套不住：深夜的情感记录被写成「验收条件：未定」，
家庭口述史被写成「档案工程」，3 分钟的随记被撑成 2300 字的项目书。

`summary-prompt.md` 是 v3：模型先判体裁（7 类），再按该类骨架成稿，篇幅区间也按体裁定死。
稿子第一行固定是 `体裁：<类型> — <判定依据>`，这一行同时是下游的元数据来源
（MCP 用它做筛选，人用它一眼看出判错没有）。**改提示词时别丢这一行。**

实测效果（同一批 6 条录音，v2 → v3）：

| 录音 | v2 | v3 | 字数 |
| --- | --- | --- | --- |
| 深夜情感记录 | 项目稿（验收条件：未定） | 随记/日记 | 2589 → 722 |
| 家庭口述史 | 档案工程 | 口述/回忆 | 2311 → 1227 |
| 面试演练 | 逼出「验收条件」 | 演练/练习 | 3132 → 1190 |

## 三个会绊住你的坑

1. **`SUMMARY_MAX_TOKENS` 代码默认 3000**，官方文档写 8000。不显式设，长推演会被从中间截断，
   而且不报错——末尾突然断句就是它。模板里已设 8000。
2. **`ENABLE_INTERNAL_SHARING` 默认 false**。Share 弹窗会无条件先拉内部共享列表，那个请求 403，
   前端把整个弹窗报成 `Failed to load shares: Internal sharing is not enabled`，连公开链接也点不出来。
   模板里已设 true。
3. **`network_mode: host`**。ASR 和 LLM 通常只监听宿主回环，桥接网络访问不到。
   如果你的两个上游是公网地址，可以删掉这行、改成 `ports: ["8899:8899"]`。

## 换掉它需要什么

不用 Speakr 也能有这条链，只要你拿的东西满足三个条件：

- 一个能接 `POST /v1/audio/transcriptions` 的转写入口；
- 一个能接 `POST /v1/chat/completions` 的出稿入口；
- 一个能用 token 读回「标题 + 正文 + 时间」的 REST API（下游两个插槽只认这个）。

## 这个目录里有什么

| 文件 | 作用 |
| --- | --- |
| `docker-compose.yml` | lite 镜像、host 网络、uploads/instance 两个卷 |
| `speakr.env.template` | 全部必需变量 + 上面那几个坑的注释 |
| `summary-prompt.md` | 摘要提示词 v3 全文（直接粘进后台） |
