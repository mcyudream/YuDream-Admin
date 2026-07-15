package online.yudream.base.infra.platform.ai.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationProgress;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderAdapter;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import online.yudream.base.infra.platform.ai.service.provider.ResolvedAiModel;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class OpenAiCompatibleGenerationGateway implements AiGenerationGateway {

    private static final int ERROR_BODY_LIMIT = 500;
    private static final String REASONING_CONTENT_KEY = "reasoningContent";
    private static final String REASONING_CONTENT_SNAKE_KEY = "reasoning_content";

    private final ObjectProvider<AiAgentTool> aiAgentToolProvider;
    private final AiProviderConfigParser providerConfigParser;
    private final List<AiProviderAdapter> providerAdapters;
    private final AiClientProperties aiClientProperties;
    private final PluginAiToolRegistry pluginAiToolRegistry;

    public OpenAiCompatibleGenerationGateway(
            ObjectProvider<AiAgentTool> aiAgentToolProvider,
            AiProviderConfigParser providerConfigParser,
            List<AiProviderAdapter> providerAdapters,
            AiClientProperties aiClientProperties,
            PluginAiToolRegistry pluginAiToolRegistry
    ) {
        this.aiAgentToolProvider = aiAgentToolProvider;
        this.providerConfigParser = providerConfigParser;
        this.providerAdapters = providerAdapters;
        this.aiClientProperties = aiClientProperties;
        this.pluginAiToolRegistry = pluginAiToolRegistry;
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request) {
        Map<String, String> config = request.config() == null ? Map.of() : request.config();
        ResolvedAiModel resolved = resolve(config, request);
        List<AiAgentToolResult> toolResults = Collections.synchronizedList(new ArrayList<>());
        try {
            log.debug("AI non-stream call start, provider={}, endpoint={}, model={}, promptLength={}, image={}",
                    resolved.provider().code(),
                    resolved.provider().endpointUrl(),
                    resolved.model().modelName(),
                    length(request.userPrompt()),
                    StringUtils.hasText(request.imageDataUrl()));
            String content = requestSpec(request, resolved, toolResults).call().content();
            log.debug("AI non-stream call completed, contentLength={}, toolResults={}", length(content), toolResults.size());
            return toResult(content, toolResults);
        } catch (BizException e) {
            log.debug("AI non-stream call business error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.debug("AI non-stream call failed", e);
            throw new BizException("AI 调用异常：" + explainException(e));
        }
    }

    @Override
    public AiGenerationResult generateStream(
            AiGenerationRequest request,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        Map<String, String> config = request.config() == null ? Map.of() : request.config();
        ResolvedAiModel resolved = resolve(config, request);
        List<AiAgentToolResult> toolResults = Collections.synchronizedList(new ArrayList<>());
        StringBuilder content = new StringBuilder();
        try {
            progress(onProgress, "request", "正在准备模型请求。");
            log.debug("AI stream call start, provider={}, endpoint={}, model={}, promptLength={}, image={}",
                    resolved.provider().code(),
                    resolved.provider().endpointUrl(),
                    resolved.model().modelName(),
                    length(request.userPrompt()),
                    StringUtils.hasText(request.imageDataUrl()));
            requestSpec(request, resolved, toolResults, onTool, onProgress)
                    .stream()
                    .chatResponse()
                    .doFirst(() -> {
                        log.debug("AI stream subscribed, provider={}, model={}",
                                resolved.provider().code(),
                                resolved.model().modelName());
                        progress(onProgress, "subscribed", "模型流已建立，正在等待首个响应。");
                    })
                    .doOnNext(response -> {
                        String reasoning = reasoningDelta(response);
                        if (StringUtils.hasText(reasoning)) {
                            log.debug("AI stream reasoning received, length={}, preview={}", reasoning.length(), preview(reasoning));
                            progress(onProgress, "reasoning", reasoning);
                        }
                        String delta = contentDelta(response);
                        if (!StringUtils.hasText(delta)) {
                            return;
                        }
                        if (content.isEmpty()) {
                            progress(onProgress, "first-delta", "模型已开始输出内容。");
                        }
                        content.append(delta);
                        log.debug("AI stream delta received, length={}, preview={}", delta.length(), preview(delta));
                        if (onDelta != null) {
                            onDelta.accept(delta);
                        }
                    })
                    .doOnComplete(() -> progress(onProgress, "stream-complete", "模型流式输出已完成，正在汇总结果。"))
                    .blockLast(aiClientProperties.getReadTimeout());
            log.debug("AI stream call completed, contentLength={}, toolResults={}", content.length(), toolResults.size());
            progress(onProgress, "complete", "AI 处理完成。");
            return toResult(content.toString(), toolResults);
        } catch (BizException e) {
            log.debug("AI stream call business error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.debug("AI stream call failed", e);
            throw new BizException("AI 流式调用异常：" + explainException(e));
        }
    }

    public void test(Map<String, String> config, String message) {
        generate(new AiGenerationRequest(
                "你是 YuDream AI 能力连通性测试助手，只需要返回一句简短中文。",
                message,
                null,
                null,
                null,
                config
        ));
    }

    private ResolvedAiModel resolve(Map<String, String> config, AiGenerationRequest request) {
        ResolvedAiModel resolved = providerConfigParser.resolve(
                config,
                request.providerCode(),
                request.modelCode(),
                providerAdapters
        );
        if (!StringUtils.hasText(resolved.provider().apiKey())) {
            throw new BizException("AI API Key 未配置：" + resolved.provider().displayName());
        }
        if (!StringUtils.hasText(resolved.provider().endpointBaseUrl())) {
            throw new BizException("AI API 地址未配置：" + resolved.provider().displayName());
        }
        if (!StringUtils.hasText(resolved.model().modelName())) {
            throw new BizException("AI 模型未配置：" + resolved.provider().displayName());
        }
        return resolved;
    }

    private ChatClient.ChatClientRequestSpec requestSpec(
            AiGenerationRequest request,
            ResolvedAiModel resolved,
            List<AiAgentToolResult> toolResults
    ) {
        return requestSpec(request, resolved, toolResults, null, null);
    }

    private ChatClient.ChatClientRequestSpec requestSpec(
            AiGenerationRequest request,
            ResolvedAiModel resolved,
            List<AiAgentToolResult> toolResults,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        List<ToolCallback> callbacks = request.toolCallingEnabled() ? toolCallbacks(toolResults, onTool, onProgress) : List.of();
        AiGenerationRequest effectiveRequest = request.withToolCallingEnabled(!callbacks.isEmpty());
        ChatClient.ChatClientRequestSpec spec = ChatClient.create(chatModel(resolved, effectiveRequest))
                .prompt()
                .system(request.systemPrompt())
                .messages(historyMessages(request.history()))
                .user(user -> {
                    user.text(request.userPrompt());
                    imageMedia(request.imageDataUrl()).ifPresent(user::media);
                });
        if (!callbacks.isEmpty()) {
            log.debug("AI tool callbacks registered, count={}, names={}",
                    callbacks.size(),
                    callbacks.stream().map(callback -> callback.getToolDefinition().name()).toList());
            spec.toolCallbacks(callbacks);
        }
        return spec;
    }

    private OpenAiChatModel chatModel(ResolvedAiModel resolved, AiGenerationRequest request) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(resolved.provider().endpointBaseUrl())
                .completionsPath(resolved.provider().completionsPath())
                .apiKey(resolved.provider().apiKey())
                .restClientBuilder(restClientBuilder(resolved.provider().proxyUrl()))
                .webClientBuilder(webClientBuilder(resolved.provider().proxyUrl()))
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(resolved.adapter().chatOptions(resolved.provider(), resolved.model(), request))
                .toolCallingManager(DefaultToolCallingManager.builder().build())
                .retryTemplate(RetryTemplate.defaultInstance())
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    private RestClient.Builder restClientBuilder(String proxyUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(aiClientProperties.getConnectTimeout());
        factory.setReadTimeout(aiClientProperties.getReadTimeout());
        if (StringUtils.hasText(proxyUrl)) {
            URI uri = parseProxyUri(proxyUrl);
            if (!StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
                throw new BizException("AI 代理地址配置无效：代理地址必须包含主机和端口");
            }
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), uri.getPort())));
        }
        return RestClient.builder()
                .requestFactory(factory)
                .messageConverters(converters -> converters.stream()
                        .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                        .map(MappingJackson2HttpMessageConverter.class::cast)
                        .forEach(this::allowOctetStreamJson));
    }

    private WebClient.Builder webClientBuilder(String proxyUrl) {
        HttpClient client = HttpClient.create()
                .responseTimeout(aiClientProperties.getReadTimeout());
        if (StringUtils.hasText(proxyUrl)) {
            URI uri = parseProxyUri(proxyUrl);
            if (!StringUtils.hasText(uri.getHost()) || uri.getPort() <= 0) {
                throw new BizException("AI 代理地址配置无效：代理地址必须包含主机和端口");
            }
            client = client.proxy(proxy -> proxy
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(uri.getHost())
                    .port(uri.getPort()));
        }
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client));
    }

    private void allowOctetStreamJson(MappingJackson2HttpMessageConverter converter) {
        List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
        if (!mediaTypes.contains(MediaType.APPLICATION_OCTET_STREAM)) {
            mediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
            converter.setSupportedMediaTypes(mediaTypes);
        }
    }

    private List<ToolCallback> toolCallbacks(
            List<AiAgentToolResult> toolResults,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        var scope = PluginAiToolExecutionScope.current();
        if (scope != null) {
            return pluginAiToolRegistry.tools().stream().filter(tool -> allowed(tool, scope))
                    .map(tool -> pluginToolCallback(tool, scope, toolResults, onTool, onProgress)).toList();
        }
        List<AiAgentTool> scopedTools = AiAgentToolExecutionScope.currentTools();
        if (scopedTools != null) {
            return scopedTools.stream()
                    .map(tool -> toolCallback(tool, toolResults, onTool, onProgress))
                    .toList();
        }
        var allowedToolNames = AiAgentToolExecutionScope.current();
        return aiAgentToolProvider.stream()
                .filter(tool -> allowedToolNames == null || allowedToolNames.contains(tool.descriptor().name()))
                .map(tool -> toolCallback(tool, toolResults, onTool, onProgress))
                .toList();
    }

    private ToolCallback toolCallback(
            AiAgentTool tool,
            List<AiAgentToolResult> toolResults,
            Consumer<AiAgentToolResult> onTool,
            Consumer<AiGenerationProgress> onProgress
    ) {
        AiAgentToolDescriptor descriptor = tool.descriptor();
        return FunctionToolCallback.<Map<String, Object>, AiAgentToolResult>builder(safeToolName(descriptor.name()), args -> {
                    Map<String, Object> arguments = args == null ? Map.of() : args;
                    progress(onProgress, "tool-start", "正在调用工具：" + descriptor.title());
                    log.debug("AI tool call start, tool={}, action={}, argsKeys={}",
                            descriptor.name(),
                            arguments.get("action"),
                            arguments.keySet());
                    AiAgentToolResult result = tool.execute(new AiAgentToolCall(descriptor.name(), arguments));
                    toolResults.add(result);
                    if (onTool != null) {
                        onTool.accept(result);
                    }
                    progress(onProgress, "tool-complete", "工具调用完成：" + descriptor.title());
                    log.debug("AI tool call completed, tool={}, action={}, payloadKeys={}",
                            result.toolName(),
                            result.action(),
                            result.payload() == null ? List.of() : result.payload().keySet());
                    return result;
                })
                .description(descriptor.description())
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .inputSchema(inputSchema(descriptor))
                .toolCallResultConverter((result, type) -> JSONUtil.toJsonStr(result))
                .build();
    }

    private String inputSchema(AiAgentToolDescriptor descriptor) {
        JSONObject properties = JSONUtil.createObj();
        Map<String, Object> schema = descriptor.inputSchema() == null ? Map.of() : descriptor.inputSchema();
        schema.forEach((key, value) -> properties.set(key, propertySchema(value)));
        return JSONUtil.createObj()
                .set("type", "object")
                .set("additionalProperties", true)
                .set("properties", properties)
                .toString();
    }

    private JSONObject propertySchema(Object value) {
        if (value instanceof Map<?, ?> map) {
            JSONObject property = JSONUtil.createObj();
            map.forEach((key, item) -> property.set(String.valueOf(key), item));
            property.set("type", property.getStr("type", "string"));
            return property;
        }
        return JSONUtil.createObj()
                .set("type", "string")
                .set("description", value == null ? "" : String.valueOf(value));
    }

    private Optional<Media> imageMedia(String imageDataUrl) {
        if (!StringUtils.hasText(imageDataUrl)) {
            return Optional.empty();
        }
        String value = imageDataUrl.trim();
        int comma = value.indexOf(',');
        if (!value.startsWith("data:") || comma <= 0) {
            throw new BizException("样图格式无效：仅支持 data URL");
        }
        String meta = value.substring(5, comma);
        String mimeType = meta.split(";")[0];
        try {
            byte[] data = Base64.getDecoder().decode(value.substring(comma + 1));
            return Optional.of(Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(mimeType))
                    .data(new ByteArrayResource(data))
                    .name("reference-image")
                    .build());
        } catch (Exception e) {
            throw new BizException("样图解析失败：" + e.getMessage());
        }
    }

    private List<Message> historyMessages(List<AiChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<AiChatMessage> valid = history.stream()
                .filter(item -> item != null && StringUtils.hasText(item.content()))
                .toList();
        int maxMessages = Math.max(0, aiClientProperties.getHistoryMaxTurns()) * 2;
        if (maxMessages > 0 && valid.size() > maxMessages) {
            valid = valid.subList(valid.size() - maxMessages, valid.size());
        }
        List<Message> messages = new ArrayList<>(valid.size());
        for (AiChatMessage item : valid) {
            if (item.isAssistant()) {
                messages.add(new AssistantMessage(item.content().trim()));
            } else {
                messages.add(new UserMessage(item.content().trim()));
            }
        }
        log.debug("AI history injected, turns={}, messages={}", messages.size() / 2, messages.size());
        return messages;
    }

    static AiGenerationResult toResult(String content, List<AiAgentToolResult> toolResults) {
        if (toolResults != null && !toolResults.isEmpty()) {
            return new AiGenerationResult("", summary(content, toolResults), "", "", "", "", "", List.of(), List.copyOf(toolResults));
        }
        return new AiGenerationResult("", content == null ? "" : content.trim(), "", "", "", "", "", List.of(), List.of());
    }

    private boolean allowed(PluginAiTool tool, online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context) {
        var descriptor = tool.descriptor();
        return descriptor != null && descriptor.risk() == PluginAiToolRisk.READ && context.allowsTool(descriptor.name())
                && descriptor.allowedTriggers().contains(context.trigger()) && context.hasPermission(descriptor.permissionCode());
    }

    private ToolCallback pluginToolCallback(PluginAiTool tool, online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext context,
                                             List<AiAgentToolResult> results, Consumer<AiAgentToolResult> onTool, Consumer<AiGenerationProgress> onProgress) {
        var descriptor = tool.descriptor();
        return FunctionToolCallback.<Map<String, Object>, AiAgentToolResult>builder(safeToolName(descriptor.name()), args -> {
            if (!allowed(tool, context)) throw new BizException("当前用户无权调用 AI 工具：" + descriptor.title());
            progress(onProgress, "tool-start", "正在调用工具：" + descriptor.title());
            var value = tool.execute(context, new online.yudream.base.plugin.spi.system.ai.PluginAiToolCall(descriptor.name(), args));
            AiAgentToolResult result = new AiAgentToolResult(descriptor.name(), value.action(), context.traceId(), value.message(), value.payload());
            results.add(result); if (onTool != null) onTool.accept(result); progress(onProgress, "tool-complete", "工具调用完成：" + descriptor.title()); return result;
        }).description(descriptor.description()).inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .inputSchema(inputSchema(new AiAgentToolDescriptor(descriptor.name(), descriptor.title(), descriptor.description(), descriptor.permissionCode(), descriptor.title(), "插件工具", descriptor.description(), descriptor.inputSchema())))
                .toolCallResultConverter((result, type) -> JSONUtil.toJsonStr(result)).build();
    }


    private URI parseProxyUri(String proxyUrl) {
        String value = proxyUrl.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        return URI.create(value);
    }

    private static String summary(String content, List<AiAgentToolResult> toolResults) {
        if (StringUtils.hasText(content)) {
            return content.trim();
        }
        return toolResults.stream()
                .findFirst()
                .map(AiAgentToolResult::message)
                .orElse("画布操作已完成。");
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String contentDelta(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String reasoningDelta(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        Map<String, Object> metadata = response.getResult().getOutput().getMetadata();
        Object value = metadata.get(REASONING_CONTENT_KEY);
        if (value == null) {
            value = metadata.get(REASONING_CONTENT_SNAKE_KEY);
        }
        return value == null ? "" : String.valueOf(value);
    }

    private String preview(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) + "..." : normalized;
    }

    private void progress(Consumer<AiGenerationProgress> onProgress, String action, String content) {
        if (onProgress != null) {
            onProgress.accept(new AiGenerationProgress(action, content));
        }
    }

    private String safeToolName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String explainException(Exception e) {
        if (hasCause(e, TimeoutException.class)) {
            long minutes = Math.max(1, aiClientProperties.getReadTimeout().toMinutes());
            return "模型生成超过 " + minutes + " 分钟仍未结束，请缩小任务范围后重试";
        }
        String responseError = explainResponseException(e);
        if (StringUtils.hasText(responseError)) {
            return responseError;
        }
        String message = e.getMessage();
        if (!StringUtils.hasText(message)) {
            return e.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        if (looksLikeHtml(normalized)) {
            return "目标地址返回 HTML 页面，请确认 baseUrl 填的是 API 根地址，例如 https://api.openai.com/v1";
        }
        return normalized.length() > ERROR_BODY_LIMIT ? normalized.substring(0, ERROR_BODY_LIMIT) + "..." : normalized;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String explainResponseException(Throwable error) {
        if (error == null) {
            return "";
        }
        if (error instanceof WebClientResponseException e) {
            return httpError(e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString(), e.getMessage());
        }
        if (error instanceof RestClientResponseException e) {
            return httpError(e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString(), e.getMessage());
        }
        String cause = explainResponseException(error.getCause());
        if (StringUtils.hasText(cause)) {
            return cause;
        }
        for (Throwable suppressed : error.getSuppressed()) {
            String suppressedError = explainResponseException(suppressed);
            if (StringUtils.hasText(suppressedError)) {
                return suppressedError;
            }
        }
        return "";
    }

    private String httpError(int status, String statusText, String body, String fallback) {
        String detail = explainResponseBody(body);
        if (!StringUtils.hasText(detail)) {
            detail = normalizeErrorText(fallback);
        }
        String title = "HTTP " + status + (StringUtils.hasText(statusText) ? " " + statusText.trim() : "");
        if (!StringUtils.hasText(detail)) {
            return title;
        }
        return title + "：" + limitErrorText(detail);
    }

    private String explainResponseBody(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        if (looksLikeHtml(body)) {
            return "目标地址返回 HTML 页面，请确认 baseUrl 填的是 API 根地址，例如 https://api.openai.com/v1";
        }
        try {
            JSONObject json = JSONUtil.parseObj(body);
            JSONObject error = json.getJSONObject("error");
            if (error != null) {
                return firstText(error.getStr("message"), error.toString());
            }
            return firstText(json.getStr("message"), json.getStr("error"), body);
        } catch (Exception ignored) {
            return normalizeErrorText(body);
        }
    }

    private String normalizeErrorText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String limitErrorText(String value) {
        String normalized = normalizeErrorText(value);
        return normalized.length() > ERROR_BODY_LIMIT ? normalized.substring(0, ERROR_BODY_LIMIT) + "..." : normalized;
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean looksLikeHtml(String body) {
        if (!StringUtils.hasText(body)) {
            return false;
        }
        String value = body.stripLeading().toLowerCase();
        return value.startsWith("<!doctype html") || value.startsWith("<html") || value.contains("<body");
    }
}
