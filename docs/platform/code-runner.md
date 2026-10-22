# 代码运行器（yudream-code-runner）

AI 工作区「编码」模式的 `workspace.run` 在独立容器中执行 Python 3 / Node 20 代码。运行器只用 Python 标准库实现（`yudream-code-runner/runner.py`），镜像预装常用 Python 包（默认 `numpy pandas openpyxl pyyaml`，构建参数 `PY_PACKAGES` 可调）。

## 运行模型

- 用户工作区的元数据在数据库、内容在对象存储；运行器**不持有任何用户数据**。每次运行由后端把需要的文件（`inputs` + 入口，合计 ≤ 20 MB、≤ 200 个）随请求发给运行器，运行器写入临时目录、执行、收集新建或改动的文件回传，随即删除临时目录。
- 产物由后端写入工作区输出目录（缺省 `tmp/runs/<时间>`，随 tmp/ 定期清理），**不覆盖原文件**；来源记为 AI，可在文件管理器查看版本与恢复。
- 每次运行都要经过：编码模式 + `platform:chat:workspace:run` 权限 + 运行器可用（部署开关 + 健康检查）+ 用户在确认卡上逐次批准。

## 接口

- `GET /health`：`{status, languages, python, node}`
- `POST /run`：`{language: python|node, entry?|code?, files: [{path, content(base64)}], args, stdin, env, timeoutSeconds, memoryMb}` → `{exitCode, timedOut, durationMillis, stdout, stderr, outputs: [{path, content}], outputsTruncated}`
- 两者都要求请求头 `X-Runner-Token`；并发超过 `RUNNER_MAX_CONCURRENT` 返回 429。

## 限制

| 项 | 默认 |
|---|---|
| 墙钟超时 | 请求值，1–120 秒（`RUNNER_MAX_TIMEOUT_SECONDS`），到时杀整个进程组 |
| CPU 时间 / 内存 | 超时 + 1 秒；Python 地址空间 512 MB（`RUNNER_MEMORY_MB`），Node 用堆上限 + 容器内存上限 |
| 单文件 / 回传合计 | 10 MB / 20 MB、200 个文件 |
| stdout / stderr | 各 64 KB，超出截断 |
| 进程数 | `RUNNER_MAX_PROCESSES`（256）+ 容器 pids 上限 |

## 部署（Docker Compose）

`.env`：

```env
CODE_RUNNER_ENABLED=true
CODE_RUNNER_TOKEN=<随机长令牌，后端与运行器共用>
```

启动：`docker compose --profile code-runner up -d`。运行器只接入 `internal: true` 的 `yudream-runner` 网络（无外网出口，也接触不到数据库与中间件），只读根文件系统、`/tmp` 为 512 MB tmpfs、去掉全部 capability、非 root（uid 10001），容器限 1 CPU / 1.5 GB / 256 进程。

未启用时后端不创建运行器端口，`workspace.run` 不出现；`GET /api/platform/chat/workspace/runner` 返回不可用原因。工作流里的 Python 节点 / 自定义 Python 工具仍在后端本机执行（可能需要访问外部网络），不经运行器。

本地开发：`RUNNER_TOKEN=xxx RUNNER_HOST=127.0.0.1 python3 -I yudream-code-runner/runner.py`，后端以 `CODE_RUNNER_ENABLED=true CODE_RUNNER_BASE_URL=http://127.0.0.1:8090 CODE_RUNNER_TOKEN=xxx` 启动。测试：`python3 -I yudream-code-runner/test_runner.py`。
