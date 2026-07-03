package online.yudream.base.infra.platform.integration.service;

import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class LocalPythonRuntimeExecutor implements RuntimeExecutor {

    @Override
    public RuntimeExecutionResult execute(RuntimeScript script, String stdin) {
        if (script.getLanguage() != RuntimeLanguage.PYTHON) {
            return new RuntimeExecutionResult("", "暂不支持该运行时", -1, 0, ExecutionStatus.FAILED, "暂不支持该运行时");
        }
        long start = System.currentTimeMillis();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("yudream-runtime-");
            Path scriptFile = workDir.resolve("script.py");
            Files.writeString(scriptFile, script.getScriptContent(), StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder("python", scriptFile.toAbsolutePath().toString());
            builder.directory(workDir.toFile());
            if (script.getEnv() != null) {
                builder.environment().putAll(script.getEnv());
            }
            Process process = builder.start();
            if (stdin != null) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();
            boolean finished = process.waitFor(script.getTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new RuntimeExecutionResult(read(process.getInputStream()), read(process.getErrorStream()), -1, elapsed(start), ExecutionStatus.TIMEOUT, "脚本执行超时");
            }
            String stdout = read(process.getInputStream());
            String stderr = read(process.getErrorStream());
            ExecutionStatus status = process.exitValue() == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
            return new RuntimeExecutionResult(stdout, stderr, process.exitValue(), elapsed(start), status, status == ExecutionStatus.SUCCESS ? null : stderr);
        } catch (Exception e) {
            return new RuntimeExecutionResult("", "", -1, elapsed(start), ExecutionStatus.FAILED, e.getMessage());
        } finally {
            cleanup(workDir);
        }
    }

    private String read(java.io.InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private void cleanup(Path workDir) {
        if (workDir == null) {
            return;
        }
        try (var paths = Files.walk(workDir)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
