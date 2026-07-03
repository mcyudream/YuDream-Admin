package online.yudream.base.infra.platform.integration.service;

import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Service
public class LocalPythonRuntimeExecutor implements RuntimeExecutor {

    private static final int MAX_OUTPUT_BYTES = 128 * 1024;
    private static final String OUTPUT_TRUNCATED = "\n...输出已截断...";

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
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readQuietly(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readQuietly(process.getErrorStream()));
            if (stdin != null) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();
            boolean finished = process.waitFor(script.getTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                return new RuntimeExecutionResult(output(stdoutFuture), output(stderrFuture), -1, elapsed(start), ExecutionStatus.TIMEOUT, "脚本执行超时");
            }
            String stdout = output(stdoutFuture);
            String stderr = output(stderrFuture);
            ExecutionStatus status = process.exitValue() == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
            return new RuntimeExecutionResult(stdout, stderr, process.exitValue(), elapsed(start), status, status == ExecutionStatus.SUCCESS ? null : stderr);
        } catch (Exception e) {
            return new RuntimeExecutionResult("", "", -1, elapsed(start), ExecutionStatus.FAILED, e.getMessage());
        } finally {
            cleanup(workDir);
        }
    }

    private String output(CompletableFuture<String> future) throws ExecutionException, InterruptedException {
        return future.get();
    }

    private String readQuietly(InputStream inputStream) {
        try {
            return read(inputStream);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private String read(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        boolean truncated = false;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            int remaining = MAX_OUTPUT_BYTES - total;
            if (remaining > 0) {
                int write = Math.min(read, remaining);
                output.write(buffer, 0, write);
                total += write;
            }
            if (read > remaining) {
                truncated = true;
            }
        }
        String value = output.toString(StandardCharsets.UTF_8);
        return truncated ? value + OUTPUT_TRUNCATED : value;
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
