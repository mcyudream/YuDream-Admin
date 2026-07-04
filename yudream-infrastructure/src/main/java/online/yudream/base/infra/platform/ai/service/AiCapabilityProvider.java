package online.yudream.base.infra.platform.ai.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "ai";
    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    static final String DEFAULT_MODEL = "gpt-4o-mini";
    static final String DEFAULT_TEMPERATURE = "0.4";

    private final OpenAiCompatibleGenerationGateway generationGateway;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private Map<String, String> config = Map.of();

    public AiCapabilityProvider(OpenAiCompatibleGenerationGateway generationGateway) {
        this.generationGateway = generationGateway;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        Map<String, String> defaultConfig = new LinkedHashMap<>();
        defaultConfig.put("baseUrl", DEFAULT_BASE_URL);
        defaultConfig.put("apiKey", "");
        defaultConfig.put("model", DEFAULT_MODEL);
        defaultConfig.put("temperature", DEFAULT_TEMPERATURE);
        return new CapabilityDescriptor(
                CODE,
                "AI 助手",
                CapabilityType.AI,
                "提供 OpenAI 兼容的文本生成与页面构建能力，启用后可被 CMS、文档和其他平台模块按需调用",
                "i-ri:sparkling-2-line",
                95,
                defaultConfig
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("AI 能力未启用");
        }
        return CapabilityHealth.enabled("AI 能力已启用", Map.of(
                "baseUrl", baseUrl(),
                "model", model(),
                "apiKeyConfigured", String.valueOf(hasApiKey())
        ));
    }

    @Override
    public void enable(Map<String, String> config) {
        this.config = config == null ? Map.of() : config;
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
        config = Map.of();
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("AI 能力未启用");
        }
        try {
            generationGateway.test(config, message);
            return CapabilityTestResult.success("AI 测试调用成功");
        } catch (Exception e) {
            return CapabilityTestResult.failure("AI 测试调用失败：" + e.getMessage());
        }
    }

    private String baseUrl() {
        return config.getOrDefault("baseUrl", DEFAULT_BASE_URL);
    }

    private String model() {
        return config.getOrDefault("model", DEFAULT_MODEL);
    }

    private boolean hasApiKey() {
        return config.containsKey("apiKey") && !config.get("apiKey").isBlank();
    }
}
