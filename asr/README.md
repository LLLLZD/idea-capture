# asr · 把转写做成一个 OpenAI 兼容服务

一个盒子：丢进去一个音频文件，出来中文文本。

协议是 OpenAI 的 `POST /v1/audio/transcriptions`，所以 Speakr 能接它，任何别的客户端也能接它——这两件事之间没有耦合。

引擎是 **FunASR**（阿里开源）+ `fun-asr-nano`，跑在我们自己的机器上。

## 为什么不用现成的云端转写

先看结论：**转写是整条链的地基，地基不能有概率性错误。**

拿同一段 201 秒的中文口述（技术推演，中英混说）逐个试过：

| 方案 | 耗时 | 中文质量 | 水印套话 |
| --- | --- | --- | --- |
| **Fun-ASR-Nano（本目录）** | 16.5 s（12×） | 好，标点完整，DeepSeek / Chrome / CRI 都对 | 无 |
| SenseVoice-Small | 2 s（100×） | 差（deep c、科目、你有身份证） | 无 |
| Whisper large-v3（别人搭的壳） | 66 s（3×） | 好 | **有**「请不吝点赞订阅转发打赏支持明镜与点点栏目」 |
| whisper-1（聚合网关的一条渠道） | 16.6 s | 只有 1/4 概率返回中文，另外 3/4 编出英文译文 | 有 |

两个结论：

1. **Whisper 系的「广告」不是壳的问题**。那句套话来自训练数据里的频道口播，遇到静音或音乐段就复读，
   换封装、换代理都没用，只能换模型。我方评测就是在别人的壳上撞到的。
2. **确定性比聪明更重要**。同一个文件连发 10 次，聚合网关返回四种结果（中文 / 英文 / 空文本 / 格式错误），
   而且都带 HTTP 200。四分之一概率编造，这个不能用。

SenseVoice 比 Nano 快 8 倍，但错词密集——长推演里的专有名词经不起这个，所以我们选了 Nano。
你要速度可以自己改 `--model sensevoice`。

## 部署

要求：一台能跑容器的机器；有 NVIDIA GPU 最好（`--gpus` 已配好），没有也能跑 CPU，只是慢。

```bash
cd asr
docker compose up -d --build       # 首次下模型（Nano + VAD 约 2.1 GB）到 ./models
docker compose ps                  # 等到 healthy
curl -s 127.0.0.1:8030/health
# {"status":"ok","device":"cuda","models_loaded":["fun-asr-nano"]}
```

权重落在 `./models/models/`，重建容器不会重下。

**基座镜像**：`Dockerfile` 的 `BASE` 必须自带 torch + torchaudio。默认值是我们机器上的本地标签
（那台是 linux-aarch64，PyPI 没有 aarch64 的 CUDA wheel，只能用镜像里预置的 torch）。
x86_64 + NVIDIA 的话换成任意一个带 CUDA 的 pytorch runtime 镜像：

```bash
BASE=pytorch/pytorch:<你挑的 cuda runtime tag> docker compose build
```

## 调用

```bash
curl -s 127.0.0.1:8030/v1/audio/transcriptions \
  -F file=@x.m4a -F model=fun-asr-nano -F response_format=json
```

接进 Speakr 只要三行（`../speakr/speakr.env`）：

```ini
TRANSCRIPTION_BASE_URL=http://127.0.0.1:8030/v1
TRANSCRIPTION_API_KEY=not-needed      # 本服务不鉴权，但该连接器校验非空
TRANSCRIPTION_MODEL=fun-asr-nano
```

**不要写 `ASR_BASE_URL`**——那个变量走的是 Speakr 的另一套连接器（`asr_endpoint`），协议不是 OpenAI 这套，接不上。
上游代码：`src/services/transcription/registry.py:406`（openai_whisper）与 `:242`（asr_endpoint）。

已验证它容忍 Speakr 会发的额外字段（`prompt` / `hotwords` / `temperature` / `language` 全部 200），
带不带 `Authorization` 头都行。`/v1/models` 还会列出 sensevoice / paraformer / moss-transcribe-diarize，
请求里指定 `model` 就能切。

## 跨机调用

默认只绑 `127.0.0.1`，够用且安全。Speakr 在另一台机器时，任选一条：

1. **SSH 隧道**（我们用的）：Speakr 那侧 `ssh -N -L 8030:127.0.0.1:8030 <asr-host>`，配置照旧写回环。
2. **反代 + key**：把端口暴露出去之前先加一层鉴权，这个服务本身没有鉴权。

别直接把 8030 开到公网——那等于给别人免费 GPU。

## 实测与边界

- 201 秒中文口述：16.5 / 16.1 / 17.2 / 18.1 秒（约 12× 实时），多次结果逐字一致。
- 4.67 秒短音频同样正确，并自动补了标点。
- 90 分钟录音按 12× 外推约 7～8 分钟，**未实测**（长音频要过 VAD 切块，实际可能打折）。
- 已知错词：「天生为为长沙也会设计的」——三套模型都听不出，属于录音本身含糊，不是模型退化。
- 不鉴权，靠只绑回环保护。

## 这个目录里有什么

| 文件 | 作用 |
| --- | --- |
| `Dockerfile` | funasr + funasr-server，`BASE` 可换 |
| `docker-compose.yml` | 8030 → 8000（回环）、权重卷、healthcheck、GPU 预留 |
