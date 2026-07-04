package online.yudream.base.infra.platform.ai.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.infra.platform.ai.service.provider.AiProviderConfigParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "ai";

    private final OpenAiCompatibleGenerationGateway generationGateway;
    private final AiProviderConfigParser providerConfigParser;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private Map<String, String> config = Map.of();

    public AiCapabilityProvider(
            OpenAiCompatibleGenerationGateway generationGateway,
            AiProviderConfigParser providerConfigParser
    ) {
        this.generationGateway = generationGateway;
        this.providerConfigParser = providerConfigParser;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        Map<String, String> defaultConfig = new LinkedHashMap<>();
        defaultConfig.put("providers", providerConfigParser.defaultProvidersJson());
        defaultConfig.put("defaultProvider", AiProviderConfigParser.DEFAULT_PROVIDER_CODE);
        defaultConfig.put("defaultModel", AiProviderConfigParser.DEFAULT_OPENAI_MODEL);
        return new CapabilityDescriptor(
                CODE,
                "AI 助手",
                CapabilityType.AI,
                "提供多供应商、多模型的文本生成与页面构建能力，支持 OpenAI 兼容、Kimi、DeepSeek 等模型参数适配",
                "i-ri:sparkling-2-line",
                95,
                defaultConfig,
                List.of("sse")
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("AI 能力未启用");
        }
        return CapabilityHealth.enabled("AI 能力已启用", providerConfigParser.metrics(config));
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
}
