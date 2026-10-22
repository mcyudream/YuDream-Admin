"""YuDream 代码运行器：为 AI 工作区执行 Python / Node 代码的独立容器服务（仅标准库）。

每次运行：
- 在临时目录写入调用方给出的工作区文件与入口脚本；
- 以资源限制（CPU 时间、内存、单文件大小、进程数、打开文件数）和墙钟超时运行；
- 收集新建或改动的文件回传，随后删除临时目录。

隔离由部署承担：容器只接入无外网的内部网络、只读根文件系统、tmpfs、去掉全部 capability、
非 root 用户。服务本身用共享令牌（X-Runner-Token）鉴权，不持久化任何数据。
"""

import base64
import hashlib
import json
import os
import resource
import shutil
import signal
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("RUNNER_TOKEN", "")
PORT = int(os.environ.get("RUNNER_PORT", "8090"))
HOST = os.environ.get("RUNNER_HOST", "0.0.0.0")
WORK_ROOT = os.environ.get("RUNNER_WORK_ROOT", tempfile.gettempdir())
MAX_CONCURRENT = int(os.environ.get("RUNNER_MAX_CONCURRENT", "2"))
MAX_REQUEST_BYTES = int(os.environ.get("RUNNER_MAX_REQUEST_MB", "40")) * 1024 * 1024
MAX_TIMEOUT_SECONDS = int(os.environ.get("RUNNER_MAX_TIMEOUT_SECONDS", "120"))
DEFAULT_MEMORY_MB = int(os.environ.get("RUNNER_MEMORY_MB", "512"))
MAX_PROCESSES = int(os.environ.get("RUNNER_MAX_PROCESSES", "256"))
MAX_OUTPUT_CHARS = 64 * 1024
MAX_FILE_BYTES = 10 * 1024 * 1024
MAX_OUTPUT_FILES = 200
MAX_OUTPUT_TOTAL = 20 * 1024 * 1024
SKIPPED_DIRS = {"__pycache__", "node_modules", ".cache", ".npm"}

LANGUAGES = {
    # Python 用地址空间上限限制内存；V8 启动即保留大段虚拟地址，Node 改用堆上限 + 容器内存限制
    "python": {"command": [sys.executable, "-B", "-u"], "default_entry": "main.py", "address_limit": True},
    "node": {"command": ["node"], "default_entry": "main.js", "address_limit": False},
}

slots = threading.BoundedSemaphore(MAX_CONCURRENT)


class RunError(Exception):
    def __init__(self, status, message):
        super().__init__(message)
        self.status = status
        self.message = message


def safe_relative(path):
    """工作区相对路径：拒绝绝对路径、..、反斜杠与控制字符。"""
    if not isinstance(path, str) or not path or len(path) > 240:
        raise RunError(400, f"文件路径无效：{path!r}")
    if path.startswith("/") or "\\" in path or any(ord(c) < 32 or ord(c) == 127 for c in path):
        raise RunError(400, f"文件路径无效：{path}")
    parts = path.split("/")
    if any(part in ("", ".", "..") for part in parts):
        raise RunError(400, f"文件路径无效：{path}")
    return path


def clip(text):
    return text if len(text) <= MAX_OUTPUT_CHARS else text[:MAX_OUTPUT_CHARS] + "\n...输出已截断..."


def digest(path):
    with open(path, "rb") as handle:
        return hashlib.sha256(handle.read()).hexdigest()


def limits(timeout_seconds, memory_mb, address_limit):
    def apply():
        os.setsid()
        resource.setrlimit(resource.RLIMIT_CPU, (timeout_seconds + 1, timeout_seconds + 2))
        if address_limit:
            memory = memory_mb * 1024 * 1024
            resource.setrlimit(resource.RLIMIT_AS, (memory, memory))
        resource.setrlimit(resource.RLIMIT_FSIZE, (MAX_FILE_BYTES, MAX_FILE_BYTES))
        resource.setrlimit(resource.RLIMIT_NOFILE, (256, 256))
        resource.setrlimit(resource.RLIMIT_NPROC, (MAX_PROCESSES, MAX_PROCESSES))
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    return apply


