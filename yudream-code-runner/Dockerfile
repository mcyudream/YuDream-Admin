# YuDream 代码运行器：Python 3.12 + Node 20，供 AI 工作区按需执行代码（不常驻用户数据）。
# 常用 Python 包可按需调整：docker build --build-arg PY_PACKAGES="numpy pandas" .；传空串得到最小镜像
ARG REGISTRY=swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/library
FROM ${REGISTRY}/node:20-bookworm-slim AS node

FROM ${REGISTRY}/python:3.12-slim-bookworm
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple
ARG PY_PACKAGES="numpy pandas openpyxl pyyaml"
COPY --from=node /usr/local/bin/node /usr/local/bin/node
RUN if [ -n "${PY_PACKAGES}" ]; then pip install --no-cache-dir --index-url "${PIP_INDEX_URL}" ${PY_PACKAGES}; fi \
    && useradd --create-home --uid 10001 runner
COPY runner.py /opt/runner/runner.py

ENV RUNNER_HOST=0.0.0.0 \
    RUNNER_PORT=8090 \
    RUNNER_WORK_ROOT=/tmp/jobs \
    PYTHONDONTWRITEBYTECODE=1
EXPOSE 8090
USER runner
CMD ["python3", "-I", "/opt/runner/runner.py"]
