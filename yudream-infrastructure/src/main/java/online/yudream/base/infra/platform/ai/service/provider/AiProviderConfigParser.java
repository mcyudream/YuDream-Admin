package online.yudream.base.infra.platform.ai.service.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import online.yudream.base.domain.platform.ai.enumerate.AiEndpointUsage;
import online.yudream.base.domain.platform.ai.enumerate.AiProviderProtocol;

/**
 * AI 端点配置解析：把 {@code providers} JSON（以及老版扁平字段）解析为带用途与协议的端点清单。
 * <p>
 * 聊天、向量化、重排序端点在配置中平级共存；旧配置里挂在聊天端点上的
 * {@code embeddingModels}/{@code rerankModels} 会在此合成独立的向量化/重排序端点，
 * 保持既有的 (providerCode, modelCode) 引用（Wiki 知识库、插件语义记忆）继续可用。
 */
@Component
public class AiProviderConfigParser {

    public static final String DEFAULT_PROVIDER_CODE = "openai";
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_EMBEDDING_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    public static final String DEFAULT_TEMPERATURE = "0.4";

    public List<AiProviderEndpoint> parse(Map<String, String> config) {
        Map<String, String> safeConfig = config == null ? Map.of() : config;
        String providers = safeConfig.get("providers");
        List<AiProviderEndpoint> endpoints = StringUtils.hasText(providers)
                ? parseProviders(providers)
                : List.of(legacyProvider(safeConfig));
        return withLegacyUsageEndpoints(endpoints);
    }

    /** 指定用途且已启用的端点。 */
    public List<AiProviderEndpoint> endpoints(Map<String, String> config, AiEndpointUsage usage) {
        return parse(config).stream()
                .filter(AiProviderEndpoint::enabled)
                .filter(endpoint -> endpoint.isUsage(usage))
                .toList();
    }

