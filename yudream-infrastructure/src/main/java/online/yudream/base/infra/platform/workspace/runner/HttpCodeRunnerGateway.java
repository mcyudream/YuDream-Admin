package online.yudream.base.infra.platform.workspace.runner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.workspace.service.CodeRunnerGateway;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunRequest;
import online.yudream.base.domain.platform.workspace.valobj.CodeRunResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行器 HTTP 适配：POST /run 携带文件（base64）与限制，返回输出与产物；GET /health 探测可用性（结果缓存 30 秒）。
 * 令牌经 X-Runner-Token 传递，不写日志。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "yudream.platform.code-runner", name = "enabled", havingValue = "true")
public class HttpCodeRunnerGateway implements CodeRunnerGateway {

    private static final long HEALTH_TTL_MILLIS = 30_000;

    private final CodeRunnerProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;
    private volatile long checkedAt;
    private volatile String unavailable = "运行器尚未连通";
    private volatile Map<String, String> runtimes = Map.of();

    public HttpCodeRunnerGateway(CodeRunnerProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public String unavailableReason() {
        if (System.currentTimeMillis() - checkedAt > HEALTH_TTL_MILLIS) {
            probe();
        }
        return unavailable;
    }

    @Override
    public Map<String, String> runtimes() {
        return unavailableReason() == null ? runtimes : Map.of();
    }

    private synchronized void probe() {
        if (System.currentTimeMillis() - checkedAt <= HEALTH_TTL_MILLIS) {
            return;
        }
        try {
            if (!StringUtils.hasText(properties.getToken())) {
                unavailable = "未配置运行器令牌（CODE_RUNNER_TOKEN）";
                return;
            }
            HttpResponse<String> response = http.send(request("/health").timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                unavailable = response.statusCode() == 401 ? "运行器拒绝了令牌，请核对 CODE_RUNNER_TOKEN" : "运行器健康检查失败（HTTP " + response.statusCode() + "）";
                return;
            }
            Map<String, Object> body = json.readValue(response.body(), new TypeReference<>() { });
            Map<String, String> versions = new LinkedHashMap<>();
            for (String key : List.of("python", "node")) {
                if (body.get(key) != null) {
                    versions.put(key, String.valueOf(body.get(key)));
                }
            }
            runtimes = Map.copyOf(versions);
            unavailable = null;
        } catch (Exception error) {
            unavailable = "无法连接代码运行器（" + properties.getBaseUrl() + "）";
            log.debug("代码运行器健康检查失败：{}", error.getMessage());
        } finally {
            checkedAt = System.currentTimeMillis();
        }
    }

    @Override
    public CodeRunResult run(CodeRunRequest request) {
        String reason = unavailableReason();
        if (reason != null) {
            throw new BizException(reason);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", request.language());
        if (request.entry() != null) {
            payload.put("entry", request.entry());
        }
        if (request.code() != null) {
            payload.put("code", request.code());
        }
        payload.put("args", request.args());
        payload.put("stdin", request.stdin() == null ? "" : request.stdin());
        payload.put("env", request.env());
        payload.put("timeoutSeconds", request.timeoutSeconds());
        payload.put("memoryMb", request.memoryMb());
        List<Map<String, String>> files = new ArrayList<>();
        request.files().forEach((path, data) -> files.add(Map.of("path", path, "content", Base64.getEncoder().encodeToString(data))));
        payload.put("files", files);
        try {
            HttpResponse<String> response = http.send(request("/run").timeout(properties.getTimeout())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload), StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Map<String, Object> body = json.readValue(response.body(), new TypeReference<>() { });
            if (response.statusCode() != 200) {
                throw new BizException("代码运行器拒绝了请求：" + body.getOrDefault("error", "HTTP " + response.statusCode()));
            }
            Map<String, byte[]> outputs = new LinkedHashMap<>();
            Object rawOutputs = body.get("outputs");
            if (rawOutputs instanceof List<?> items) {
                for (Object item : items) {
                    if (item instanceof Map<?, ?> file && file.get("path") != null) {
                        outputs.put(String.valueOf(file.get("path")), Base64.getDecoder().decode(String.valueOf(file.get("content"))));
                    }
                }
            }
            return new CodeRunResult(number(body.get("exitCode")), Boolean.TRUE.equals(body.get("timedOut")), number(body.get("durationMillis")),
                    String.valueOf(body.getOrDefault("stdout", "")), String.valueOf(body.getOrDefault("stderr", "")), outputs,
                    Boolean.TRUE.equals(body.get("outputsTruncated")));
        } catch (BizException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BizException("代码运行被中断");
        } catch (Exception error) {
            checkedAt = 0;
            throw new BizException("调用代码运行器失败：" + error.getMessage());
        }
    }

    private HttpRequest.Builder request(String path) {
        String base = properties.getBaseUrl().endsWith("/") ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1) : properties.getBaseUrl();
        return HttpRequest.newBuilder(URI.create(base + path)).header("X-Runner-Token", properties.getToken());
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : -1;
    }
}
