package online.yudream.base.infra.platform.ai.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class OpenAiCompatibleGenerationGateway implements AiGenerationGateway {

    private static final int TIMEOUT = 60000;
    private static final int ERROR_BODY_LIMIT = 500;

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        JSONObject json = callChatCompletion(request.config(), request.model(), request.systemPrompt(), request.userPrompt(), request.imageDataUrl());
        return toResult(extractContent(json));
    }

    @Override
    public AiGenerationResult generateStream(AiGenerationRequest request, Consumer<String> onDelta) {
        return toResult(callChatCompletionStream(
                request.config(),
                request.model(),
                request.systemPrompt(),
                request.userPrompt(),
                request.imageDataUrl(),
                onDelta
        ));
    }

    public void test(Map<String, String> config, String message) {
        callChatCompletion(config, null, "你是 YuDream AI 能力连通性测试助手，只需返回一句简短中文。", message, null);
    }

    private JSONObject callChatCompletion(Map<String, String> config, String requestModel, String systemPrompt, String userPrompt, String imageDataUrl) {
        String apiKey = value(config, "apiKey", "");
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException("AI API Key 未配置");
        }
        JSONObject body = chatBody(config, requestModel, systemPrompt, userPrompt, imageDataUrl, false);

        HttpRequest request = HttpRequest.post(endpoint(config))
                .bearerAuth(apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", "YuDreamAdmin/1.0")
                .contentType("application/json")
                .body(body.toString())
                .timeout(TIMEOUT);
        applyProxy(request, config);

        try (HttpResponse response = request.execute()) {
            if (!response.isOk()) {
                throw new BizException("AI 调用失败：" + response.getStatus() + "，" + explainBody(response.body()));
            }
            String responseBody = response.body();
            if (looksLikeHtml(responseBody)) {
                throw new BizException("AI 调用返回了 HTML 页面，请检查 baseUrl 是否为 OpenAI 兼容 API 地址");
            }
            return JSONUtil.parseObj(responseBody);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("AI 调用异常：" + e.getMessage());
        }
    }

    private String callChatCompletionStream(Map<String, String> config, String requestModel, String systemPrompt, String userPrompt, String imageDataUrl, Consumer<String> onDelta) {
        String apiKey = value(config, "apiKey", "");
        if (!StringUtils.hasText(apiKey)) {
            throw new BizException("AI API Key 未配置");
        }
        JSONObject body = chatBody(config, requestModel, systemPrompt, userPrompt, imageDataUrl, true);
        java.net.http.HttpClient client = streamClient(config);
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(URI.create(endpoint(config)))
                .timeout(Duration.ofMillis(TIMEOUT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("User-Agent", "YuDreamAdmin/1.0")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            java.net.http.HttpResponse<Stream<String>> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new BizException("AI 流式调用失败：" + response.statusCode() + "，" + explainBody(readLimited(lines)));
                }
                StringBuilder content = new StringBuilder();
                lines.forEach(line -> appendStreamLine(line, content, onDelta));
                if (content.isEmpty()) {
                    throw new BizException("AI 流式返回内容为空");
                }
                return content.toString();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("AI 流式调用被中断");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("AI 流式调用异常：" + e.getMessage());
        }
    }

    private JSONObject chatBody(Map<String, String> config, String requestModel, String systemPrompt, String userPrompt, String imageDataUrl, boolean stream) {
        JSONObject body = JSONUtil.createObj()
                .set("model", model(config, requestModel))
                .set("temperature", temperature(config));
        mergeExtraBody(body, config);
        body.set("messages", messages(systemPrompt, userPrompt, imageDataUrl));
        if (stream) {
            body.set("stream", true);
        }
        return body;
    }

    private java.net.http.HttpClient streamClient(Map<String, String> config) {
        java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT));
        String proxyUrl = value(config, "proxyUrl", "");
        if (StringUtils.hasText(proxyUrl)) {
            URI uri = parseProxyUri(proxyUrl);
            if (!StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
                throw new BizException("AI 代理地址配置无效：代理地址必须包含主机和端口");
            }
            builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), uri.getPort())));
        }
        return builder.build();
    }

    private void appendStreamLine(String line, StringBuilder content, Consumer<String> onDelta) {
        String trimmed = line == null ? "" : line.trim();
        if (!trimmed.startsWith("data:")) {
            return;
        }
        String payload = trimmed.substring(5).trim();
        if (!StringUtils.hasText(payload) || "[DONE]".equals(payload)) {
            return;
        }
        JSONObject json;
        try {
            json = JSONUtil.parseObj(payload);
        } catch (Exception ignored) {
            return;
        }
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return;
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject delta = choice.getJSONObject("delta");
        String text = delta == null ? choice.getStr("text", "") : delta.getStr("content", "");
        if (!StringUtils.hasText(text)) {
            return;
        }
        content.append(text);
        if (onDelta != null) {
            onDelta.accept(text);
        }
    }

    private String readLimited(Stream<String> lines) {
        StringBuilder body = new StringBuilder();
        lines.limit(20).forEach(line -> body.append(line).append(' '));
        return body.toString();
    }

    private JSONArray messages(String systemPrompt, String userPrompt, String imageDataUrl) {
        JSONArray messages = JSONUtil.createArray()
                .put(JSONUtil.createObj().set("role", "system").set("content", systemPrompt));
        if (!StringUtils.hasText(imageDataUrl)) {
            return messages.put(JSONUtil.createObj().set("role", "user").set("content", userPrompt));
        }
        JSONArray content = JSONUtil.createArray()
                .put(JSONUtil.createObj().set("type", "text").set("text", userPrompt))
                .put(JSONUtil.createObj()
                        .set("type", "image_url")
                        .set("image_url", JSONUtil.createObj().set("url", imageDataUrl)));
        return messages.put(JSONUtil.createObj().set("role", "user").set("content", content));
    }

    private void mergeExtraBody(JSONObject body, Map<String, String> config) {
        String extraBody = renderTemplate(value(config, "extraBody", ""), config);
        if (!StringUtils.hasText(extraBody)) {
            return;
        }
        try {
            JSONObject extra = JSONUtil.parseObj(extraBody);
            extra.forEach(body::set);
        } catch (Exception e) {
            throw new BizException("AI extraBody 不是有效 JSON：" + e.getMessage());
        }
    }

    private String renderTemplate(String value, Map<String, String> config) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value
                .replace("{{thinkingEnabled}}", value(config, "thinkingEnabled", "false"))
                .replace("{{model}}", value(config, "model", AiCapabilityProvider.DEFAULT_MODEL))
                .replace("{{embeddingModel}}", value(config, "embeddingModel", ""))
                .replace("{{rerankModel}}", value(config, "rerankModel", ""));
    }

    private void applyProxy(HttpRequest request, Map<String, String> config) {
        String proxyUrl = value(config, "proxyUrl", "");
        if (!StringUtils.hasText(proxyUrl)) {
            return;
        }
        try {
            URI uri = parseProxyUri(proxyUrl);
            if (!StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
                throw new IllegalArgumentException("代理地址必须包含主机和端口");
            }
            request.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), uri.getPort())));
        } catch (Exception e) {
            throw new BizException("AI 代理地址配置无效：" + e.getMessage());
        }
    }

    private URI parseProxyUri(String proxyUrl) {
        String value = proxyUrl.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        return URI.create(value);
    }

    private AiGenerationResult toResult(String content) {
        JSONObject json = parseJsonObject(content);
        return new AiGenerationResult(
                json.getStr("title", ""),
                json.getStr("summary", json.getStr("message", "")),
                json.getStr("htmlContent", ""),
                json.getStr("cssContent", ""),
                json.getStr("builderProjectJson", ""),
                json.getStr("markdownContent", ""),
                toolCalls(json)
        );
    }

    private List<AiAgentToolCall> toolCalls(JSONObject json) {
        JSONArray array = json.getJSONArray("toolCalls");
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        return array.stream()
                .filter(JSONObject.class::isInstance)
                .map(JSONObject.class::cast)
                .map(this::toToolCall)
                .filter(call -> StringUtils.hasText(call.toolName()))
                .toList();
    }

    private AiAgentToolCall toToolCall(JSONObject json) {
        JSONObject arguments = json.getJSONObject("arguments");
        return new AiAgentToolCall(
                json.getStr("toolName", json.getStr("name", "")),
                arguments == null ? Map.of() : toMap(arguments)
        );
    }

    private Map<String, Object> toMap(JSONObject json) {
        Map<String, Object> result = new LinkedHashMap<>();
        json.forEach(result::put);
        return result;
    }

    private JSONObject parseJsonObject(String content) {
        String normalized = stripFence(content);
        try {
            return JSONUtil.parseObj(normalized);
        } catch (Exception e) {
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return JSONUtil.parseObj(normalized.substring(start, end + 1));
            }
            throw new BizException("AI 返回内容不是有效 JSON");
        }
    }

    private String stripFence(String content) {
        String value = content == null ? "" : content.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```[a-zA-Z]*\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.trim();
    }

    private String extractContent(JSONObject response) {
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BizException("AI 返回结果为空");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        if (message == null || !StringUtils.hasText(message.getStr("content"))) {
            throw new BizException("AI 返回消息为空");
        }
        return message.getStr("content");
    }

    private String endpoint(Map<String, String> config) {
        String baseUrl = value(config, "baseUrl", AiCapabilityProvider.DEFAULT_BASE_URL).replaceAll("/+$", "");
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/chat/completions";
    }

    private String explainBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "响应体为空";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (looksLikeHtml(normalized)) {
            if (normalized.contains("Cloudflare") || normalized.contains("cf-error-details")) {
                return "目标服务返回 Cloudflare 拦截页，请更换可服务端直连的 OpenAI 兼容 API 地址，或在网关侧放行当前服务器 IP";
            }
            return "目标地址返回 HTML 页面，请确认 baseUrl 填的是 API 根地址，例如 https://api.openai.com/v1";
        }
        return normalized.length() > ERROR_BODY_LIMIT ? normalized.substring(0, ERROR_BODY_LIMIT) + "..." : normalized;
    }

    private boolean looksLikeHtml(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String value = body.stripLeading().toLowerCase();
        return value.startsWith("<!doctype html") || value.startsWith("<html") || value.contains("<body");
    }

    private String model(Map<String, String> config, String requestModel) {
        return StringUtils.hasText(requestModel) ? requestModel.trim() : value(config, "model", AiCapabilityProvider.DEFAULT_MODEL);
    }

    private String value(Map<String, String> config, String key, String fallback) {
        if (config == null) {
            return fallback;
        }
        String value = config.get(key);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private double temperature(Map<String, String> config) {
        try {
            return Double.parseDouble(value(config, "temperature", AiCapabilityProvider.DEFAULT_TEMPERATURE));
        } catch (NumberFormatException e) {
            return Double.parseDouble(AiCapabilityProvider.DEFAULT_TEMPERATURE);
        }
    }
}