    /** 按用途与编码查找端点。 */
    public Optional<AiProviderEndpoint> find(Map<String, String> config, AiEndpointUsage usage, String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return parse(config).stream()
                .filter(AiProviderEndpoint::enabled)
                .filter(endpoint -> endpoint.isUsage(usage))
                .filter(endpoint -> endpoint.code().equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public ResolvedAiModel resolve(
            Map<String, String> config,
            String providerCode,
            String modelCode,
            List<AiProviderAdapter> adapters
    ) {
        Map<String, String> safeConfig = config == null ? Map.of() : config;
        Selection selection = selection(providerCode, modelCode);
        List<AiProviderEndpoint> providers = endpoints(safeConfig, AiEndpointUsage.CHAT);
        if (providers.isEmpty()) {
            throw new BizException("AI 供应商未配置");
        }
        String defaultProvider = firstText(selection.providerCode(), safeConfig.get("defaultProvider"), DEFAULT_PROVIDER_CODE);
        AiProviderEndpoint provider = providers.stream()
                .filter(item -> equalsCode(item.code(), defaultProvider))
                .findFirst()
                .orElse(providers.get(0));
        String defaultModel = firstText(selection.modelCode(), safeConfig.get("defaultModel"), provider.defaultModel());
        AiModelEndpoint model = selectModel(provider, defaultModel);
        AiProviderAdapter adapter = adapter(provider.type(), adapters);
        return new ResolvedAiModel(provider, model, adapter);
    }

    public String defaultProvidersJson() {
        return """
                [
                  {
                    "code": "openai",
                    "name": "OpenAI",
                    "type": "OPENAI",
                    "usage": "chat",
                    "protocol": "CHAT",
                    "baseUrl": "https://api.openai.com/v1",
                    "apiKey": "",
                    "defaultModel": "gpt-4o-mini",
                    "temperature": "0.4",
                    "models": [
                      { "code": "gpt-4o-mini", "name": "GPT-4o mini", "model": "gpt-4o-mini" }
                    ]
                  },
                  {
                    "code": "anthropic",
                    "name": "Anthropic",
                    "type": "OPENAI_COMPATIBLE",
                    "usage": "chat",
                    "protocol": "ANTH",
                    "baseUrl": "https://api.anthropic.com",
                    "apiKey": "",
                    "defaultModel": "",
                    "temperature": "0.4",
                    "models": []
                  },
                  {
                    "code": "kimi",
                    "name": "Kimi",
                    "type": "KIMI",
                    "usage": "chat",
                    "protocol": "CHAT",
                    "baseUrl": "https://api.moonshot.cn/v1",
                    "apiKey": "",
                    "defaultModel": "kimi-k2.6",
                    "temperature": "0.4",
                    "models": [
                      { "code": "kimi-k2.6", "name": "Kimi K2.6", "model": "kimi-k2.6", "thinkingEnabled": true },
                      { "code": "kimi-k2.7-code", "name": "Kimi K2.7 Code", "model": "kimi-k2.7-code", "thinkingEnabled": true }
                    ]
                  },
                  {
                    "code": "deepseek",
                    "name": "DeepSeek",
                    "type": "DEEPSEEK",
                    "usage": "chat",
                    "protocol": "CHAT",
                    "baseUrl": "https://api.deepseek.com",
                    "apiKey": "",
                    "defaultModel": "deepseek-v4-pro",
                    "temperature": "0.4",
                    "models": [
                      { "code": "deepseek-v4-flash", "name": "DeepSeek V4 Flash", "model": "deepseek-v4-flash" },
                      { "code": "deepseek-v4-pro", "name": "DeepSeek V4 Pro", "model": "deepseek-v4-pro", "thinkingEnabled": true, "reasoningEffort": "high" }
                    ]
                  },
                  {
                    "code": "openai-embedding",
                    "name": "OpenAI 向量化",
                    "type": "OPENAI",
                    "usage": "embedding",
                    "protocol": "EMBEDDING",
                    "baseUrl": "https://api.openai.com/v1",
                    "apiKey": "",
                    "defaultModel": "text-embedding-3-small",
                    "models": [
                      { "code": "text-embedding-3-small", "name": "text-embedding-3-small", "model": "text-embedding-3-small" }
                    ]
                  }
                ]
                """;
    }

    private List<AiProviderEndpoint> parseProviders(String providers) {
        try {
            JSONArray array = JSONUtil.parseArray(providers);
            List<AiProviderEndpoint> result = new ArrayList<>();
            for (Object item : array) {
                if (item instanceof JSONObject json) {
                    result.add(toProvider(json));
                }
                else if (item != null) {
                    result.add(toProvider(JSONUtil.parseObj(item)));
                }
            }
            return result;
        } catch (Exception e) {
            throw new BizException("AI providers 不是有效 JSON 数组：" + e.getMessage());
        }
    }

    private AiProviderEndpoint toProvider(JSONObject json) {
        AiEndpointUsage usage = AiEndpointUsage.from(json.getStr("usage", json.getStr("endpointType", "")));
        AiProviderProtocol protocol = AiProviderProtocol.from(json.getStr("protocol", ""));
        List<AiModelEndpoint> models = toModels(json.getJSONArray("models"), usage);
        String defaultModel = firstText(json.getStr("defaultModel"), models.isEmpty() ? "" : models.get(0).optionCode());
        return new AiProviderEndpoint(
                firstText(json.getStr("code"), DEFAULT_PROVIDER_CODE),
                json.getStr("name", ""),
                AiProviderType.from(json.getStr("type", "")),
                usage,
                protocol,
                firstText(json.getStr("baseUrl"), DEFAULT_OPENAI_BASE_URL),
                json.getStr("completionsPath", json.getStr("path", "")),
                json.getStr("apiKey", ""),
                json.getStr("proxyUrl", ""),
                defaultModel,
                firstText(json.getStr("temperature"), DEFAULT_TEMPERATURE),
                extraBodyText(json),
                models,
                stringList(json.get("embeddingModels")),
                stringList(json.get("rerankModels")),
                json.getBool("enabled", true)
        );
    }

    /** 未显式声明 kind 的模型按端点用途归类，避免向量化端点的模型被当成聊天模型。 */
    private List<AiModelEndpoint> toModels(JSONArray array, AiEndpointUsage usage) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        String defaultKind = usage.code();
        List<AiModelEndpoint> result = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof CharSequence value) {
                String model = value.toString().trim();
                result.add(new AiModelEndpoint(model, model, model, "", "", null, "", defaultKind, false));
                continue;
            }
            if (item == null) {
                continue;
            }
            JSONObject json = item instanceof JSONObject object ? object : JSONUtil.parseObj(item);
            String model = firstText(json.getStr("model"), json.getStr("name"), json.getStr("code"));
            String code = firstText(json.getStr("code"), model);
            result.add(new AiModelEndpoint(
                    code,
                    json.getStr("name", model),
                    model,
                    json.getStr("temperature", ""),
                    json.getStr("reasoningEffort", ""),
                    json.getBool("thinkingEnabled", null),
                    extraBodyText(json),
                    json.getStr("kind", defaultKind),
                    json.getBool("vision", false)
            ));
        }
        return result;
    }

    /**
     * 旧配置兼容：聊天端点上的 {@code embeddingModels}/{@code rerankModels} 合成为独立端点，
     * 使既有 Wiki 知识库与插件语义记忆的 (providerCode, modelCode) 引用继续生效。
     */
    private List<AiProviderEndpoint> withLegacyUsageEndpoints(List<AiProviderEndpoint> endpoints) {
        List<AiProviderEndpoint> result = new ArrayList<>(endpoints);
        for (AiProviderEndpoint endpoint : endpoints) {
            if (!endpoint.isChat()) {
                continue;
            }
            if (!endpoint.embeddingModels().isEmpty() && !hasEndpoint(result, AiEndpointUsage.EMBEDDING, endpoint.code())) {
                result.add(legacyUsageEndpoint(endpoint, AiEndpointUsage.EMBEDDING, endpoint.embeddingModels()));
            }
            if (!endpoint.rerankModels().isEmpty() && !hasEndpoint(result, AiEndpointUsage.RERANK, endpoint.code())) {
                result.add(legacyUsageEndpoint(endpoint, AiEndpointUsage.RERANK, endpoint.rerankModels()));
            }
        }
        return List.copyOf(result);
    }

    private boolean hasEndpoint(List<AiProviderEndpoint> endpoints, AiEndpointUsage usage, String code) {
        return endpoints.stream().anyMatch(item -> item.isUsage(usage) && equalsCode(item.code(), code));
    }

    private AiProviderEndpoint legacyUsageEndpoint(AiProviderEndpoint chat, AiEndpointUsage usage, List<String> models) {
        String suffix = usage == AiEndpointUsage.EMBEDDING ? "向量化" : "重排序";
        List<AiModelEndpoint> entries = models.stream()
                .map(model -> new AiModelEndpoint(model, model, model, "", "", null, "", usage.code(), false))
                .toList();
        return new AiProviderEndpoint(
                chat.code(),
                firstText(chat.name(), chat.code()) + " " + suffix,
                chat.type(),
                usage,
                AiProviderProtocol.defaultFor(usage),
                chat.baseUrl(),
                "",
                chat.apiKey(),
                chat.proxyUrl(),
                entries.isEmpty() ? "" : entries.get(0).optionCode(),
                chat.temperature(),
                "",
                entries,
                List.of(),
                List.of(),
                chat.enabled()
        );
    }

    private AiProviderEndpoint legacyProvider(Map<String, String> config) {
        String model = firstText(config.get("model"), DEFAULT_OPENAI_MODEL);
        List<AiModelEndpoint> models = splitModels(firstText(config.get("models"), model)).stream()
                .map(value -> new AiModelEndpoint(
                        value,
                        value,
                        value,
                        config.getOrDefault("temperature", DEFAULT_TEMPERATURE),
                        config.getOrDefault("reasoningEffort", ""),
                        boolOrNull(config.get("thinkingEnabled")),
                        config.getOrDefault("extraBody", ""),
                        "chat",
                        false
                ))
                .toList();
        return new AiProviderEndpoint(
                firstText(config.get("providerCode"), DEFAULT_PROVIDER_CODE),
                firstText(config.get("providerName"), "Default AI"),
                legacyProviderType(config),
                AiEndpointUsage.CHAT,
                AiProviderProtocol.CHAT,
                firstText(config.get("baseUrl"), DEFAULT_OPENAI_BASE_URL),
                AiProviderEndpoint.DEFAULT_COMPLETIONS_PATH,
                config.getOrDefault("apiKey", ""),
                config.getOrDefault("proxyUrl", ""),
                model,
                config.getOrDefault("temperature", DEFAULT_TEMPERATURE),
                config.getOrDefault("extraBody", ""),
                models.isEmpty() ? List.of(new AiModelEndpoint(model, model, model, config.getOrDefault("temperature", DEFAULT_TEMPERATURE), "", boolOrNull(config.get("thinkingEnabled")), config.getOrDefault("extraBody", ""), "chat", false)) : models,
                splitModels(config.getOrDefault("embeddingModel", "")),
                splitModels(config.getOrDefault("rerankModel", "")),
                true
        );
    }

    private AiModelEndpoint selectModel(AiProviderEndpoint provider, String modelCode) {
        List<AiModelEndpoint> models = provider.chatModels();
        if (models.isEmpty()) {
            if (!provider.models().isEmpty()) {
                throw new BizException("AI 供应商未配置对话模型：" + provider.displayName());
            }
            String model = firstText(provider.defaultModel(), DEFAULT_OPENAI_MODEL);
            return new AiModelEndpoint(model, model, model, provider.temperature(), "", null, "", "chat", false);
        }
        String selected = firstText(modelCode, provider.defaultModel(), models.get(0).optionCode());
        return models.stream()
                .filter(item -> equalsCode(item.optionCode(), selected) || equalsCode(item.modelName(), selected))
                .findFirst()
                .orElse(models.get(0));
    }

    private AiProviderType legacyProviderType(Map<String, String> config) {
        if (StringUtils.hasText(config.get("providerType"))) {
            return AiProviderType.from(config.get("providerType"));
        }
        String baseUrl = config.getOrDefault("baseUrl", "").toLowerCase();
        if (baseUrl.contains("moonshot") || baseUrl.contains("kimi")) {
            return AiProviderType.KIMI;
        }
        if (baseUrl.contains("deepseek")) {
            return AiProviderType.DEEPSEEK;
        }
        return AiProviderType.OPENAI_COMPATIBLE;
    }

    private AiProviderAdapter adapter(AiProviderType type, List<AiProviderAdapter> adapters) {
        if (adapters == null || adapters.isEmpty()) {
            throw new BizException("AI provider adapter 未注册");
        }
        return adapters.stream()
                .filter(item -> item.type() == type)
                .findFirst()
                .orElseGet(() -> adapters.stream()
                        .filter(item -> item.type() == AiProviderType.OPENAI_COMPATIBLE)
                        .findFirst()
                        .orElse(adapters.get(0)));
    }

    private Selection selection(String providerCode, String modelCode) {
        if (!StringUtils.hasText(providerCode) && StringUtils.hasText(modelCode) && modelCode.contains(":")) {
            String[] parts = modelCode.split(":", 2);
            return new Selection(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return new Selection(providerCode, modelCode);
    }

    private String extraBodyText(JSONObject json) {
        Object raw = json.get("extraBody");
        if (raw == null) {
            return "";
        }
        if (raw instanceof CharSequence value) {
            return value.toString();
        }
        return JSONUtil.toJsonStr(raw);
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof CharSequence text) {
            return splitModels(text.toString());
        }
        if (value instanceof JSONArray array) {
            List<String> result = new ArrayList<>();
            for (Object item : array) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(String.valueOf(item).trim());
                }
            }
            return result;
        }
        return List.of();
    }

    private List<String> splitModels(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("[,，\\n\\r]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private Boolean boolOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private boolean equalsCode(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right) && left.trim().equalsIgnoreCase(right.trim());
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

    public Map<String, Object> metrics(Map<String, String> config) {
        List<AiProviderEndpoint> providers = parse(config);
        List<AiProviderEndpoint> chat = providers.stream().filter(AiProviderEndpoint::isChat).toList();
        List<AiProviderEndpoint> embeddings = providers.stream().filter(item -> item.isUsage(AiEndpointUsage.EMBEDDING)).toList();
        List<AiProviderEndpoint> reranks = providers.stream().filter(item -> item.isUsage(AiEndpointUsage.RERANK)).toList();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("providers", chat.stream().map(AiProviderEndpoint::code).toList());
        metrics.put("providerCount", chat.size());
        metrics.put("modelCount", chat.stream().mapToInt(provider -> provider.chatModels().size()).sum());
        metrics.put("protocols", chat.stream().map(provider -> provider.protocol().code()).distinct().toList());
        metrics.put("embeddingEndpoints", embeddings.stream().map(AiProviderEndpoint::code).toList());
        metrics.put("embeddingModelCount", embeddings.stream().mapToInt(provider -> provider.embeddingModelNames().size()).sum());
        metrics.put("rerankEndpoints", reranks.stream().map(AiProviderEndpoint::code).toList());
        metrics.put("rerankModelCount", reranks.stream().mapToInt(provider -> provider.rerankModelNames().size()).sum());
        metrics.put("apiKeyConfigured", providers.stream().anyMatch(provider -> StringUtils.hasText(provider.apiKey())));
        metrics.put("proxyEnabled", providers.stream().anyMatch(provider -> StringUtils.hasText(provider.proxyUrl())));
        return metrics;
    }

    private record Selection(String providerCode, String modelCode) {
    }
}
