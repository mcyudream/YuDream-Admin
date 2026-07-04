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

@Component
public class AiProviderConfigParser {

    public static final String DEFAULT_PROVIDER_CODE = "openai";
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    public static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_TEMPERATURE = "0.4";

    public List<AiProviderEndpoint> parse(Map<String, String> config) {
        Map<String, String> safeConfig = config == null ? Map.of() : config;
        String providers = safeConfig.get("providers");
        if (StringUtils.hasText(providers)) {
            return parseProviders(providers);
        }
        return List.of(legacyProvider(safeConfig));
    }

    public ResolvedAiModel resolve(
            Map<String, String> config,
            String providerCode,
            String modelCode,
            List<AiProviderAdapter> adapters
    ) {
        Map<String, String> safeConfig = config == null ? Map.of() : config;
        Selection selection = selection(providerCode, modelCode);
        List<AiProviderEndpoint> providers = parse(safeConfig).stream()
                .filter(AiProviderEndpoint::enabled)
                .toList();
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
                    "baseUrl": "https://api.openai.com/v1",
                    "apiKey": "",
                    "defaultModel": "gpt-4o-mini",
                    "temperature": "0.4",
                    "models": [
                      { "code": "gpt-4o-mini", "name": "GPT-4o mini", "model": "gpt-4o-mini" }
                    ],
                    "embeddingModels": ["text-embedding-3-small"],
                    "rerankModels": []
                  },
                  {
                    "code": "kimi",
                    "name": "Kimi",
                    "type": "KIMI",
                    "baseUrl": "https://api.moonshot.cn/v1",
                    "apiKey": "",
                    "defaultModel": "kimi-k2.6",
                    "temperature": "0.4",
                    "models": [
                      { "code": "kimi-k2.6", "name": "Kimi K2.6", "model": "kimi-k2.6", "thinkingEnabled": true },
                      { "code": "kimi-k2.7-code", "name": "Kimi K2.7 Code", "model": "kimi-k2.7-code", "thinkingEnabled": true }
                    ],
                    "embeddingModels": [],
                    "rerankModels": []
                  },
                  {
                    "code": "deepseek",
                    "name": "DeepSeek",
                    "type": "DEEPSEEK",
                    "baseUrl": "https://api.deepseek.com",
                    "apiKey": "",
                    "defaultModel": "deepseek-v4-pro",
                    "temperature": "0.4",
                    "models": [
                      { "code": "deepseek-v4-flash", "name": "DeepSeek V4 Flash", "model": "deepseek-v4-flash" },
                      { "code": "deepseek-v4-pro", "name": "DeepSeek V4 Pro", "model": "deepseek-v4-pro", "thinkingEnabled": true, "reasoningEffort": "high" }
                    ],
                    "embeddingModels": [],
                    "rerankModels": []
                  }
                ]
                """;
    }

    private List<AiProviderEndpoint> parseProviders(String providers) {
        try {
            JSONArray array = JSONUtil.parseArray(providers);
            List<AiProviderEndpoint> result = new ArrayList<>();
            for (Object item : array) {
                result.add(toProvider(item instanceof JSONObject json ? json : JSONUtil.parseObj(item)));
            }
            return result;
        } catch (Exception e) {
            throw new BizException("AI providers 不是有效 JSON 数组：" + e.getMessage());
        }
    }

    private AiProviderEndpoint toProvider(JSONObject json) {
        List<AiModelEndpoint> models = toModels(json.getJSONArray("models"));
        String defaultModel = firstText(json.getStr("defaultModel"), models.isEmpty() ? "" : models.get(0).optionCode());
        return new AiProviderEndpoint(
                firstText(json.getStr("code"), DEFAULT_PROVIDER_CODE),
                json.getStr("name", ""),
                AiProviderType.from(json.getStr("type", "")),
                firstText(json.getStr("baseUrl"), DEFAULT_OPENAI_BASE_URL),
                firstText(json.getStr("completionsPath"), AiProviderEndpoint.DEFAULT_COMPLETIONS_PATH),
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

    private List<AiModelEndpoint> toModels(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        List<AiModelEndpoint> result = new ArrayList<>();
        for (Object item : array) {
            if (item instanceof CharSequence value) {
                String model = value.toString().trim();
                result.add(new AiModelEndpoint(model, model, model, "", "", null, "", "chat", false));
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
                    json.getStr("kind", "chat"),
                    json.getBool("vision", false)
            ));
        }
        return result;
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
        List<AiModelEndpoint> models = provider.models();
        if (models.isEmpty()) {
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
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("providers", providers.stream().map(AiProviderEndpoint::code).toList());
        metrics.put("providerCount", providers.size());
        metrics.put("modelCount", providers.stream().mapToInt(provider -> provider.models().size()).sum());
        metrics.put("embeddingModelCount", providers.stream().mapToInt(provider -> provider.embeddingModels().size()).sum());
        metrics.put("rerankModelCount", providers.stream().mapToInt(provider -> provider.rerankModels().size()).sum());
        metrics.put("apiKeyConfigured", providers.stream().anyMatch(provider -> StringUtils.hasText(provider.apiKey())));
        metrics.put("proxyEnabled", providers.stream().anyMatch(provider -> StringUtils.hasText(provider.proxyUrl())));
        return metrics;
    }

    private record Selection(String providerCode, String modelCode) {
    }
}
