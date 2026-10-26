# ASR 服务：FunASR 自带的 OpenAI 兼容服务（funasr-server）
#
# BASE 必须自带 torch 和 torchaudio —— funasr 只装它自己的东西，不负责装 torch。
#
# 我们的默认值是本机已有镜像的本地标签：我们那台机器是 GB10（linux-aarch64 / sm_121），
# PyPI 不发布 aarch64 的 CUDA wheel，所以只能用镜像里预置的 torch。
# 别人在 x86_64 + NVIDIA 上，换成任意一个带 CUDA 的 pytorch runtime 镜像即可：
#   BASE=pytorch/pytorch:<你挑的 cuda runtime tag> docker compose build
# 没有 GPU 也行，把 --device 改成 cpu（慢很多，见 README 的速度对比）。
ARG BASE=asr-base:pinned
FROM ${BASE}

RUN pip install --no-cache-dir --index-url https://mirrors.aliyun.com/pypi/simple/ \
      funasr==1.4.16 python-multipart

ENV MODELSCOPE_CACHE=/models/ms

EXPOSE 8000

CMD ["funasr-server", "--host", "0.0.0.0", "--port", "8000", \
     "--device", "cuda", "--model", "fun-asr-nano"]