def run_job(payload):
    language = payload.get("language")
    if language not in LANGUAGES:
        raise RunError(400, "language 只支持 python 或 node")
    spec = LANGUAGES[language]
    timeout_seconds = max(1, min(int(payload.get("timeoutSeconds") or 30), MAX_TIMEOUT_SECONDS))
    memory_mb = max(64, min(int(payload.get("memoryMb") or DEFAULT_MEMORY_MB), 2048))
    files = payload.get("files") or []
    if not isinstance(files, list) or len(files) > 500:
        raise RunError(400, "files 需为不超过 500 项的列表")
    code = payload.get("code")
    entry = payload.get("entry") or (None if code is None else spec["default_entry"])
    if not entry:
        raise RunError(400, "需要 entry（工作区中的入口文件）或 code（直接运行的代码）")
    entry = safe_relative(entry)
    args = [str(arg) for arg in (payload.get("args") or [])][:20]
    stdin = payload.get("stdin") or ""
    env_extra = {str(k): str(v) for k, v in (payload.get("env") or {}).items() if str(k).isidentifier()}

    job_dir = tempfile.mkdtemp(prefix="job-", dir=WORK_ROOT)
    try:
        before = {}
        for item in files:
            relative = safe_relative(item.get("path"))
            target = os.path.join(job_dir, relative)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, "wb") as handle:
                handle.write(base64.b64decode(item.get("content") or ""))
            before[relative] = digest(target)
        if code is not None:
            target = os.path.join(job_dir, entry)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, "w", encoding="utf-8") as handle:
                handle.write(str(code))
            before[entry] = digest(target)
        if not os.path.isfile(os.path.join(job_dir, entry)):
            raise RunError(400, f"入口文件不存在：{entry}")

        env = {
            "PATH": os.environ.get("PATH", "/usr/local/bin:/usr/bin:/bin"),
            "HOME": job_dir,
            "TMPDIR": job_dir,
            "LANG": "C.UTF-8",
            "PYTHONIOENCODING": "utf-8",
            "PYTHONDONTWRITEBYTECODE": "1",
            "NODE_OPTIONS": f"--max-old-space-size={max(32, memory_mb // 2)}",
            **env_extra,
        }
        started = time.monotonic()
        process = subprocess.Popen(
            spec["command"] + [entry] + args,
            cwd=job_dir,
            env=env,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            preexec_fn=limits(timeout_seconds, memory_mb, spec["address_limit"]),
        )
        timed_out = False
        try:
            stdout, stderr = process.communicate(stdin.encode("utf-8"), timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            timed_out = True
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            stdout, stderr = process.communicate()
        duration = int((time.monotonic() - started) * 1000)

        outputs, total, truncated = [], 0, False
        for root, dirs, names in os.walk(job_dir):
            dirs[:] = sorted(d for d in dirs if d not in SKIPPED_DIRS and not os.path.islink(os.path.join(root, d)))
            for name in sorted(names):
                full = os.path.join(root, name)
                if os.path.islink(full) or not os.path.isfile(full):
                    continue
                relative = os.path.relpath(full, job_dir).replace(os.sep, "/")
                size = os.path.getsize(full)
                if relative in before and size <= MAX_FILE_BYTES and before[relative] == digest(full):
                    continue
                if size > MAX_FILE_BYTES or len(outputs) >= MAX_OUTPUT_FILES or total + size > MAX_OUTPUT_TOTAL:
                    truncated = True
                    continue
                with open(full, "rb") as handle:
                    outputs.append({"path": relative, "content": base64.b64encode(handle.read()).decode("ascii")})
                total += size

        return {
            "exitCode": -1 if timed_out else process.returncode,
            "timedOut": timed_out,
            "durationMillis": duration,
            "stdout": clip(stdout.decode("utf-8", errors="replace")),
            "stderr": clip(stderr.decode("utf-8", errors="replace")),
            "outputs": outputs,
            "outputsTruncated": truncated,
        }
    finally:
        shutil.rmtree(job_dir, ignore_errors=True)


def versions():
    result = {"python": sys.version.split()[0]}
    try:
        result["node"] = subprocess.run(["node", "--version"], capture_output=True, text=True, timeout=5).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        result["node"] = None
    return result


class Handler(BaseHTTPRequestHandler):
    server_version = "yudream-code-runner"

    def log_message(self, format, *args):
        sys.stderr.write("[runner] " + (format % args) + "\n")

    def reply(self, status, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def authorized(self):
        if not TOKEN or self.headers.get("X-Runner-Token") != TOKEN:
            self.reply(401, {"error": "未授权"})
            return False
        return True

    def do_GET(self):
        if self.path != "/health":
            self.reply(404, {"error": "not found"})
            return
        if self.authorized():
            self.reply(200, {"status": "ok", "languages": sorted(LANGUAGES), **versions()})

    def do_POST(self):
        if self.path != "/run":
            self.reply(404, {"error": "not found"})
            return
        if not self.authorized():
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_REQUEST_BYTES:
            self.reply(413, {"error": "请求体为空或过大"})
            return
        if not slots.acquire(blocking=False):
            self.reply(429, {"error": "运行器繁忙，请稍后重试"})
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            self.reply(200, run_job(payload))
        except RunError as error:
            self.reply(error.status, {"error": error.message})
        except (ValueError, TypeError) as error:
            self.reply(400, {"error": f"请求格式无效：{error}"})
        except Exception as error:  # noqa: BLE001 - 运行器不能因单个任务崩溃
            self.reply(500, {"error": f"运行失败：{error}"})
        finally:
            slots.release()


def main():
    if not TOKEN:
        sys.stderr.write("[runner] 必须设置 RUNNER_TOKEN\n")
        sys.exit(1)
    os.makedirs(WORK_ROOT, exist_ok=True)
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    sys.stderr.write(f"[runner] listening on {HOST}:{PORT}, {versions()}\n")
    server.serve_forever()


if __name__ == "__main__":
    main()
