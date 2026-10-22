package online.yudream.base.infra.platform.workspace.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunRequest;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 适配器对真实运行器（yudream-code-runner/runner.py）的端到端测试；本机没有 python3 时跳过。
 */
class HttpCodeRunnerGatewayTest {

    private static Process runner;
    private static Path work;
    private static int port;

    @BeforeAll
    static void start() throws Exception {
        Path script = Path.of("..", "yudream-code-runner", "runner.py").toAbsolutePath().normalize();
        assumeTrue(Files.exists(script), "runner.py 不在预期位置");
        assumeTrue(new ProcessBuilder("python3", "--version").start().waitFor() == 0, "本机没有 python3");
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        work = Files.createTempDirectory("runner-gateway-test");
        ProcessBuilder builder = new ProcessBuilder("python3", "-I", script.toString());
        builder.environment().putAll(Map.of("RUNNER_TOKEN", "secret", "RUNNER_PORT", String.valueOf(port), "RUNNER_HOST", "127.0.0.1",
                "RUNNER_WORK_ROOT", work.toString(), "RUNNER_MAX_PROCESSES", "100000"));
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        runner = builder.start();
        Thread.sleep(800);
    }

    @AfterAll
    static void stop() {
        if (runner != null) {
            runner.destroy();
        }
    }

    private static HttpCodeRunnerGateway gateway(String token) {
        CodeRunnerProperties properties = new CodeRunnerProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + port);
        properties.setToken(token);
        return new HttpCodeRunnerGateway(properties, new ObjectMapper());
    }

    @Test
    void runsPythonWithFilesAndReturnsNewOutputs() {
        HttpCodeRunnerGateway gateway = gateway("secret");
        assertNull(gateway.unavailableReason());
        assertTrue(gateway.runtimes().containsKey("python"));
        CodeRunResult result = gateway.run(new CodeRunRequest("python", "main.py", null, List.of("x"), "", Map.of(),
                Map.of("main.py", "import sys\nopen('out.txt','w').write(open('in.txt').read().upper() + sys.argv[1])\nprint('完成')\n".getBytes(StandardCharsets.UTF_8),
                        "in.txt", "abc".getBytes(StandardCharsets.UTF_8)), 10, 256));
        assertEquals(0, result.exitCode(), result.stderr());
        assertEquals("完成", result.stdout().strip());
        assertEquals("ABCx", new String(result.outputs().get("out.txt"), StandardCharsets.UTF_8));
        assertEquals(java.util.Set.of("out.txt"), result.outputs().keySet());
    }

    @Test
    void wrongTokenMakesTheRunnerUnavailable() {
        HttpCodeRunnerGateway gateway = gateway("wrong");
        assertNotNull(gateway.unavailableReason());
        assertThrows(BizException.class, () -> gateway.run(new CodeRunRequest("python", null, "print(1)", null, null, null, null, 5, 256)));
        assertNotNull(gateway("").unavailableReason());
    }
}
