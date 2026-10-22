"""运行器集成测试：在本机端口启动真实服务，覆盖 Python/Node、产物回传、超时、内存与路径校验。

python3 -I test_runner.py
"""

import base64
import json
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import unittest
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
TOKEN = "test-token"


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


class RunnerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.work = tempfile.mkdtemp(prefix="runner-test-")
        cls.port = free_port()
        env = {**os.environ, "RUNNER_TOKEN": TOKEN, "RUNNER_PORT": str(cls.port), "RUNNER_HOST": "127.0.0.1",
               "RUNNER_WORK_ROOT": cls.work, "RUNNER_MAX_PROCESSES": "100000"}
        cls.server = subprocess.Popen([sys.executable, "-I", os.path.join(HERE, "runner.py")], env=env,
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(50):
            try:
                cls.call("GET", "/health")
                return
            except (urllib.error.URLError, ConnectionError):
                time.sleep(0.1)
        raise RuntimeError("runner did not start")

    @classmethod
    def tearDownClass(cls):
        cls.server.terminate()
        cls.server.wait(5)
        shutil.rmtree(cls.work, ignore_errors=True)

    @classmethod
    def call(cls, method, path, body=None, token=TOKEN):
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(f"http://127.0.0.1:{cls.port}{path}", data=data, method=method,
                                         headers={"X-Runner-Token": token, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as error:
            with error:
                return error.code, json.loads(error.read())

    @staticmethod
    def file(path, text):
        return {"path": path, "content": base64.b64encode(text.encode("utf-8")).decode("ascii")}

    def outputs(self, result):
        return {item["path"]: base64.b64decode(item["content"]).decode("utf-8") for item in result["outputs"]}

    def test_health_requires_token_and_reports_runtimes(self):
        self.assertEqual(401, self.call("GET", "/health", token="wrong")[0])
        status, body = self.call("GET", "/health")
        self.assertEqual(200, status)
        self.assertEqual(["node", "python"], body["languages"])

    def test_python_reads_inputs_and_returns_only_new_or_changed_files(self):
        status, result = self.call("POST", "/run", {
            "language": "python",
            "files": [self.file("data/in.csv", "a,b\n1,2\n"), self.file("keep.txt", "same")],
            "code": "import csv, os\nrows = list(csv.reader(open('data/in.csv')))\nprint(len(rows))\n"
                    "os.makedirs('out', exist_ok=True)\n"
                    "open('out/result.txt', 'w').write('sum=%d' % (int(rows[1][0]) + int(rows[1][1])))\n"
                    "open('data/in.csv', 'a').write('3,4\\n')\n",
        })
        self.assertEqual(200, status, result)
        self.assertEqual(0, result["exitCode"])
        self.assertEqual("2", result["stdout"].strip())
        files = self.outputs(result)
        self.assertEqual("sum=3", files["out/result.txt"])
        self.assertIn("3,4", files["data/in.csv"])
        self.assertNotIn("keep.txt", files)

    def test_node_entry_from_workspace_with_args_and_stdin(self):
        status, result = self.call("POST", "/run", {
            "language": "node",
            "entry": "src/app.js",
            "args": ["世界"],
            "stdin": "输入",
            "files": [self.file("src/app.js", "let s='';process.stdin.on('data',d=>s+=d).on('end',()=>{console.log(`你好 ${process.argv[2]} ${s}`);require('fs').writeFileSync('hello.txt','ok')})")],
        })
        self.assertEqual(200, status, result)
        self.assertEqual(0, result["exitCode"], result["stderr"])
        self.assertEqual("你好 世界 输入", result["stdout"].strip())
        self.assertEqual("ok", self.outputs(result)["hello.txt"])

    def test_timeout_kills_the_process_group(self):
        status, result = self.call("POST", "/run", {"language": "python", "timeoutSeconds": 1,
                                                    "code": "import time\nwhile True: time.sleep(0.1)\n"})
        self.assertEqual(200, status)
        self.assertTrue(result["timedOut"])
        self.assertLess(result["durationMillis"], 5000)

    def test_python_memory_limit_fails_the_run_not_the_server(self):
        status, result = self.call("POST", "/run", {"language": "python", "memoryMb": 128,
                                                    "code": "x = bytearray(512 * 1024 * 1024)\nprint('allocated')\n"})
        self.assertEqual(200, status)
        self.assertNotEqual(0, result["exitCode"])
        self.assertIn("MemoryError", result["stderr"])
        self.assertEqual(200, self.call("GET", "/health")[0])

    def test_rejects_traversal_and_unknown_language(self):
        for path in ["../escape.py", "/abs.py", "a/../../b.py", "a\\b.py"]:
            status, body = self.call("POST", "/run", {"language": "python", "files": [self.file(path, "x")], "code": "print(1)"})
            self.assertEqual(400, status, path)
        self.assertEqual(400, self.call("POST", "/run", {"language": "ruby", "code": "puts 1"})[0])
        self.assertEqual(400, self.call("POST", "/run", {"language": "python", "entry": "missing.py"})[0])
        self.assertEqual([], os.listdir(self.work))


if __name__ == "__main__":
    unittest.main(verbosity=1)
